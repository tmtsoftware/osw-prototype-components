package org.tmt.osw.simulatedinfrareddetectorhcd

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.{ActorRef, ActorSystem, SpawnProtocol}
import com.typesafe.config.ConfigFactory
import csw.logging.client.appenders.{LogAppenderBuilder, StdOutAppender}
import csw.logging.client.internal.JsonExtensions.RichJsObject
import csw.logging.client.scaladsl.{LoggerFactory, LoggingSystemFactory}
import csw.params.core.models._
import csw.params.core.states.CurrentState
import csw.prefix.models.Prefix
import csw.prefix.models.Subsystem.CSW
import org.mockito.MockitoSugar.mock
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike
import org.tmt.osw.simulatedinfrareddetectorhcd.ControllerMessages._
import play.api.libs.json.{JsObject, Json}

import java.net.InetAddress
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

  private val prefix = Prefix("ESW.SimulatedInfraredDetectorHcd")
  private val loggerFactory = new LoggerFactory(prefix)
  private val logger        = loggerFactory.getLogger

  private val currentStatePublisherMock = mock[ActorRef[CurrentState]]

  protected val logBuffer: mutable.Buffer[JsObject] = mutable.Buffer.empty[JsObject]
  protected val testAppender                        = new TestAppender(x => {
      //print(x.toString)
      logBuffer += Json.parse(x.toString).as[JsObject]
  })


  lazy private val config = ConfigFactory.load()

  private lazy val detectorDimensions: (Int, Int) = {
    (config.getInt("detector.xs"), config.getInt("detector.ys"))
  }
  private lazy val frameReadTimeMs =
    detectorDimensions._1 * detectorDimensions._2 * config.getDouble("pixelClockTimeMs") / 32.0


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
      val controller = testKit.spawn(ControllerActor(logger, currentStatePublisherMock, prefix), "controller")
      val probe = testKit.createTestProbe[ControllerResponse]()
      controller ! Initialize(testId, probe.ref)
      probe.expectMessage(OK(testId))
      testKit.stop(controller)
      eventually(logBuffer.size shouldBe 2)
      logBuffer.head.getString("message") shouldBe "In uninitialized state"
      logBuffer(1).getString("message") shouldBe "In idle state"
    }

    "return OK on configureExposure" in {
      val controller = testKit.spawn(ControllerActor(logger, currentStatePublisherMock, prefix), "controller")
      val probe = testKit.createTestProbe[ControllerResponse]()
      controller ! Initialize(testId, probe.ref)
      probe.expectMessage(OK(testId))
      controller ! ConfigureExposure(testId2, probe.ref, ExposureParameters(2,4,2))
      probe.expectMessage(OK(testId2))
      testKit.stop(controller)
      eventually(logBuffer.size shouldBe 3)
      logBuffer.head.getString("message") shouldBe "In uninitialized state"
      logBuffer(1).getString("message") shouldBe "In idle state"
      logBuffer(2).getString("message") shouldBe "In idle state"

    }

    "take an exposure" in {
      val resets = 2
      val reads = 4
      val ramps = 2
      val obsId = ObsId("2021A-001-002")
      val exposureId = ExposureId(obsId, CSW, "DET1", TYPLevel("SCI1"), ExposureNumber(1))
      val filename = "test.fits"
      val expectedExposureTime = (resets+reads)*ramps*frameReadTimeMs

      val currentStateProbe = testKit.createTestProbe[CurrentState]()
      val controller = testKit.spawn(ControllerActor(logger, currentStateProbe.ref, prefix), "controller")
      val probe = testKit.createTestProbe[ControllerResponse]()
      controller ! Initialize(testId, probe.ref)
      probe.expectMessage(OK(testId))
      controller ! ConfigureExposure(testId2, probe.ref, ExposureParameters(resets, reads, ramps))
      probe.expectMessage(OK(testId2))
      controller ! StartExposure(testId3, Some(obsId), exposureId, filename, probe.ref)
      probe.expectMessage(ExposureStarted(testId3))
      probe.expectNoMessage(expectedExposureTime.millis)
      val finishedMessage = probe.expectMessageType[ExposureFinished]
      finishedMessage.runId shouldBe testId3
      finishedMessage.exposureInfo.obsId shouldBe obsId
      finishedMessage.exposureInfo.exposureId shouldBe exposureId
      finishedMessage.exposureInfo.exposureFilename shouldBe filename
      testKit.stop(controller)

      val exposureTimerPeriod = config.getInt("exposureTimerPeriod")
      //println(expectedExposureTime)
      val expectedExposureLoops = (expectedExposureTime / exposureTimerPeriod).toInt+1
      val expectedExposureMessages = expectedExposureLoops * 3

      val expectedNumLogMessages = 6 + expectedExposureMessages
      eventually(logBuffer.size shouldBe expectedNumLogMessages)
      logBuffer.head.getString("message") shouldBe "In uninitialized state"
      logBuffer(1).getString("message") shouldBe "In idle state"
      logBuffer(2).getString("message") shouldBe "In idle state"
      logBuffer(3).getString("message").contains("Starting exposure") shouldBe true
      logBuffer(expectedExposureMessages+4).getString("message") shouldBe "Exposure Complete"
      logBuffer(expectedExposureMessages+5).getString("message") shouldBe "In idle state"

      currentStateProbe.receiveMessages(expectedExposureLoops)

    }

    "abort an exposure" in {
      val resets = 2
      val reads = 10
      val ramps = 4
      val obsId = ObsId("2021A-001-002")
      val exposureId = ExposureId(obsId, CSW, "DET1", TYPLevel("SCI1"), ExposureNumber(1))
      val filename = "test.fits"
      val currentStateProbe = testKit.createTestProbe[CurrentState]()
      val controller = testKit.spawn(ControllerActor(logger, currentStateProbe.ref, prefix), "controller")
      val probe = testKit.createTestProbe[ControllerResponse]()
      controller ! Initialize(testId, probe.ref)
      probe.expectMessage(OK(testId))
      controller ! ConfigureExposure(testId2, probe.ref, ExposureParameters(resets, reads, ramps))
      probe.expectMessage(OK(testId2))
      controller ! StartExposure(testId3, Some(obsId), exposureId, filename, probe.ref)
      probe.expectMessage(ExposureStarted(testId3))
      Thread.sleep(10 * frameReadTimeMs.toInt)
      controller ! AbortExposure(testId4, probe.ref)
      probe.expectMessage(OK(testId4))
      val finishedMessage = probe.expectMessageType[ExposureFinished](50.millis)
      finishedMessage.runId shouldBe testId4
      finishedMessage.exposureInfo.obsId shouldBe obsId
      finishedMessage.exposureInfo.exposureId shouldBe exposureId
      finishedMessage.exposureInfo.exposureFilename shouldBe filename
      probe.expectNoMessage(5.seconds)
      testKit.stop(controller)

      val exposureTimerPeriod = config.getInt("exposureTimerPeriod")
      val expectedExposureLoops = (10.0 * frameReadTimeMs.toInt / exposureTimerPeriod).toInt - 1
      val expectedExposureMessages = expectedExposureLoops * 3

      val expectedNumLogMessages = 8 + expectedExposureMessages
      eventually(logBuffer.size shouldBe expectedNumLogMessages)
      logBuffer.head.getString("message") shouldBe "In uninitialized state"
      logBuffer(1).getString("message") shouldBe "In idle state"
      logBuffer(2).getString("message") shouldBe "In idle state"
      logBuffer(3).getString("message").contains("Starting exposure") shouldBe true
      logBuffer(expectedExposureMessages+4).getString("message") shouldBe "Exposure Aborted"
      logBuffer(expectedExposureMessages+5).getString("message") shouldBe "Exposure Complete"
      logBuffer(expectedExposureMessages+6).getString("message") shouldBe "In idle state"
      logBuffer(expectedExposureMessages+7).getString("message") shouldBe "In idle state"

      currentStateProbe.receiveMessages(expectedExposureLoops)

    }
  }

}