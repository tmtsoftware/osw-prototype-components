package org.tmt.osw.simulatedinfrareddetector

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.typesafe.config.ConfigFactory
import csw.logging.api.scaladsl.Logger
import csw.params.core.models.Id
import org.tmt.osw.simulatedinfrareddetector.ControllerMessages.{ControllerMessage, _}

import scala.concurrent.duration._

object ControllerActor {
  lazy private val config = ConfigFactory.load()

  lazy private val exposureTimerPeriod = config.getInt("exposureTimerPeriod").millis

  def apply(logger: Logger): Behavior[ControllerMessage] = Behaviors.setup { _ =>
    uninitialized(ControllerData(logger))
  }

  private def uninitialized(data: ControllerData): Behavior[ControllerMessage] = {
    data.logger.info("In uninitialized state")
    Behaviors.receiveMessage[ControllerMessage] {
      case Initialize(runId, replyTo) =>
        replyTo ! OK(runId)
        idle(data.copy(Idle))
      case x =>
        x.replyTo ! UnsupportedCommand(x.runId, "uninitialized", x)
        Behaviors.same
    }

  }

  private def idle(data: ControllerData): Behavior[ControllerMessage] = {
    data.logger.info("In idle state")
    Behaviors.receiveMessage[ControllerMessage] {
      case ConfigureExposure(runId, replyTo, params) =>
        replyTo ! OK(runId)
        idle(data.copy(newParams = params))
      case StartExposure(runId, replyTo, filename) =>
        replyTo ! ExposureStarted(runId)
        startExposure(runId, data.copy(newExposureFilename = filename), replyTo)
      case ExposureInProgress(_, _) if data.state == Aborting =>  // this can occur on abort
        // ignore
        idle(data.copy(Idle))
      case Shutdown(runId, replyTo) =>
        replyTo ! OK(runId)
        uninitialized(data.copy(Uninitialized))
      case x =>
        x.replyTo ! UnsupportedCommand(x.runId, "idle", x)
        Behaviors.same

    }
  }

  private def exposing(data: ControllerData): Behavior[ControllerMessage] = {
    Behaviors.receiveMessage {
      case AbortExposure(runId, replyTo) =>
        data.logger.info("Exposure Aborted")
        replyTo ! OK(runId)
        Behaviors.withTimers[ControllerMessage] { timers =>
          timers.startSingleTimer(ExposureComplete(runId, replyTo), 0.seconds)  // TODO should be runId from startExposure?
          aborting(data.copy(Aborting))
        }
      case ExposureComplete(runId, replyTo) =>
        data.logger.info("Exposure Complete")
        replyTo ! ExposureFinished(runId, data.exposureFilename)
        idle(data.copy(Idle))
      case ExposureInProgress(runId, replyTo) =>
        val elapsedTime = System.currentTimeMillis() - data.exposureStartTime
        data.logger.debug(s"Exposure In Progress: elapsed time = $elapsedTime ms.  total time = ${calculateExposureDurationMillis(data.exposureParameters)}")
        val (nextState, time) = if (elapsedTime > calculateExposureDurationMillis(data.exposureParameters))
          (ExposureComplete(runId, replyTo), 0.seconds)
        else
          (ExposureInProgress(runId, replyTo), exposureTimerPeriod)

        Behaviors.withTimers[ControllerMessage] { timers =>
          timers.startSingleTimer(nextState, time)
          exposing(data.copy(Exposing))
        }
      case x =>
        x.replyTo ! UnsupportedCommand(x.runId, "exposing", x)
        Behaviors.same

    }
  }

  private def aborting(data: ControllerData): Behavior[ControllerMessage] = {
    Behaviors.receiveMessage {
      case ExposureComplete(runId, replyTo) =>
        data.logger.info("Exposure Complete")
        replyTo ! ExposureFinished(runId, data.exposureFilename)
        idle(data.copy(Aborting))
      case ExposureInProgress(runId, replyTo) =>
        Behaviors.withTimers[ControllerMessage] { timers =>
          timers.startSingleTimer(ExposureComplete(runId, replyTo), 0.seconds)
          aborting(data.copy(Aborting))
        }
      case x =>
        x.replyTo ! UnsupportedCommand(x.runId, "aborting", x)
        Behaviors.same
    }
  }



  private def calculateExposureDurationMillis(params: ExposureParameters): Long = {
    params.integrationTimeMillis * params.coadds
  }


  private def startExposure(runId: Id, data: ControllerData, replyTo: ActorRef[ControllerResponse]) = {
    data.logger.info(s"Starting exposure.  Itime = ${data.exposureParameters.integrationTimeMillis} ms, Coadds = ${data.exposureParameters.coadds}")
    Behaviors.withTimers[ControllerMessage] { timers =>
      timers.startSingleTimer(ExposureInProgress(runId, replyTo), exposureTimerPeriod)
      exposing(data.copy(Exposing, newExposureStartTime = System.currentTimeMillis()))
    }
  }

}
