package org.slurmdroid.nnunet

import org.slurmdroid.plugin.api.ISlurmDroidPlugin
import org.slurmdroid.plugin.api.SlurmDroidPluginService

class NnUNetPluginService : SlurmDroidPluginService() {
    override fun createPlugin(): ISlurmDroidPlugin =
        (application as NnUNetPluginApp).plugin
}
