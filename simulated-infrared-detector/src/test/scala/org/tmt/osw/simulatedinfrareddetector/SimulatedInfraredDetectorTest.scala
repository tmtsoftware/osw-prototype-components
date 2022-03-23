package org.tmt.osw.simulatedinfrareddetector

import java.io.File

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import csw.command.client.CommandServiceFactory
import csw.command.client.extensions.AkkaLocationExt.RichAkkaLocation
import csw.command.client.messages.SupervisorContainerCommonMessages.Restart
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{ComponentId, ComponentType}
import csw.params.commands.CommandIssue.UnsupportedCommandIssue
import csw.params.commands.CommandResponse.{Completed, Invalid}
import csw.params.commands.{Observe, Setup}
import csw.prefix.models.{Prefix, Subsystem}
import csw.testkit.scaladsl.CSWService.{AlarmServer, EventServer}
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class SimulatedInfraredDetectorTest
    extends ScalaTestFrameworkTestKit(AlarmServer, EventServer)
    with AnyWordSpecLike
    with BeforeAndAfterEach {

  import AssemblyConstants._
  import frameworkTestKit._

  private implicit val actorSystem: ActorSystem[SpawnProtocol.Command] = frameworkTestKit.actorSystem
  private implicit val ec: ExecutionContext                            = actorSystem.executionContext
  private implicit val timeout: Timeout                                = 12.seconds

  private val testPrefix         = Prefix(Subsystem.CSW, "test")
  private val assemblyPrefix     = Prefix(Subsystem.CSW, "simulated.Infrared.Detector")
  private val assemblyConnection = AkkaConnection(ComponentId(assemblyPrefix, ComponentType.Assembly))

  override def beforeAll(): Unit = {
    super.beforeAll()
    // one Assembly run for all tests
    spawnStandalone(com.typesafe.config.ConfigFactory.load("SimulatedInfraredDetectorStandalone.conf"))
  }

  override def afterEach(): Unit = {
    val akkaLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
    val supervisor   = akkaLocation.componentRef
    supervisor ! Restart
    Thread.sleep(1000)
  }

  "assembly" must {

    "be locatable using Location Service" in {
      val akkaLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
      akkaLocation.connection shouldBe assemblyConnection
    }

    "return Completed on initialize command" in {
      val akkaLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
      val assembly     = CommandServiceFactory.make(akkaLocation)

      val initializeCommand = Setup(testPrefix, commandName.initialize, None)

      Await.result(assembly.submitAndWait(initializeCommand), 2.seconds) shouldBe a[Completed]
    }

    "ConfigureExposure command when uninitialized should return UnsupportedCommand" in {
      val akkaLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
      val assembly     = CommandServiceFactory.make(akkaLocation)

      val configureExposureCommand = Setup(testPrefix, commandName.configureExposure, None)
        .add(keys.integrationTime.set(1000))
        .add(keys.coaddition.set(2))

      val result = Await.result(assembly.submitAndWait(configureExposureCommand), 2.seconds)
      result shouldBe a[Invalid]
      result.asInstanceOf[Invalid].issue shouldBe a[UnsupportedCommandIssue]
    }
  }

  "return ExposureFinished on initialize, configureExposure, and startExposure commands" in {
    val filename     = "assemblyTest.fits"
    val akkaLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
    val assembly     = CommandServiceFactory.make(akkaLocation)

    val initializeCommand = Setup(testPrefix, commandName.initialize, None)

    Await.result(assembly.submitAndWait(initializeCommand), 2.seconds) shouldBe a[Completed]

    val configureExposureCommand = Setup(testPrefix, commandName.configureExposure, None)
      .add(keys.integrationTime.set(1000))
      .add(keys.coaddition.set(2))

    Await.result(assembly.submitAndWait(configureExposureCommand), 2.seconds) shouldBe a[Completed]

    val startExposureCommand = Observe(testPrefix, commandName.startExposure, None)
      .add(keys.filename.set(filename))

    Await.result(assembly.submitAndWait(startExposureCommand)(10.seconds), 10.seconds) shouldBe a[Completed]

    val file = new File(filename)
    file.exists() shouldBe true
    file.delete()

  }
}
