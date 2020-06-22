package org.tmt.osw.simulatedinfrareddetector

import csw.params.commands.CommandName
import csw.params.core.generics.{Key, KeyType}

object AssemblyConstants {
  object commandName {
    val initialize: CommandName = CommandName("initialize")
    val configureExposure: CommandName = CommandName("configureExposure")
    val startExposure: CommandName = CommandName("startExposure")
    val abortExposure: CommandName = CommandName("abortExposure")
    val shutdown: CommandName = CommandName("shutdown")
  }
  object keys {
    val filename: Key[String] = KeyType.StringKey.make("filename")
    val integrationTime: Key[Int] = KeyType.IntKey.make("integrationTime")
    val coaddition: Key[Int] = KeyType.IntKey.make("coadditions")
  }

}
