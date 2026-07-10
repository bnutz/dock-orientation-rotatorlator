/*
 * Overhaul Phase 2, 2026-07: the domain logic of the old ReceiverPortStatus (2018) and
 * ObserverRotationSetting (2018), reshaped as Flows + suspend functions. The
 * LocalBroadcastManager event bus is gone — consumers collect these Flows directly.
 */

package com.justbnutz.dockorientationrotatorlator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Watches and controls the device's power/dock state and rotation-lock settings.
 *
 * To test Dock Mode, run:
 * ./adb shell am broadcast -a android.intent.action.DOCK_EVENT --ei android.intent.extra.DOCK_STATE 0
 */
class PowerStateRepository(private val context: Context) {

    // region ================== EVENT FLOWS ==================
    // ====== ================== =========== ==================

    /**
     * Stream of PowerStatus values: emits the current status immediately, then again on
     * every power/dock broadcast while collected. Each collection registers its own
     * receiver, so the Service keeps its stream when the UI goes away.
     */
    fun powerStatusFlow(): Flow<PowerStatus> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                trySend(currentPowerStatus())
            }
        }

        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_DOCK_EVENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        // RECEIVER_EXPORTED: system broadcasts originate outside this app (explicit
        // flag required from targetSdk 34)
        ContextCompat.registerReceiver(context, receiver, intentFilter, ContextCompat.RECEIVER_EXPORTED)

        // Seed with the current state
        trySend(currentPowerStatus())

        awaitClose { context.unregisterReceiver(receiver) }
    }

    /**
     * Emits on every change to the system rotation settings (and once on collect), so
     * the UI can refresh its rotation-lock labels.
     */
    fun rotationSettingFlow(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }

        context.contentResolver.registerContentObserver(
            Uri.withAppendedPath(Settings.System.CONTENT_URI, Settings.System.ACCELEROMETER_ROTATION),
            true,
            observer
        )

        // Seed so collectors render the initial state
        trySend(Unit)

        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    // endregion

    // region ================== ROTATION SETTING OPS ==================
    // ====== ================== ==================== ==================

    /**
     * Verifies we have permission to alter the system settings and if so, apply the
     * given rotation mode (auto-rotate flag + user-rotation value).
     */
    suspend fun applyRotationMode(rotationMode: RotationMode) {

        if (rotationMode == RotationMode.NO_CHANGE) return

        // Double-check permissions
        if (!Settings.System.canWrite(context)) return

        val isAutoRotate = rotationMode == RotationMode.AUTO_ROTATE

        // Apply the Auto-Rotate setting
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            if (isAutoRotate) 1 else 0
        )

        // If we're disabling Auto-Rotate, then also need to set the User Rotation value.
        // Wait a little bit first, as some systems don't handle successive multiple
        // rotation changes too well (150ms, same as the 2018 Timer-based original).
        if (!isAutoRotate) {
            delay(150)
            setUserRotation(rotationMode)
        }
    }

    /**
     * If a fixed-orientation is chosen, then need to disable Auto-Rotate and then set the User Rotation
     * value accordingly - this will be relative to the "natural orientation" of the device, which needs
     * to be figured out separately.
     *
     * Reference: https://stackoverflow.com/a/9888357
     */
    private fun setUserRotation(userRotationMode: RotationMode) {

        // Fetch the "natural orientation" of the device (portrait vs landscape)
        val naturalOrientation = getNaturalOrientation()

        // Make sure we have a baseline orientation to reference against
        if (naturalOrientation == Configuration.ORIENTATION_UNDEFINED) return

        // Set the User Rotation value relative to the natural orientation of the device
        val userRotation = when (userRotationMode) {
            RotationMode.PORTRAIT ->
                if (naturalOrientation == Configuration.ORIENTATION_PORTRAIT) Surface.ROTATION_0 else Surface.ROTATION_90

            RotationMode.PORTRAIT_INVERTED ->
                if (naturalOrientation == Configuration.ORIENTATION_PORTRAIT) Surface.ROTATION_180 else Surface.ROTATION_270

            RotationMode.LANDSCAPE ->
                if (naturalOrientation == Configuration.ORIENTATION_LANDSCAPE) Surface.ROTATION_0 else Surface.ROTATION_90

            RotationMode.LANDSCAPE_INVERTED ->
                if (naturalOrientation == Configuration.ORIENTATION_LANDSCAPE) Surface.ROTATION_180 else Surface.ROTATION_270

            else -> -1
        }

        // Make sure we have a proper value to set and apply it
        if (userRotation >= 0) {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.USER_ROTATION,
                userRotation
            )
        }
    }

    /**
     * Return the current Rotation-Lock setting of the device
     * (0: Portrait | 1: Auto-Rotate)
     */
    fun isAutoRotate(): Boolean {
        return Settings.System.getInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0
        ) == 1
    }

    /**
     * Return the current User Rotation setting of the device
     * (Corresponds to Surface.Rotation constants)
     */
    fun getUserRotation(): Int {
        return Settings.System.getInt(
            context.contentResolver,
            Settings.System.USER_ROTATION,
            -1
        )
    }

    /**
     * The User Rotation value is relative to the "natural orientation" of the device. So "Surface.ROTATION_90"
     * will be different to a tablet that is naturally landscape vs a phone that is naturally portrait.
     *
     * Because of this, we need to determine the natural orientation of the current device to properly label
     * our settings.
     *
     * Reference: https://stackoverflow.com/a/9888357
     */
    @Suppress("DEPRECATION") // getDefaultDisplay: revisit alongside the Compose UI (Phase 3)
    fun getNaturalOrientation(): Int {

        // Retrieve the system objects for determining device layout measurements
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return Configuration.ORIENTATION_UNDEFINED

        // Get the current rotation and orientation values
        val currentRotation = windowManager.defaultDisplay.rotation
        val currentOrientation = context.resources.configuration.orientation

        // Natural orientation can be determined by comparing the two values against each other
        return if (
            ((currentRotation == Surface.ROTATION_0 || currentRotation == Surface.ROTATION_180)
                    && currentOrientation == Configuration.ORIENTATION_PORTRAIT)
            ||
            ((currentRotation == Surface.ROTATION_90 || currentRotation == Surface.ROTATION_270)
                    && currentOrientation == Configuration.ORIENTATION_LANDSCAPE)
        ) {
            Configuration.ORIENTATION_PORTRAIT
        } else {
            Configuration.ORIENTATION_LANDSCAPE
        }
    }

    // endregion

    // region ================== PORT STATUS OPS ==================
    // ====== ================== =============== ==================

    /**
     * Check the current _DOCK / _BATTERY state Intents and return what type of Port / Power status
     * we're currently at.
     *
     * References:
     * - https://developer.android.com/training/monitoring-device-state/docking-monitoring.html
     * - https://developer.android.com/training/monitoring-device-state/battery-monitoring.html
     */
    fun currentPowerStatus(): PowerStatus {
        return when {
            isWirelesslyCharging() -> PowerStatus.WIRELESSLY_CHARGING
            isPluggedIn() || isDocked() -> PowerStatus.PLUGGED_IN
            else -> PowerStatus.DISCONNECTED
        }
    }

    /**
     * Retrieve the current Dock state via sticky broadcasts and return whether we are currently docked
     * or not.
     */
    private fun isDocked(): Boolean {
        val dockState = getStateIntent(checkingBatteryState = false)
            ?.getIntExtra(Intent.EXTRA_DOCK_STATE, -1)
            ?: return false

        return dockState != Intent.EXTRA_DOCK_STATE_UNDOCKED
    }

    /**
     * Retrieve the current state of the USB port via sticky broadcasts and return whether we are actually
     * plugged in or not.
     */
    private fun isPluggedIn(): Boolean {
        val chargePlugState = getStateIntent(checkingBatteryState = true)
            ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            ?: return false

        return chargePlugState == BatteryManager.BATTERY_PLUGGED_AC
                || chargePlugState == BatteryManager.BATTERY_PLUGGED_USB
    }

    /**
     * Check if the device is currently wirelessly charging. Same logic as isPluggedIn()
     */
    private fun isWirelesslyCharging(): Boolean {
        val chargePlugState = getStateIntent(checkingBatteryState = true)
            ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            ?: return false

        return chargePlugState == BatteryManager.BATTERY_PLUGGED_WIRELESS
    }

    /**
     * Retrieve the "sticky" Intent of either Battery State or Dock State, used for checking current
     * power status of the device.
     *
     * "Sticky" broadcast Intents can be retrieved immediately by passing a null BroadcastReceiver.
     */
    private fun getStateIntent(checkingBatteryState: Boolean): Intent? {
        val intentFilter = IntentFilter(
            if (checkingBatteryState) Intent.ACTION_BATTERY_CHANGED else Intent.ACTION_DOCK_EVENT
        )
        return context.registerReceiver(null, intentFilter)
    }

    // endregion
}
