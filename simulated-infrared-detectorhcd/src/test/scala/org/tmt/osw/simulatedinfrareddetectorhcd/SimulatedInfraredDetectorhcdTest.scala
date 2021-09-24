package org.tmt.osw.simulatedinfrareddetectorhcd

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import csw.command.client.CommandServiceFactory
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{ComponentId, ComponentType}
import csw.logging.client.scaladsl.LoggingSystemFactory
import csw.params.commands.CommandResponse.Completed
import csw.params.commands.{CommandResponse, Observe, Setup}
import csw.params.core.models.{ExposureId, ExposureNumber, ObsId, TYPLevel}
import csw.params.events.{Event, IRDetectorEvent, ObserveEvent, SystemEvent}
import csw.prefix.models.Subsystem.CSW
import csw.prefix.models.{Prefix, Subsystem}
import csw.testkit.scaladsl.CSWService.{AlarmServer, EventServer}
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import org.tmt.osw.simulatedinfrareddetectorhcd.HcdConstants.{commandName, keys}

import java.io.File
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._

class SimulatedInfraredDetectorhcdTest extends ScalaTestFrameworkTestKit(AlarmServer, EventServer) with AnyWordSpecLike {

  import frameworkTestKit._
  //actorRuntime.startLogging("hcdTest")
  //private implicit val actorSystem: ActorSystem[SpawnProtocol.Command] = frameworkTestKit.actorSystem
  LoggingSystemFactory.forTestingOnly()

  private val testPrefix   = Prefix(Subsystem.CSW, "test")
  private val hcdPrefix    = Prefix(Subsystem.CSW, "simulated.Infrared.DetectorHcd")
  private val connection   = AkkaConnection(ComponentId(hcdPrefix, ComponentType.HCD))

  private implicit val timeout: Timeout                                = 12.seconds

  override def beforeAll(): Unit = {
    super.beforeAll()
    // uncomment if you want one HCD run for all tests
    spawnStandalone(com.typesafe.config.ConfigFactory.load("SimulatedInfraredDetectorhcdStandalone.conf"))
  }

  "HCD should be locatable using Location Service" in {
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    akkaLocation.connection shouldBe connection
  }

  "return Completed on initialize command" in {
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get
    val assembly     = CommandServiceFactory.make(akkaLocation)

    val initializeCommand = Setup(testPrefix, commandName.initialize, None)

    Await.result(assembly.submitAndWait(initializeCommand), 2.seconds) shouldBe a[Completed]
  }

  "return ExposureFinished on initialize, configureExposure, and startExposure commands" in {
    val filename     = "hcdTest.fits"
    val obsId = ObsId("2020A-001-123")
    val subsystem = CSW
    val detector = "DET1"
    val typLevel = TYPLevel("SCI1")
    val exposureNumber = ExposureNumber("0001")
    val exposureId = ExposureId(subsystem, detector, typLevel, exposureNumber)

    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get
    val hcd     = CommandServiceFactory.make(akkaLocation)

    val initializeCommand = Setup(testPrefix, commandName.initialize, None)

    Await.result(hcd.submitAndWait(initializeCommand), 2.seconds) shouldBe a[Completed]

    val configureExposureCommand = Setup(testPrefix, commandName.configureExposure, None)
      .add(keys.resets.set(1))
      .add(keys.ramps.set(2))
      .add(keys.reads.set(10))

    Await.result(hcd.submitAndWait(configureExposureCommand), 2.seconds) shouldBe a[Completed]

    val startExposureCommand = Observe(testPrefix, commandName.startExposure, None)
      .add(keys.filename.set(filename))
      .add(keys.exposureId.set(exposureId.toString))


    val subscriber = eventService.defaultSubscriber
    val startExposureEvent = IRDetectorEvent.exposureStart(hcdPrefix, exposureId)
    val endExposureEvent = IRDetectorEvent.exposureEnd(hcdPrefix, exposureId)
    val dataWriteStartEvent = IRDetectorEvent.dataWriteStart(hcdPrefix, exposureId, filename)
    val dataWriteEndEvent = IRDetectorEvent.dataWriteEnd(hcdPrefix, exposureId, filename)
    val subscriptionStartEventList = mutable.ListBuffer[Event]()
    val subscriptionEndEventList = mutable.ListBuffer[Event]()
    val startEventSubscription = subscriber.subscribeCallback(Set(startExposureEvent.eventKey), { ev => subscriptionStartEventList.append(ev) })
    val endEventSubscription = subscriber.subscribeCallback(Set(endExposureEvent.eventKey, dataWriteStartEvent.eventKey, dataWriteEndEvent.eventKey),
      { ev => subscriptionEndEventList.append(ev) })

    val submitResult = Await.result(hcd.submit(startExposureCommand) , 2.seconds)
    submitResult shouldBe a[CommandResponse.Started]

    Await.result(hcd.queryFinal(submitResult.runId), 10.seconds) shouldBe a[Completed]

    val file = new File(filename)
    file.exists() shouldBe true
    file.delete()

    // check observe events
    val receivedStartEvent0 = subscriptionStartEventList.head
    val receivedStartEvent1 = subscriptionStartEventList(1)

    // First event is the "get" value of the event before it's been published.
    // therefore, it is an invalid System Event
    receivedStartEvent0 shouldBe a[SystemEvent]
    receivedStartEvent0.isInvalid shouldBe true
    receivedStartEvent0.eventName shouldBe startExposureEvent.eventName

    receivedStartEvent1 shouldBe a[ObserveEvent]
    receivedStartEvent1.isInvalid shouldBe false
    receivedStartEvent1.eventName shouldBe startExposureEvent.eventName
    receivedStartEvent1.asInstanceOf[ObserveEvent](keys.exposureId).head shouldBe exposureId.toString
    startEventSubscription.unsubscribe()

    subscriptionEndEventList.foreach(println)
    subscriptionEndEventList.size shouldBe 6
    val receivedEvent1 = subscriptionEndEventList.head
    val receivedEvent2 = subscriptionEndEventList(1)
    val receivedEvent3 = subscriptionEndEventList(2)
    val receivedEvent4 = subscriptionEndEventList(3)
    val receivedEvent5 = subscriptionEndEventList(4)
    val receivedEvent6 = subscriptionEndEventList(5)

    // First three events are the "get" value of each event before it's been published.
    // therefore, they are invalid System Events
    receivedEvent1 shouldBe a[SystemEvent]
    receivedEvent2 shouldBe a[SystemEvent]
    receivedEvent3 shouldBe a[SystemEvent]
    receivedEvent1.eventName shouldBe endExposureEvent.eventName
    receivedEvent1.isInvalid shouldBe true
    receivedEvent2.eventName shouldBe dataWriteStartEvent.eventName
    receivedEvent3.isInvalid shouldBe true
    receivedEvent3.eventName shouldBe dataWriteEndEvent.eventName
    receivedEvent3.isInvalid shouldBe true

    receivedEvent4 shouldBe a[ObserveEvent]
    receivedEvent5 shouldBe a[ObserveEvent]
    receivedEvent6 shouldBe a[ObserveEvent]

    receivedEvent4.eventName shouldBe endExposureEvent.eventName
    receivedEvent4.isInvalid shouldBe false
    receivedEvent4.asInstanceOf[ObserveEvent](keys.exposureId).head shouldBe exposureId.toString
    receivedEvent5.eventName shouldBe dataWriteStartEvent.eventName
    receivedEvent5.isInvalid shouldBe false
    receivedEvent5.asInstanceOf[ObserveEvent](keys.exposureId).head shouldBe exposureId.toString
    receivedEvent6.eventName shouldBe dataWriteEndEvent.eventName
    receivedEvent6.isInvalid shouldBe false
    receivedEvent6.asInstanceOf[ObserveEvent](keys.exposureId).head shouldBe exposureId.toString

    endEventSubscription.unsubscribe()
  }
}
