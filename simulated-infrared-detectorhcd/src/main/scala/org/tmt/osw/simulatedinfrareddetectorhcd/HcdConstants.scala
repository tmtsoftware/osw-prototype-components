package org.tmt.osw.simulatedinfrareddetectorhcd

import csw.params.commands.CommandName
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.states.StateName

object HcdConstants {
  object commandName {
    val initialize: CommandName        = CommandName("initialize")
    val configureExposure: CommandName = CommandName("configureExposure")
    val startExposure: CommandName     = CommandName("startExposure")
    val abortExposure: CommandName     = CommandName("abortExposure")
    val shutdown: CommandName          = CommandName("shutdown")
  }
  object keys {
    val filename: Key[String]     = KeyType.StringKey.make("filename")
    val resets: Key[Int] = KeyType.IntKey.make("resets")
    val reads: Key[Int]      = KeyType.IntKey.make("reads")
    val ramps: Key[Int]      = KeyType.IntKey.make("ramps")

    val rampsDone: Key[Int]       = KeyType.IntKey.make("rampsDone")
    val readsDone: Key[Int]       = KeyType.IntKey.make("readsDone")
  }
  val currentStateName: StateName = StateName("controllerState")
}
