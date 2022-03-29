package org.tmt.osw.simulatedinfrareddetector

import java.io.File

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import csw.logging.client.scaladsl.LoggerFactory
import csw.prefix.models.Prefix
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike
import org.tmt.osw.simulatedinfrareddetector.ControllerMessages.{DataWritten, FitsResponse, WriteData}

import scala.concurrent.duration._

class FitsActorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike with BeforeAndAfterEach {
  private val loggerFactory = new LoggerFactory(Prefix("CSW.SimulatedInfraredDetector"))
  private val logger        = loggerFactory.getLogger

  "FitsActor" must {
    "write FITS file on WriteData" in {
      val filename = "FitsActorTest.fits"
      val data = Array(
        Array(1, 2, 3, 4),
        Array(2, 4, 6, 8),
        Array(3, 4, 5, 6),
        Array(4, 8, 12, 16)
      )
      val fits  = testKit.spawn(FitsActor(logger), "fits")
      val probe = testKit.createTestProbe[FitsResponse]()
      fits ! WriteData(filename, FitsData(data), probe.ref)
      probe.expectMessage(10.seconds, DataWritten(filename))

      val file = new File(filename)
      file.exists() shouldBe true
      file.delete()
    }
  }
}
