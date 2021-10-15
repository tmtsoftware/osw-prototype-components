package org.tmt.osw.simulatedinfrareddetector

import csw.params.commands.CommandName
import csw.params.core.generics.{Key, KeyType}

object AssemblyConstants {
  object commandName {
    val initialize: CommandName        = CommandName("INIT")
    val configureExposure: CommandName = CommandName("LOAD_CONFIGURATION")
    val startExposure: CommandName     = CommandName("START_EXPOSURE")
    val abortExposure: CommandName     = CommandName("ABORT_EXPOSURE")
    val shutdown: CommandName          = CommandName("SHUTDOWN")
  }
  object keys {
    val filename: Key[String]     = KeyType.StringKey.make("filename")
    val integrationTime: Key[Int] = KeyType.IntKey.make("rampIntegrationTime")
    val coaddition: Key[Int]      = KeyType.IntKey.make("ramps")
  }

}
