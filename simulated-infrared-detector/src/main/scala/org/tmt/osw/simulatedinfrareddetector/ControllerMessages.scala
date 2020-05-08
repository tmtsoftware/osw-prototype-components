package org.tmt.osw.simulatedinfrareddetector

import akka.actor.typed.ActorRef

case object ControllerMessages {
  sealed trait ControllerMessage
  case class Initialize(replyTo: ActorRef[ControllerResponse]) extends ControllerMessage
  case class ConfigureExposure(params: ExposureParameters, replyTo: ActorRef[ControllerResponse]) extends ControllerMessage
  case class StartExposure(filename: String, replyTo: ActorRef[ControllerResponse]) extends ControllerMessage
  case class ExposureInProgress(replyTo: ActorRef[ControllerResponse]) extends ControllerMessage
  case class AbortExposure(replyTo: ActorRef[ControllerResponse]) extends ControllerMessage
  case class Shutdown(replyTo: ActorRef[ControllerResponse]) extends ControllerMessage
  case class ExposureComplete(replyTo: ActorRef[ControllerResponse]) extends ControllerMessage

  sealed trait ControllerResponse
  case object OK extends ControllerResponse
  case object ExposureStarted extends ControllerResponse
  case object ExposureFinished extends ControllerResponse
}


