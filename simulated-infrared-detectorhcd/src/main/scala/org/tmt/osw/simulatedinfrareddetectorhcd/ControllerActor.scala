package org.tmt.osw.simulatedinfrareddetectorhcd

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.typesafe.config.ConfigFactory
import csw.logging.api.scaladsl.Logger
import csw.params.core.models.Id
import csw.params.core.states.CurrentState
import csw.prefix.models.Prefix
import org.tmt.osw.simulatedinfrareddetectorhcd.ControllerMessages._
import org.tmt.osw.simulatedinfrareddetectorhcd.HcdConstants.keys

import scala.concurrent.duration._

/*
  Exposure configured with x resets, n reads, r ramps
  Frame read time is numberOfPixels/32 * pixelClockTimeMs (from config)

 */

object ControllerActor {
  lazy private val config = ConfigFactory.load()

  lazy private val exposureTimerPeriod = config.getInt("exposureTimerPeriod").millis

  private lazy val detectorDimensions: (Int, Int) = {
    (config.getInt("detector.xs"), config.getInt("detector.ys"))
  }

  private lazy val frameReadTimeMs =
    detectorDimensions._1 * detectorDimensions._2 * config.getDouble("pixelClockTimeMs") / 32.0

  def apply(logger: Logger, currentStatePublisher: ActorRef[CurrentState], prefix: Prefix): Behavior[ControllerMessage] =
    Behaviors.setup { _ =>
      uninitialized(ControllerData(logger, currentStatePublisher, prefix))
    }

  private def uninitialized(data: ControllerData): Behavior[ControllerMessage] = {
    data.logger.info("In uninitialized state")
    Behaviors.receiveMessage[ControllerMessage] {
      case Initialize(runId, replyTo) =>
        replyTo ! OK(runId)
        idle(data.copy(state = Idle))
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
        idle(data.copy(exposureParameters = params))
      case StartExposure(runId, maybeObsId, exposureId, filename, replyTo) =>
        replyTo ! ExposureStarted(runId)
        startExposure(
          runId,
          data.copy(status = ControllerStatus(), exposureInfo = ExposureInfo(maybeObsId, exposureId, filename)),
          replyTo
        )
      case ExposureInProgress(_, _) if data.state == Aborting => // this can occur on abort
        // ignore
        idle(data.copy(state = Idle))
      case Shutdown(runId, replyTo) =>
        replyTo ! OK(runId)
        uninitialized(data.copy(state = Uninitialized))
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
          timers.startSingleTimer(ExposureComplete(runId, replyTo), 0.seconds) // TODO should be runId from startExposure?
          aborting(data.copy(state = Aborting))
        }
      case ExposureComplete(runId, replyTo) =>
        data.logger.info("Exposure Complete")
        replyTo ! ExposureFinished(
          runId,
          FitsData(generateFakeImageData(detectorDimensions._1, detectorDimensions._2)),
          data.exposureInfo
        )
        idle(data.copy(state = Idle))
      case ExposureInProgress(runId, replyTo) =>
        val elapsedTime = System.currentTimeMillis() - data.exposureStartTime
        data.logger.debug(
          s"Exposure In Progress: elapsed time = $elapsedTime ms.  total time = ${calculateExposureDurationMillis(data.exposureParameters)}"
        )
        val numReadTimes = math.floor(elapsedTime / frameReadTimeMs).toInt
        val readsPerRamp = data.exposureParameters.resets + data.exposureParameters.reads

        data.logger.debug(
          s"Exposure In Progress: numReadTimes = $numReadTimes, readsPerRamp = $readsPerRamp."
        )

        val rampsDone = math.floor(numReadTimes / readsPerRamp).toInt
        val readsDone = numReadTimes - rampsDone * readsPerRamp - data.exposureParameters.resets match {
          case x if x > 0 => x
          case _          => 0
        }
        data.logger.debug(
          s"Exposure In Progress: readsDone = $readsDone of ${data.exposureParameters.reads}.  rampsDone = $rampsDone of ${data.exposureParameters.ramps}."
        )

        val status = ControllerStatus(readsDone, rampsDone)
        data.currentStateForwarder ! createCurrentState(data.prefix, status)
        val (nextState, time) =
          if (rampsDone < data.exposureParameters.ramps)
            (ExposureInProgress(runId, replyTo), exposureTimerPeriod)
          else
            (ExposureComplete(runId, replyTo), 0.seconds)

        Behaviors.withTimers[ControllerMessage] { timers =>
          timers.startSingleTimer(nextState, time)
          exposing(data.copy(state = Exposing, status = status))
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
        replyTo ! ExposureAborted(
          runId,
          FitsData(generateFakeImageData(detectorDimensions._1, detectorDimensions._2)),
          data.exposureInfo
        )
        idle(data.copy(state = Aborting))
      case ExposureInProgress(runId, replyTo) =>
        Behaviors.withTimers[ControllerMessage] { timers =>
          timers.startSingleTimer(ExposureComplete(runId, replyTo), 0.seconds)
          aborting(data.copy(state = Aborting))
        }
      case x =>
        x.replyTo ! UnsupportedCommand(x.runId, "aborting", x)
        Behaviors.same
    }
  }

  private def calculateExposureDurationMillis(params: ExposureParameters): Long = {
    (params.resets + params.reads) * params.ramps
  }

  private def startExposure(runId: Id, data: ControllerData, replyTo: ActorRef[ControllerResponse]) = {
    data.logger.info(
      s"Starting exposure.  resets = ${data.exposureParameters.resets} ms, reads = ${data.exposureParameters.reads}, ramps = ${data.exposureParameters.ramps}"
    )
    Behaviors.withTimers[ControllerMessage] { timers =>
      timers.startSingleTimer(ExposureInProgress(runId, replyTo), exposureTimerPeriod)
      exposing(data.copy(state = Exposing, exposureStartTime = System.currentTimeMillis()))
    }
  }

  private def generateFakeImageData(xs: Int, ys: Int) = {
    Array.tabulate(xs, ys) {
      case (x, y) => x * xs + y
    }
  }

  private def createCurrentState(prefix: Prefix, status: ControllerStatus) =
    CurrentState(prefix, HcdConstants.currentStateName)
      .add(keys.readsDone.set(status.readsDone))
      .add(keys.rampsDone.set(status.rampsDone))
}
