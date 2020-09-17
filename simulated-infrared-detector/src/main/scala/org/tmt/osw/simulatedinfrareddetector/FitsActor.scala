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
      case WriteData(filename, data,  replyTo) =>
        writeData(logger, data, filename)
        replyTo ! DataWritten(filename)
        Behaviors.same
    }



  private def writeData(logger: Logger, data: FitsData, filename: String): Unit = {
    logger.info(s"Writing data.  Image dimensions = ${data.dimensions}")
    if (config.getBoolean("writeDataToFile")) {
      val fits = new Fits()
      fits.addHDU(FitsFactory.hduFactory(data.data))
      val bf = new BufferedFile(filename, "rw")
      fits.write(bf)
      bf.close()
    }
  }

}
