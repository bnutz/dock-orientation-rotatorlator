/*
 * Overhaul Phase 2, 2026-07: the domain logic of the old ReceiverPortStatus (2018) and
 * ObserverRotationSetting (2018), reshaped as Flows + suspend functions. The
 * LocalBroadcastManager event bus is gone — consumers collect these Flows directly.
 */

package com.justbnutz.dockorientationrotatorlator.data

import com.justbnutz.dockorientationrotatorlator.model.PowerStatus
import com.justbnutz.dockorientationrotatorlator.model.RotationMode

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
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Watches and controls the device's power/dock state and rotation-lock settings.
 *
 * To test Dock Mode, run:
 * ./adb shell am broadcast -a android.intent.action.DOCK_EVENT --ei android.intent.extra.DOCK_STATE 0
 */
class PowerStateRepository(private val context: Context) {

    private companion object {
        /** EXTRA_PLUGGED is 0 when running on battery; there is no named constant for it. */
        const val PLUG_TYPE_NONE = 0

        /** No sticky ACTION_DOCK_EVENT has ever been sent (a phone that has never been docked). */
        const val DOCK_STATE_UNKNOWN = -1
    }

    // region ================== EVENT FLOWS ==================
    // ====== ================== =========== ==================

    /**
     * Stream of PowerStatus values: emits the current status immediately, then again on
     * every power/dock change while collected. Each collection registers its own
     * receiver, so the Service keeps its stream when the UI goes away.
     *
     * ACTION_BATTERY_CHANGED is the authoritative source of the plug state, and it is
     * listened to for a reason. ACTION_POWER_CONNECTED / _DISCONNECTED carry NO plug-type
     * extra, so the only way to answer "USB or wireless?" from them is to re-read the
     * sticky ACTION_BATTERY_CHANGED — and at the moment those edge broadcasts are
     * delivered, that sticky can still hold the PREVIOUS EXTRA_PLUGGED value (notably on
     * a wireless pad, where the plug type settles only once charging negotiates). Reading
     * it there yields the old status, and since nothing re-reads afterwards the app stays
     * stale until something re-collects this Flow — which is exactly what rotating the
     * screen used to do, and why the state only appeared to update "after an orientation
     * change".
     *
     * So: hold the two inputs (plug type, dock state) as state, and update each from the
     * extras of the broadcast that actually carries it.
     */
    fun powerStatusFlow(): Flow<PowerStatus> = callbackFlow {

        // Seed from the sticky broadcasts, then keep updated from the broadcasts themselves
        var plugType = stickyPlugType()
        var dockState = stickyDockState()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    // Carries EXTRA_PLUGGED: the value we actually care about
                    Intent.ACTION_BATTERY_CHANGED ->
                        plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, plugType)

                    Intent.ACTION_DOCK_EVENT ->
                        dockState = intent.getIntExtra(Intent.EXTRA_DOCK_STATE, dockState)

                    // No plug-type extra on these two. They're kept because they fire
                    // promptly on the physical plug/unplug edge; the sticky re-read is a
                    // best-effort refresh, and any staleness is corrected moments later by
                    // the ACTION_BATTERY_CHANGED that always follows.
                    Intent.ACTION_POWER_CONNECTED,
                    Intent.ACTION_POWER_DISCONNECTED ->
                        plugType = stickyPlugType()
                }

                trySend(statusOf(plugType, dockState))
            }
        }

        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_DOCK_EVENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        // RECEIVER_EXPORTED: system broadcasts originate outside this app (explicit
        // flag required from targetSdk 34)
        ContextCompat.registerReceiver(context, receiver, intentFilter, ContextCompat.RECEIVER_EXPORTED)

        // Seed with the current state
        trySend(statusOf(plugType, dockState))

        awaitClose { context.unregisterReceiver(receiver) }
    }
        // ACTION_BATTERY_CHANGED also fires on every battery level/temperature tick, which
        // says nothing about the plug. Collapse those: without this, the Service would
        // re-apply the rotation setting (a Settings.System write) every few seconds.
        .distinctUntilChanged()

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
     * Derive the Power status from the two inputs the system gives us, rather than from a
     * fresh sticky read — see powerStatusFlow() for why the timing of that read matters.
     *
     * References:
     * - https://developer.android.com/training/monitoring-device-state/docking-monitoring.html
     * - https://developer.android.com/training/monitoring-device-state/battery-monitoring.html
     */
    private fun statusOf(plugType: Int, dockState: Int): PowerStatus {
        val isWireless = plugType == BatteryManager.BATTERY_PLUGGED_WIRELESS
        val isPlugged = plugType == BatteryManager.BATTERY_PLUGGED_AC
                || plugType == BatteryManager.BATTERY_PLUGGED_USB
        val isDocked = dockState != Intent.EXTRA_DOCK_STATE_UNDOCKED && dockState != DOCK_STATE_UNKNOWN

        return when {
            isWireless -> PowerStatus.WIRELESSLY_CHARGING
            isPlugged || isDocked -> PowerStatus.PLUGGED_IN
            else -> PowerStatus.DISCONNECTED
        }
    }

    /**
     * The current power status, read from the sticky broadcasts. Only used to SEED the Flow:
     * once collecting, the state is tracked from the broadcast extras instead.
     */
    fun currentPowerStatus(): PowerStatus = statusOf(stickyPlugType(), stickyDockState())

    /** Plug type (BatteryManager.BATTERY_PLUGGED_*) from the sticky ACTION_BATTERY_CHANGED. */
    private fun stickyPlugType(): Int =
        stickyIntent(Intent.ACTION_BATTERY_CHANGED)
            ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, PLUG_TYPE_NONE)
            ?: PLUG_TYPE_NONE

    /** Dock state (Intent.EXTRA_DOCK_STATE_*) from the sticky ACTION_DOCK_EVENT. */
    private fun stickyDockState(): Int =
        stickyIntent(Intent.ACTION_DOCK_EVENT)
            ?.getIntExtra(Intent.EXTRA_DOCK_STATE, DOCK_STATE_UNKNOWN)
            ?: DOCK_STATE_UNKNOWN

    /**
     * "Sticky" broadcast Intents can be retrieved immediately by passing a null BroadcastReceiver.
     */
    private fun stickyIntent(action: String): Intent? =
        context.registerReceiver(null, IntentFilter(action))

    // endregion
}
