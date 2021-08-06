package org.tmt.osw.simpleassembly

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.ComponentType.HCD
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models._
import csw.params.commands.CommandResponse._
import csw.params.commands._
import csw.params.core.generics.KeyType
import csw.params.core.models.Id
import csw.prefix.models.Prefix
import csw.time.core.models.UTCTime

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.{DurationLong, FiniteDuration, MILLISECONDS}

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to Template,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/commons/framework.html
 */
class SimpleAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._

  implicit val ec: ExecutionContextExecutor      = ctx.executionContext
  implicit val actorSystem: ActorSystem[Nothing] = ctx.system
  private val log                                = loggerFactory.getLogger

  sealed trait SleepCommand

  case class Sleep(runId: Id, timeInMillis: Long) extends SleepCommand

  private val hcdConnection                     = AkkaConnection(ComponentId(Prefix("CSW.simulated.SimpleHcd"), HCD))
  private var simpleHcd: Option[CommandService] = None

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
  private val sleepKey     = KeyType.LongKey.make("sleepTimeInMs")
  private val errorKey     = KeyType.StringKey.make("error")

  override def initialize(): Unit = {
    log.info("Initializing Simple Assembly...")
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {

    trackingEvent match {
      case LocationUpdated(location) =>
        simpleHcd = Some(CommandServiceFactory.make(location.asInstanceOf[AkkaLocation])(ctx.system))
      case LocationRemoved(_) =>
        simpleHcd = None
    }
  }

  private def sleepHCD(runId: Id, sleepTime: Long): Unit = {
    simpleHcd match {
      case Some(cs) =>
        val s = Setup(componentInfo.prefix, CommandName("sleep"), None).add(sleepTimeKey.set(sleepTime))
        cs.submit(s).foreach {
          case started: Started =>
            cs.queryFinal(started.runId)((sleepTime * 2).millis)
              .foreach(sr => commandResponseManager.updateCommand(sr.withRunId(runId)))
          case other =>
            commandResponseManager.updateCommand(other.withRunId(runId))
        }
      case None =>
        commandResponseManager.updateCommand(
          Error(runId, s"A needed HCD is not available: ${hcdConnection.componentId} for ${componentInfo.prefix}")
        )
    }
  }

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
      case CommandName("hcdSleep") =>
        sleepHCD(runId, command(sleepTimeKey).head)
        Started(runId)
      // todo sleep and error
      case _ =>
        Completed(runId)
    }
  }

  def onObserve(runId: Id, command: Observe): SubmitResponse = {
    command.commandName match {
      case CommandName("noop") =>
        Completed(runId)
      case _ =>
        Completed(runId)

    }
  }

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = controlCommand match {
    case s: Setup =>
      s.commandName match {
        case CommandName("noop")                                 => Accepted(runId)
        case CommandName("sleep") if s.contains(sleepTimeKey)    => Accepted(runId)
        case CommandName("hcdSleep") if s.contains(sleepTimeKey) => Accepted(runId)
        case _ if s.contains(sleepKey)                           => Accepted(runId)
        case x                                                   => Invalid(runId, CommandIssue.UnsupportedCommandIssue(s"Setup command <$x> is not supported."))
      }
    case o: Observe =>
      o.commandName match {
        case CommandName("noop") => Accepted(runId)
        case x                   => Invalid(runId, CommandIssue.UnsupportedCommandIssue(s"Observe command <$x> is not supported."))

      }
  }

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = controlCommand match {
    case s: Setup   => onSetup(runId, s)
    case o: Observe => onObserve(runId, o)
  }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}
