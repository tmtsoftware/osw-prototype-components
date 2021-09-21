package org.tmt.osw.simulatedinfrareddetector

import csw.logging.api.scaladsl.Logger

sealed trait ControllerState
case object Uninitialized extends ControllerState
case object Idle          extends ControllerState
case object Exposing      extends ControllerState
case object Aborting      extends ControllerState

case class ExposureParameters(integrationTimeMillis: Int, coadds: Int)
case class FitsData(data: Array[Array[Int]]) {
  val dimensions: (Int, Int) = (data.length, data(0).length)
}

case class ControllerData(
    logger: Logger,
    state: ControllerState,
    exposureParameters: ExposureParameters,
    exposureStartTime: Long,
    exposureFilename: String
)

object ControllerData {
  def apply(logger: Logger): ControllerData = ControllerData(logger, Uninitialized, ExposureParameters(10000, 1), 0L, "none")
}
