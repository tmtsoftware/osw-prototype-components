package org.tmt.osw.simulatedinfrareddetector

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory
import csw.logging.api.scaladsl.Logger
import nom.tam.fits.{Fits, FitsFactory}
import nom.tam.util.BufferedFile
import org.tmt.osw.simulatedinfrareddetector.ControllerMessages.{DataWritten, FitsMessage, WriteData}

object FitsActor {
  lazy private val config = ConfigFactory.load()

    def apply(logger: Logger): Behaviors.Receive[FitsMessage] = Behaviors.receiveMessagePartial[FitsMessage] {
      case WriteData(filename, replyTo) =>
        writeData(logger, filename)
        replyTo ! DataWritten(filename)
        Behaviors.same
    }

  private lazy val detectorDimensions: (Int, Int) =  {
    (config.getInt("detector.xs"), config.getInt("detector.ys"))
  }

  private def writeData(logger: Logger, filename: String): Unit = {
    logger.info(s"Writing data.  Image dimensions = $detectorDimensions")
    if (config.getBoolean("writeDataToFile")) {
      val data = generateFakeImageData(detectorDimensions._1, detectorDimensions._2)
      val fits = new Fits()
      fits.addHDU(FitsFactory.hduFactory(data))
      val bf = new BufferedFile(filename, "rw")
      fits.write(bf)
      bf.close()
    }

  }

  private def generateFakeImageData(xs: Int, ys: Int) = {
    Array.tabulate(xs, ys) {
      case (x,y) => x*xs+y
    }

  }

}
