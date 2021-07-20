package org.tmt.osw.simulatedinfrareddetectorhcd

import akka.actor.typed.ActorRef
import csw.logging.api.scaladsl.Logger
import csw.params.core.states.CurrentState
import csw.prefix.models.Prefix

sealed trait ControllerState
case object Uninitialized extends ControllerState
case object Idle          extends ControllerState
case object Exposing      extends ControllerState
case object Aborting      extends ControllerState

case class ExposureParameters(resets: Int, reads: Int, ramps: Int)
case class FitsData(data: Array[Array[Int]]) {
  val dimensions: (Int, Int) = (data.length, data(0).length)
}
case class ControllerStatus(readsDone: Int, rampsDone: Int) {
  def incrementReadsDone(): ControllerStatus = ControllerStatus(readsDone+1, rampsDone)
  def incrementRampsDone(): ControllerStatus = ControllerStatus(readsDone, rampsDone+1)
}

object ControllerStatus {
  def apply(): ControllerStatus = ControllerStatus(0, 0)
}

case class ControllerData(
                           logger: Logger,
                           currentStateForwarder: ActorRef[CurrentState],
                           prefix: Prefix,
                           state: ControllerState,
                           status: ControllerStatus,
                           exposureParameters: ExposureParameters,
                           exposureStartTime: Long,
                           exposureFilename: String
) {
  def copy(
      newState: ControllerState = state,
      newStatus: ControllerStatus = status,
      newParams: ExposureParameters = exposureParameters,
      newExposureStartTime: Long = exposureStartTime,
      newExposureFilename: String = exposureFilename
  ): ControllerData = {
    ControllerData(logger, currentStateForwarder, prefix, newState, newStatus, newParams, newExposureStartTime, newExposureFilename)
  }
}

object ControllerData {
  def apply(logger: Logger, currentStateForwarder: ActorRef[CurrentState], prefix: Prefix): ControllerData =
    ControllerData(
      logger,
      currentStateForwarder,
      prefix,
      Uninitialized,
      ControllerStatus(),
      ExposureParameters(1,2,1),
      0L,
      "none")
}

