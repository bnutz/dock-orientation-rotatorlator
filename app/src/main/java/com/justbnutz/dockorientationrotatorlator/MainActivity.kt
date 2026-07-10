/*
 * Overhaul Phase 3, 2026-07: single-Activity Compose app. Replaces ActivitySplashScreen
 * (system splash via androidx.core.splashscreen; the WRITE_SETTINGS gate is now Compose
 * state inside RotatorlatorApp) and ActivityRotatorlator (fragment/menu host).
 */

package com.justbnutz.dockorientationrotatorlator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {

    private val viewModel: RotatorlatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Real edge-to-edge: Compose insets handle the system bars (the old
        // values-v35 opt-out overlay is gone)
        enableEdgeToEdge()

        // If the device was previously rebooted then the Service will be stopped,
        // restart it if the monitor pref says it should be running
        viewModel.ensureServiceRunningIfEnabled()

        setContent {
            RotatorlatorTheme {
                RotatorlatorApp(
                    viewModel = viewModel,
                    onExit = { finish() }
                )
            }
        }
    }
}
