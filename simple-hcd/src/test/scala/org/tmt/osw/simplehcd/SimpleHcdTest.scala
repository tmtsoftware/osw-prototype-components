package org.tmt.osw.simplehcd

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

class SimpleHcdTest extends ScalaTestFrameworkTestKit(AlarmServer, EventServer) with AnyWordSpecLike with BeforeAndAfterEach {

  import frameworkTestKit._

  private implicit val timeout: Timeout                                = 12.seconds

  private val testPrefix = Prefix(Subsystem.CSW, "test")
  private val hcdPrefix = Prefix(Subsystem.CSW, "SimpleHcd")
  private val hcdConnection = AkkaConnection(ComponentId(hcdPrefix, ComponentType.HCD))

  override def beforeAll(): Unit = {
    super.beforeAll()
    // uncomment if you want one Assembly run for all tests
    spawnStandalone(com.typesafe.config.ConfigFactory.load("SimpleHcdStandalone.conf"))
  }

  override def afterEach(): Unit = {
    val akkaLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get
    val supervisor = akkaLocation.componentRef
    supervisor ! Restart
    Thread.sleep(1000)
  }

  "hcd" must {

    "be locatable using Location Service" in {

      val akkaLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get

      akkaLocation.connection shouldBe hcdConnection
    }

    "return Completed on noop command" in {
      val akkaLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get
      val assembly = CommandServiceFactory.make(akkaLocation)

      val initializeCommand = Setup(testPrefix, CommandName("noop"), None)

      Await.result(assembly.submitAndWait(initializeCommand), 2.seconds) shouldBe a[Completed]
    }
    "return Completed on sleep command" in {
      val akkaLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get
      val assembly = CommandServiceFactory.make(akkaLocation)

      val sleepCommand = Setup(testPrefix, CommandName("sleep"), None).add(KeyType.LongKey.make("timeInMs").set(5000))

      Await.result(assembly.submitAndWait(sleepCommand), 6.seconds) shouldBe a[Completed]
    }
  }
}