package org.tmt.osw.deploy

import csw.framework.deploy.containercmd.ContainerCmd
import csw.prefix.models.Subsystem

object PrototypeComponentContainerCmdApp extends App {

  ContainerCmd.start("prototype_component_container_cmd_app", Subsystem.withNameInsensitive("CSW"), args)

}
