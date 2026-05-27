package org.slurmdroid.nnunet

import android.app.Application

class NnUNetPluginApp : Application() {
    val plugin: NnUNetPlugin by lazy { NnUNetPlugin() }
}
