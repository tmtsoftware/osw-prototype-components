package org.tmt.osw.deploy

import csw.framework.deploy.hostconfig.HostConfig
import csw.prefix.models.Subsystem

object PrototypeComponentHostConfigApp extends App {

  HostConfig.start("prototype-component-host-config-app", Subsystem.withNameInsensitive("OSW"), args)

}
