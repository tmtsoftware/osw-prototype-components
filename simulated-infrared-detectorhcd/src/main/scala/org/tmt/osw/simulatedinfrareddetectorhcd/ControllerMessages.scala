package org.tmt.osw.simulatedinfrareddetectorhcd

import akka.actor.typed.ActorRef
import csw.params.core.models.Id

case object ControllerMessages {
  sealed trait ControllerMessage {
    def runId: Id
    def replyTo: ActorRef[ControllerResponse]
  }
  case class Initialize(runId: Id, replyTo: ActorRef[ControllerResponse]) extends ControllerMessage
  case class ConfigureExposure(runId: Id, replyTo: ActorRef[ControllerResponse], params: ExposureParameters)
      extends ControllerMessage
  case class StartExposure(runId: Id, replyTo: ActorRef[ControllerResponse], filename: String) extends ControllerMessage
  case class ExposureInProgress(runId: Id, replyTo: ActorRef[ControllerResponse])              extends ControllerMessage
  case class AbortExposure(runId: Id, replyTo: ActorRef[ControllerResponse])                   extends ControllerMessage
  case class Shutdown(runId: Id, replyTo: ActorRef[ControllerResponse])                        extends ControllerMessage
  case class ExposureComplete(runId: Id, replyTo: ActorRef[ControllerResponse])                extends ControllerMessage

  sealed trait ControllerResponse
  case class OK(runId: Id)                                                                   extends ControllerResponse
  case class ExposureStarted(runId: Id)                                                      extends ControllerResponse
  case class ExposureFinished(runId: Id, data: FitsData, filename: String)                   extends ControllerResponse
  case class UnsupportedCommand(runId: Id, currentState: String, message: ControllerMessage) extends ControllerResponse

  sealed trait FitsMessage
  case class WriteData(filename: String, data: FitsData, replyTo: ActorRef[FitsResponse]) extends FitsMessage

  sealed trait FitsResponse
  case class DataWritten(filename: String) extends FitsResponse
}
