/*
 * The app's only Activity.
 *
 * In the 2018 app there were three (Splash → Rotatorlator → TestPanel), each with its own
 * XML layout, menu, and lifecycle. A Compose app typically has ONE: navigation between
 * "screens" becomes a choice between composable functions (see RotatorlatorApp), so extra
 * Activities stop earning their keep.
 *
 * Note it extends ComponentActivity, not AppCompatActivity — Compose doesn't need the
 * AppCompat theme/widget machinery, so the whole appcompat dependency is gone.
 */

package com.justbnutz.dockorientationrotatorlator.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {

    // Survives configuration changes (rotation, dark-mode switch) — the reason the
    // repositories and Flows live in the ViewModel rather than here.
    private val viewModel: RotatorlatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called BEFORE super.onCreate(): hands the Android 12+ system splash
        // screen over to us and lets us dismiss it once the first frame is ready.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Let our content draw behind the status/navigation bars. Android 15+ enforces
        // this for apps targeting SDK 35 anyway; the Compose Scaffold then hands each
        // screen the inset padding it needs to stay clear of the bars (see the `padding`
        // lambda parameter in RotatorlatorScreens).
        enableEdgeToEdge()

        // If the device rebooted, the monitoring Service is dead — restart it if the
        // stored preference says monitoring should be on.
        viewModel.ensureServiceRunningIfEnabled()

        // setContent replaces setContentView(R.layout.…): instead of inflating an XML
        // tree, it hosts the composable tree and re-runs it whenever the state it reads
        // changes. Everything the user sees hangs off these two calls.
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
