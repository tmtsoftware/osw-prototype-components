package org.tmt.osw.simulatedinfrareddetector

import java.net.InetAddress

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import csw.logging.client.appenders.{LogAppenderBuilder, StdOutAppender}
import csw.logging.client.internal.LoggingSystem
import csw.logging.client.scaladsl.{LoggerFactory, LoggingSystemFactory}
import csw.prefix.models.Prefix
import org.scalatest.wordspec.AnyWordSpecLike
import org.tmt.osw.simulatedinfrareddetector.ControllerMessages._
import play.api.libs.json.{JsObject, Json}

import scala.collection.mutable
import scala.concurrent.duration._

class TestAppender(callback: Any => Unit) extends LogAppenderBuilder {

  def apply(system: ActorSystem[_], stdHeaders: JsObject): StdOutAppender =
    new StdOutAppender(system, stdHeaders, callback)
}


class ControllerActorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  private lazy val actorSystem                    = ActorSystem(SpawnProtocol(), "test")
  private val hostName = InetAddress.getLocalHost.getHostName
  private lazy val loggingSystem = LoggingSystemFactory.start("logging", "version", hostName, actorSystem)

  private val loggerFactory = new LoggerFactory(Prefix("csw.SequenceComponentTest"))
  private val logger = loggerFactory.getLogger
  protected val logBuffer: mutable.Buffer[JsObject] = mutable.Buffer.empty[JsObject]
  protected val testAppender                        = new TestAppender(x => logBuffer += Json.parse(x.toString).as[JsObject])

  override def beforeAll(): Unit = {
    super.beforeAll()
    loggingSystem.setAppenders(List(testAppender))
  }

  override def afterAll(): Unit = {
    logBuffer.foreach(println)
  }

  "ControllerActor" must {
    "return OK on initialize" in {
      val controller = testKit.spawn(ControllerActor(logger), "controller")
      val probe = testKit.createTestProbe[ControllerResponse]()
      controller ! Initialize(probe.ref)
      probe.expectMessage(OK)
    }

    "return OK on configureExposure" in {
      val controller = testKit.spawn(ControllerActor(logger), "controller")
      val probe = testKit.createTestProbe[ControllerResponse]()
      controller ! Initialize(probe.ref)
      probe.expectMessage(OK)
      controller ! ConfigureExposure(ExposureParameters(5000, 3), probe.ref)
      probe.expectMessage(OK)
    }

    "take exposures" in {
      val controller = testKit.spawn(ControllerActor(logger), "controller")
      val probe = testKit.createTestProbe[ControllerResponse]()
      controller ! Initialize(probe.ref)
      probe.expectMessage(OK)
      controller ! ConfigureExposure(ExposureParameters(5000, 3), probe.ref)
      probe.expectMessage(OK)
      controller ! StartExposure("test", probe.ref)
      probe.expectMessage(ExposureStarted)
      probe.expectNoMessage(15.seconds)
      probe.expectMessage(ExposureFinished)
    }

    "abort exposures" in {
      val controller = testKit.spawn(ControllerActor(logger), "controller")
      val probe = testKit.createTestProbe[ControllerResponse]()
      controller ! Initialize(probe.ref)
      probe.expectMessage(OK)
      controller ! ConfigureExposure(ExposureParameters(5000, 1), probe.ref)
      probe.expectMessage(OK)
      controller ! StartExposure("test", probe.ref)
      probe.expectMessage(ExposureStarted)
      Thread.sleep(1000)
      controller ! AbortExposure(probe.ref)
      probe.expectMessage(OK)
      probe.expectMessage(100.millis, ExposureFinished)
      probe.expectNoMessage(5.seconds)
    }
  }

}