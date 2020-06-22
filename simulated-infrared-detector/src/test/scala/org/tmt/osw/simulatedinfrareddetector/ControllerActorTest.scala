package org.tmt.osw.simulatedinfrareddetector

import java.net.InetAddress

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import com.typesafe.config.ConfigFactory
import csw.logging.client.appenders.{LogAppenderBuilder, StdOutAppender}
import csw.logging.client.scaladsl.{LoggerFactory, LoggingSystemFactory}
import csw.logging.client.internal.JsonExtensions.RichJsObject
import csw.params.core.models.Id
import csw.prefix.models.Prefix
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike
import org.tmt.osw.simulatedinfrareddetector.ControllerMessages._
import play.api.libs.json.{JsObject, Json}

import scala.collection.mutable
import scala.concurrent.duration._

class TestAppender(callback: Any => Unit) extends LogAppenderBuilder {

  def apply(system: ActorSystem[_], stdHeaders: JsObject): StdOutAppender =
    new StdOutAppender(system, stdHeaders, callback)
}


class ControllerActorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike with BeforeAndAfterEach {

  private lazy val actorSystem   = ActorSystem(SpawnProtocol(), "test")
  private lazy val hostName      = InetAddress.getLocalHost.getHostName
  private lazy val loggingSystem = LoggingSystemFactory.start("logging", "2.1", hostName, actorSystem)

  private val loggerFactory = new LoggerFactory(Prefix("ESW.SimulatedInfraredDetector"))
  private val logger        = loggerFactory.getLogger

  protected val logBuffer: mutable.Buffer[JsObject] = mutable.Buffer.empty[JsObject]
  protected val testAppender                        = new TestAppender(x => logBuffer += Json.parse(x.toString).as[JsObject])

  lazy private val config = ConfigFactory.load()

  private val testId = Id()
  private val testId2 = Id()
  private val testId3 = Id()
  private val testId4 = Id()
  private val dumpLogs = false

  override def beforeAll(): Unit = {
    super.beforeAll()
    loggingSystem.setAppenders(List(testAppender))
  }

  override def afterAll(): Unit = {
    if (dumpLogs) {
      Thread.sleep(1000)
      logBuffer.foreach(println)
    }
    actorSystem.terminate()
  }

  override def beforeEach(): Unit = {
    logBuffer.clear()
  }

  "ControllerActor" must {
    "return OK on initialize" in {
      val controller = testKit.spawn(ControllerActor(logger), "controller")
      val probe = testKit.createTestProbe[ControllerResponse]()
      controller ! Initialize(testId, probe.ref)
      probe.expectMessage(OK(testId))
      testKit.stop(controller)
      eventually(logBuffer.size shouldBe 2)
      logBuffer.head.getString("message") shouldBe "In uninitialized state"
      logBuffer(1).getString("message") shouldBe "In idle state"
    }

    "return OK on configureExposure" in {
      val controller = testKit.spawn(ControllerActor(logger), "controller")
      val probe = testKit.createTestProbe[ControllerResponse]()
      controller ! Initialize(testId, probe.ref)
      probe.expectMessage(OK(testId))
      controller ! ConfigureExposure(testId2, probe.ref, ExposureParameters(5000, 3))
      probe.expectMessage(OK(testId2))
      testKit.stop(controller)
      eventually(logBuffer.size shouldBe 3)
      logBuffer.head.getString("message") shouldBe "In uninitialized state"
      logBuffer(1).getString("message") shouldBe "In idle state"
      logBuffer(2).getString("message") shouldBe "In idle state"

    }

    "take an exposure" in {
      val itime = 2000
      val coadds = 3
      val expectedExposureTime = itime * coadds
      val filename = "test.fits"

      val controller = testKit.spawn(ControllerActor(logger), "controller")
      val probe = testKit.createTestProbe[ControllerResponse]()
      controller ! Initialize(testId, probe.ref)
      probe.expectMessage(OK(testId))
      controller ! ConfigureExposure(testId2, probe.ref, ExposureParameters(itime, coadds))
      probe.expectMessage(OK(testId2))
      controller ! StartExposure(testId3, probe.ref, filename)
      probe.expectMessage(ExposureStarted(testId3))
      probe.expectNoMessage(expectedExposureTime.millis)
      probe.expectMessage(ExposureFinished(testId3, filename))
      testKit.stop(controller)

      val exposureTimerPeriod = config.getInt("exposureTimerPeriod")
      val expectedExposureMessages = expectedExposureTime / exposureTimerPeriod

      val expectedNumLogMessages = 5 + expectedExposureMessages
      eventually(logBuffer.size shouldBe expectedNumLogMessages)
      logBuffer.head.getString("message") shouldBe "In uninitialized state"
      logBuffer(1).getString("message") shouldBe "In idle state"
      logBuffer(2).getString("message") shouldBe "In idle state"
      logBuffer(3).getString("message").contains("Starting exposure") shouldBe true
      logBuffer(expectedExposureMessages+3).getString("message") shouldBe "Exposure Complete"
      logBuffer(expectedExposureMessages+4).getString("message") shouldBe "In idle state"

    }

    "abort an exposure" in {
      val filename = "test.fits"
      val controller = testKit.spawn(ControllerActor(logger), "controller")
      val probe = testKit.createTestProbe[ControllerResponse]()
      controller ! Initialize(testId, probe.ref)
      probe.expectMessage(OK(testId))
      controller ! ConfigureExposure(testId2, probe.ref, ExposureParameters(5000, 1))
      probe.expectMessage(OK(testId2))
      controller ! StartExposure(testId3, probe.ref, filename)
      probe.expectMessage(ExposureStarted(testId3))
      Thread.sleep(1000)
      controller ! AbortExposure(testId4, probe.ref)
      probe.expectMessage(OK(testId4))
      probe.expectMessage(100.millis, ExposureFinished(testId4, filename))
      probe.expectNoMessage(5.seconds)
      testKit.stop(controller)

      val exposureTimerPeriod = config.getInt("exposureTimerPeriod")
      val expectedExposureMessages = 1000 / exposureTimerPeriod

      val expectedNumLogMessages = 7 + expectedExposureMessages
      eventually(logBuffer.size shouldBe expectedNumLogMessages)
      logBuffer.head.getString("message") shouldBe "In uninitialized state"
      logBuffer(1).getString("message") shouldBe "In idle state"
      logBuffer(2).getString("message") shouldBe "In idle state"
      logBuffer(3).getString("message").contains("Starting exposure") shouldBe true
      logBuffer(expectedExposureMessages+3).getString("message") shouldBe "Exposure Aborted"
      logBuffer(expectedExposureMessages+4).getString("message") shouldBe "Exposure Complete"
      logBuffer(expectedExposureMessages+5).getString("message") shouldBe "In idle state"
      logBuffer(expectedExposureMessages+6).getString("message") shouldBe "In idle state"

    }
  }

}