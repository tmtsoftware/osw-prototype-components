package org.tmt.osw.simulatedinfrareddetector

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.typesafe.config.ConfigFactory
import csw.logging.api.scaladsl.Logger
import org.tmt.osw.simulatedinfrareddetector.ControllerMessages.{ControllerMessage, _}

import scala.concurrent.duration._

object ControllerActor {
  lazy private val config = ConfigFactory.load()

  lazy private val exposureTimerPeriod = config.getInt("exposureTimerPeriod").millis

  def apply(logger: Logger): Behavior[ControllerMessage] = Behaviors.setup { _ =>
    uninitialized(ControllerData(logger))
  }

  private def uninitialized(data: ControllerData): Behavior[ControllerMessage] = {
    println("uninitialized")
    data.logger.info("In uninitialized state")
    Behaviors.receiveMessagePartial[ControllerMessage] {
      case Initialize(replyTo) =>
        replyTo ! OK
        idle(data.copy(Idle))
    }
  }

  private def idle(data: ControllerData): Behavior[ControllerMessage] = {
    println("idle")
    data.logger.info("In idle state")
    Behaviors.receiveMessagePartial[ControllerMessage] {
      case c: ConfigureExposure =>
        c.replyTo ! OK
        idle(data.copy(newParams = c.params))
      case s: StartExposure =>
        s.replyTo ! ExposureStarted
        startExposure(data.copy(newExposureFilename = s.filename), s.replyTo)
      case Shutdown(replyTo) =>
        replyTo ! OK
        uninitialized(data.copy(Uninitialized))
    }
  }

  private def exposing(data: ControllerData): Behavior[ControllerMessage] = {
    println("exposing")
    data.logger.info("In exposing state")
    Behaviors.receiveMessagePartial {
      case AbortExposure(replyTo) =>
        data.logger.info("Exposure Aborted")
        replyTo ! OK
        Behaviors.withTimers[ControllerMessage] { timers =>
          timers.startSingleTimer(ExposureComplete(replyTo), 0.seconds)
          exposing(data.copy(Aborting))
        }
      case ExposureComplete(replyTo) =>
        data.logger.info("Exposure Complete")
        writeData(data.logger, data.exposureFilename)
        replyTo ! ExposureFinished
        idle(data.copy(Idle))
      case ExposureInProgress(replyTo) =>
        val elapsedTime = System.currentTimeMillis() - data.exposureStartTime
        data.logger.debug(s"Exposure In Progress: elapsed time = $elapsedTime ms.  total time = ${calculateExposureDurationMillis(data.exposureParameters)}")
        val (nextState, time) = if (elapsedTime > calculateExposureDurationMillis(data.exposureParameters))
          (ExposureComplete(replyTo), 0.seconds)
        else
          (ExposureInProgress(replyTo), exposureTimerPeriod)
        Behaviors.withTimers[ControllerMessage] { timers =>
          timers.startSingleTimer(nextState, time)
          exposing(data.copy(Exposing))
        }
    }
  }

  private def calculateExposureDurationMillis(params: ExposureParameters): Long = {
    params.integrationTimeMillis * params.coadds
  }

  private lazy val detectorDimensions: (Int, Int) =  {
    (config.getInt("detector.xs"), config.getInt("detector.ys"))
  }

  private def writeData(logger: Logger, filename: String): Unit = {
    logger.info(s"writing data.  Image dimensions = $detectorDimensions")
  }

  private def startExposure(data: ControllerData, replyTo: ActorRef[ControllerResponse]) = {
    Behaviors.withTimers[ControllerMessage] { timers =>
      timers.startSingleTimer(ExposureInProgress(replyTo), exposureTimerPeriod)
      exposing(data.copy(Exposing, newExposureStartTime = System.currentTimeMillis()))
    }
  }

}
