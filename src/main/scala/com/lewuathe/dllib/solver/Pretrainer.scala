package com.lewuathe.dllib.solver

import com.lewuathe.dllib.form.Form
import com.lewuathe.dllib.network.Network
import org.apache.spark.SparkContext

import scala.util.control.Breaks._

import breeze.linalg.Vector
import com.lewuathe.dllib.layer.{Layer, PretrainLayer}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, DataFrame}
import org.apache.spark.sql.functions.{col, lit}

import com.lewuathe.dllib.{ActivationStack, Model, Instance, util}

/**
  * Pretrainer provides a way to train pre train networks before running
  * fully backpropagation. Pretrainer assumes pretrain layers are put
  * continuously at the head of network.
  */
trait Pretrainer extends Solver[org.apache.spark.mllib.linalg.Vector,
  UnsupervisedPretrainingSolver, UnsupervisedPretrainingSolverModel] {

  private def iteration(pretrainLayer: PretrainLayer, iter: Int,
                        instances: RDD[Instance], model: Model, form: Form,
                        pretrainTmpModel: Model, pretrainTmpForm: Form,
                        sc: SparkContext): (Model, Model) = {
    val bcModel = sc.broadcast(model)
    val bcPretrainTmpModel = sc.broadcast(pretrainTmpModel)
    val (modelDelta: Model, lossSum: Double, miniBatchSize: Int,
        pretrainTmpModelDelta: Model)
    = instances.sample(false, miniBatchFraction, 42 + iter)
      .treeAggregate(Model.zero(form), 0.0, 0, Model.zero(pretrainTmpForm))(
        seqOp = (c: (Model, Double, Int, Model), instance: Instance) => {
          // Sample feature
          val activations = new ActivationStack
          activations.push((instance.features, instance.features))

          // Feed forward to pretrained target layer
          breakable(
            for (l: Layer <- form.layers) {
              // Target pretrain layer does not need to forward
              if (l.id == pretrainLayer.id) break
              val (u, z) = l.forward(activations, bcModel.value)
              activations.push((u, z))
            }
          )

          // g1 = (dWeight for hidden layer, dBias for hidden layer)
          // g2 = (dWeight for visible layer, dBias for visible layer)
          // g2 cannot be used unless the network is tied weight
          val (g1, g2, loss) = pretrainLayer.pretrain(activations,
            bcModel.value, bcPretrainTmpModel.value)
          (c._1 + g1._1 + g1._2, c._2 + loss, c._3 + 1, c._4 + g2._2)
        },
        combOp = (c1, c2) => {
          (c1._1 + c2._1, c1._2 + c2._2, c1._3 + c2._3, c1._4 + c2._4)
        }
      )
    logInfo(s"Iteration ${iter} -> loss: ${lossSum / miniBatchSize}, " +
      s"count: ${miniBatchSize}, learning rate: ${learningRate}")
    (model + (modelDelta / miniBatchSize) * learningRate,
      pretrainTmpModel + (pretrainTmpModelDelta / miniBatchSize) * learningRate)
  }

  def pretrainInternal(dataset: DataFrame, model: Model): Model = {
    val w = lit(1.0)
    val instances: RDD[Instance]
      = dataset.select(col($(labelCol)), w, col($(featuresCol))).map {
      case Row(label: Double, weight: Double,
      features: org.apache.spark.mllib.linalg.Vector) => {
        val l = util.encodeLabel(label, form.layers.last.outputSize)
        Instance(l, weight, Vector[Double](features.toArray))
      }
    }

    var localModel = model
    val bcForm = dataset.sqlContext.sparkContext.broadcast(form)

    // TODO: Refactoring to be readable
    for (layer <- form.layers if layer.isInstanceOf[PretrainLayer]) {
      // Pretraining can be applied only for PretrainLayer
      layer match {
        case pretrainLayer: PretrainLayer => {
          var (pretrainTmpModel, pretrainTmpForm)
            = createPretrainTmpNetwork(pretrainLayer)
          val bcPretrainTmpForm
            = dataset.sqlContext.sparkContext.broadcast(pretrainTmpForm)
          for (iter <- 0 until numIterations) {
            val ret = iteration(pretrainLayer, iter, instances, localModel,
              bcForm.value, pretrainTmpModel , bcPretrainTmpForm.value,
              instances.sparkContext)
            localModel = ret._1
            pretrainTmpModel = ret._2
            learningRate *= learningRateDecay
          }
        } // case
      } // match
    } // for
    localModel
  }

  // Tmp model represents a model that is only used while pretraining
  private def createPretrainTmpNetwork(pretrainLayer: PretrainLayer):
      (Model, Form) = {
    val tmpForm = new Form(Array(pretrainLayer.createTmpLayer()))
    (Model(tmpForm), tmpForm)
  }
}
