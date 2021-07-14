package org.tmt.osw.simulatedinfrareddetectorhcd

import akka.actor.typed.Scheduler
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandResponse._
import csw.params.commands.{CommandIssue, ControlCommand, Observe, Setup}
import csw.params.core.models.Id
import csw.time.core.models.UTCTime
import org.tmt.osw.simulatedinfrareddetectorhcd.ControllerMessages._
import org.tmt.osw.simulatedinfrareddetectorhcd.HcdConstants.{commandName, keys}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to SimulatedInfraredDetectorhcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/commons/framework.html
 */
class SimulatedInfraredDetectorhcdHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext)
    extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  implicit val scheduler: Scheduler         = ctx.system.scheduler
  private val log                           = loggerFactory.getLogger
  private val fitsWriteTimeout              = 10.seconds

  private val fitsActor  = ctx.spawn(FitsActor(log), "fits")
  private val controller = ctx.spawn(ControllerActor(log, currentStatePublisher, componentInfo.prefix), "controller")

  private val controllerResponseActor = ctx.spawn[ControllerResponse](
    Behaviors.receiveMessagePartial[ControllerResponse] {
      case OK(runId) =>
        commandResponseManager.updateCommand(Completed(runId))
        Behaviors.same
      case ExposureFinished(runId, data, filename) =>
        val result = fitsActor.ask[FitsResponse](WriteData(filename, data, _))(fitsWriteTimeout, scheduler)
        result.onComplete {
          case Success(_: DataWritten) => commandResponseManager.updateCommand(Completed(runId))
          case Failure(exception)      => commandResponseManager.updateCommand(Error(runId, exception.getMessage))
        }
        Behaviors.same
      case UnsupportedCommand(runId, _, message) =>
        commandResponseManager.updateCommand(Invalid(runId, CommandIssue.UnsupportedCommandIssue(message.toString)))
        Behaviors.same
    },
    "responseActor"
  )
  override def initialize(): Unit = {
    log.info("Initializing simulated.Infrared.DetectorHcd...")
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {}

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = Accepted(runId)

  def onSetup(runId: Id, command: Setup): SubmitResponse = {
    command.commandName match {
      case commandName.initialize =>
        controller ! Initialize(runId, controllerResponseActor)
        Started(runId)
      case commandName.configureExposure =>
        val resets              = command(keys.resets).head
        val reads             = command(keys.reads).head
        val ramps             = command(keys.ramps).head
        val exposureParameters = ExposureParameters(resets, reads, ramps)
        controller ! ConfigureExposure(runId, controllerResponseActor, exposureParameters)
        Started(runId)
      case commandName.abortExposure =>
        controller ! AbortExposure(runId, controllerResponseActor)
        Started(runId)
      case commandName.shutdown =>
        controller ! Shutdown(runId, controllerResponseActor)
        Started(runId)
      case x => Invalid(runId, CommandIssue.UnsupportedCommandIssue(s"${x.name} is not a supported Setup command"))
    }
  }

  def onObserve(runId: Id, command: Observe): SubmitResponse = {
    command.commandName match {
      case commandName.startExposure =>
        val filename = command(keys.filename).head
        controller ! StartExposure(runId, controllerResponseActor, filename)
        Started(runId)
      case x => Invalid(runId, CommandIssue.UnsupportedCommandIssue(s"${x.name} is not a supported Setup command"))
    }
  }

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    controlCommand match {
      case s: Setup   => onSetup(runId, s)
      case o: Observe => onObserve(runId, o)
    }
  }
  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}
