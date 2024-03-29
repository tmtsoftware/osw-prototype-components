package org.tmt.osw.simple

import akka.util.Timeout
import csw.command.client.CommandServiceFactory
import csw.command.client.extensions.AkkaLocationExt.RichAkkaLocation
import csw.command.client.messages.SupervisorContainerCommonMessages.Restart
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{ComponentId, ComponentType}
import csw.params.commands.CommandResponse.Completed
import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.KeyType
import csw.prefix.models.{Prefix, Subsystem}
import csw.testkit.scaladsl.CSWService.{AlarmServer, EventServer}
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Await
import scala.concurrent.duration._

class SimpleAssemblyTest extends ScalaTestFrameworkTestKit(AlarmServer, EventServer) with AnyWordSpecLike with BeforeAndAfterEach {

  import frameworkTestKit._

  private implicit val timeout: Timeout                                = 12.seconds


  private val testPrefix = Prefix(Subsystem.CSW, "test")
  private val hcdPrefix = Prefix(Subsystem.CSW, "SimpleHcd")
  private val hcdConnection = AkkaConnection(ComponentId(hcdPrefix, ComponentType.HCD))
  private val assemblyPrefix = Prefix(Subsystem.CSW, "SimpleAssembly")
  private val assemblyConnection = AkkaConnection(ComponentId(assemblyPrefix, ComponentType.Assembly))

  override def beforeAll(): Unit = {
    super.beforeAll()
    // uncomment if you want one Assembly run for all tests
    spawnStandalone(com.typesafe.config.ConfigFactory.load("SimpleAssemblyStandalone.conf"))
  }

  override def afterEach(): Unit = {
    val akkaLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
    val supervisor = akkaLocation.componentRef
    supervisor ! Restart
    Thread.sleep(1000)
  }

  "assembly" must {

    "be locatable using Location Service" in {
      val akkaLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get

      akkaLocation.connection shouldBe assemblyConnection
    }

    "return Completed on hcdSleep command" in {
      spawnStandalone(com.typesafe.config.ConfigFactory.load("SimpleHcdStandalone.conf"))
      // wait for hcd to be created
      Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get

      val akkaLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
      val assembly = CommandServiceFactory.make(akkaLocation)

      val sleepCommand = Setup(testPrefix, CommandName("hcdSleep"), None).add(KeyType.LongKey.make("timeInMs").set(5000))

      Await.result(assembly.submitAndWait(sleepCommand), 6.seconds) shouldBe a[Completed]
    }
  }
}