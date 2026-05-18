package org.slurmdroid.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import org.slurmdroid.service.SshForegroundService
import org.slurmdroid.ui.theme.SlurmDroidTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SshForegroundService.start(this)
        setContent {
            SlurmDroidTheme {
                AppNavigation()
            }
        }
    }
}
