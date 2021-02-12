package org.tmt.osw.simpleassembly

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandResponse._
import csw.params.commands._
import csw.params.core.generics.KeyType
import csw.params.core.models.Id
import csw.time.core.models.UTCTime

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}
import scala.concurrent.ExecutionContextExecutor

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to Template,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/commons/framework.html
 */
class SimpleAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx,cswCtx) {

  import cswCtx._
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger

  sealed trait SleepCommand
  case class Sleep(runId: Id, timeInMillis: Long) extends SleepCommand

  private val workerActor =
    ctx.spawn(
      Behaviors.receiveMessage[SleepCommand](msg => {
        msg match {
          case sleep: Sleep =>
            log.trace(s"WorkerActor received sleep command with time of ${sleep.timeInMillis} ms")
            // simulate long running command
            val when: UTCTime = UTCTime.after(FiniteDuration(sleep.timeInMillis, MILLISECONDS))
            timeServiceScheduler.scheduleOnce(when) {
              commandResponseManager.updateCommand(CommandResponse.Completed(sleep.runId))
            }
          case _ => log.error("Unsupported message type")
        }
        Behaviors.same
      }),
      "WorkerActor"
    )

  private val sleepTimeKey = KeyType.LongKey.make("timeInMs")
  private val sleepKey = KeyType.LongKey.make("sleepTimeInMs")
  private val errorKey = KeyType.StringKey.make("error")

  override def initialize(): Unit = {
    log.info("Initializing Simple Assembly...")
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {}

  def onSetup(runId: Id, command: Setup): SubmitResponse = {
    command.commandName match {
      case CommandName("noop") =>
        Completed(runId)
      case CommandName("sleep") =>
        workerActor ! Sleep(runId, command(sleepTimeKey).head)
        Started(runId)
      case _ if command.contains(sleepKey) => // do something based on parameters
          workerActor ! Sleep(runId, command(sleepKey).head)
          Started(runId)
      case _ if command.contains(errorKey) => // do something based on parameters
        Error(runId, command(errorKey).head)

        // todo sleep and error
      case _ =>
        Completed(runId)
    }
  }

  def onObserve(runId: Id, command: Observe): SubmitResponse = {
    command.commandName match {
      case CommandName("noop") =>
        Completed(runId)
    }
  }

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = controlCommand match {
    case s: Setup => s.commandName match {
      case CommandName("noop")                              => Accepted(runId)
      case CommandName("sleep") if s.contains(sleepTimeKey) => Accepted(runId)
      case _ if s.contains(sleepKey)                        => Accepted(runId)
      case x                                                => Invalid(runId,
        CommandIssue.UnsupportedCommandIssue(s"Setup command <$x> is not supported."))
    }
    case o: Observe => o.commandName match {
      case CommandName("noop") => Accepted(runId)
      case x                   => Invalid(runId,
        CommandIssue.UnsupportedCommandIssue(s"Observe command <$x> is not supported."))

    }
  }

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = controlCommand match {
    case s: Setup => onSetup(runId, s)
    case o: Observe => onObserve(runId, o)
  }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}
