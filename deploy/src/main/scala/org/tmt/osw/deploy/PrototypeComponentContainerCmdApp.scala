package org.tmt.osw.deploy

import csw.framework.deploy.containercmd.ContainerCmd
import csw.prefix.models.Subsystem

object PrototypeComponentContainerCmdApp extends App {

   ContainerCmd.start("prototype-component-container-cmd-app", Subsystem.withNameInsensitive("OSW"),args)

}
