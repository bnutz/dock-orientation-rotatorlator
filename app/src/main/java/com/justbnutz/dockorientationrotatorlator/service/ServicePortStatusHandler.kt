/*
 * .service — the headless half of the app: the foreground Service that does the actual
 * work (and the receiver that restarts it after an app update). It consumes the same
 * .data repositories the UI does, and runs whether or not any UI exists.
 *
 * Created by Brian Lau on 2018-03-23
 * Copyright (c) 2018. All rights reserved.
 *
 * Overhaul Phase 2, 2026-07: the Service now collects PowerStateRepository's Flow in a
 * coroutine instead of hosting a BroadcastReceiver + LocalBroadcastManager bus. It is
 * the single place that applies the rotation setting: power events AND pref changes
 * both land in the combined Flow below.
 */

package com.justbnutz.dockorientationrotatorlator.service

import com.justbnutz.dockorientationrotatorlator.R
import com.justbnutz.dockorientationrotatorlator.data.PowerStateRepository
import com.justbnutz.dockorientationrotatorlator.data.PrefsRepository
import com.justbnutz.dockorientationrotatorlator.model.PowerStatus
import com.justbnutz.dockorientationrotatorlator.model.RotationMode
import com.justbnutz.dockorientationrotatorlator.ui.MainActivity

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Foreground Service that watches power/dock state while the monitor is enabled, applies
 * the user's chosen rotation-lock setting, and shows the ongoing status notification.
 *
 * - 2018/03/23
 */
class ServicePortStatusHandler : Service() {

    companion object {
        // Literal (ActivityRotatorlator and its PACKAGE_NAME constant were retired in
        // Phase 3). The derived notification-channel id must stay stable for existing
        // installs, so never change this string.
        private const val TAG = "com.justbnutz.dockorientationrotatorlator.ServicePortStatusHandler"

        private const val ACTION_KEY_STOP_MONITORING = "$TAG.ACTION_KEY_STOP_MONITORING"

        fun startRotatorlatorService(context: Context) {
            context.startForegroundService(
                Intent(context, ServicePortStatusHandler::class.java)
            )
        }

        fun stopRotatorlatorService(context: Context) {
            context.stopService(
                Intent(context, ServicePortStatusHandler::class.java)
            )
        }
    }

    private lateinit var prefsRepo: PrefsRepository
    private lateinit var powerRepo: PowerStateRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitorJob: Job? = null

    // Notification Tools
    private var notificationManager: NotificationManager? = null
    private var notificationBuilder: NotificationCompat.Builder? = null
    private val notificationId = 10

    // region ================== SERVICE DEFAULT ACTIONS ==================
    // ====== ================== ======================= ==================

    override fun onBind(intent: Intent?): IBinder? {
        // Not binding to anything so can return null
        return null
    }

    override fun onCreate() {
        super.onCreate()

        prefsRepo = PrefsRepository(this)
        powerRepo = PowerStateRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // Running with START_STICKY, so Intent might be null - if not, the only Action
        // we care about is the notification's Stop button
        if (intent?.action == ACTION_KEY_STOP_MONITORING) {
            stopSelf()
            return START_STICKY
        }

        // Only monitor if the master toggle is enabled (quick blocking read is fine here)
        if (runBlocking { prefsRepo.current().monitorEnabled }) {
            setupNotification()
            startMonitoring()

        } else {
            stopSelf()
        }

        // https://developer.android.com/reference/android/app/Service.html#START_STICKY
        return START_STICKY
    }

    override fun onDestroy() {

        monitorJob?.cancel()
        monitorJob = null

        // Reset the preference (covers the notification Stop button and system kills;
        // the UI toggle observes the pref so it updates itself)
        runBlocking { prefsRepo.setMonitorEnabled(false) }

        clearNotification()
        serviceScope.cancel()

        super.onDestroy()
    }

    // endregion

    // region ================== MONITORING ==================
    // ====== ================== ========== ==================

    /**
     * Collect power/dock state and prefs together: any power event OR pref change
     * re-evaluates the rotation setting and refreshes the notification.
     */
    private fun startMonitoring() {

        monitorJob?.cancel()
        monitorJob = serviceScope.launch {

            combine(powerRepo.powerStatusFlow(), prefsRepo.prefs) { powerStatus, prefs ->
                Triple(prefs.monitorEnabled, powerStatus, prefs.modeFor(powerStatus))
            }
                .distinctUntilChanged()
                .collect { (monitorEnabled, powerStatus, rotationMode) ->

                    if (!monitorEnabled) {
                        // Toggled off (e.g. from the UI) — shut down
                        stopSelf()

                    } else {
                        // Apply the user's chosen rotation for this power state
                        // (no-op for NO_CHANGE or if WRITE_SETTINGS was revoked)
                        powerRepo.applyRotationMode(rotationMode)

                        // Reflect the new state in the ongoing notification
                        updateNotification(powerStatus, rotationMode)
                    }
                }
        }
    }

    // endregion

    // region ================== NOTIFICATION OPERATIONS ==================
    // ====== ================== ======================= ==================

    /**
     * Preps the elements needed for the Service notification
     * Reference: https://developer.android.com/guide/topics/ui/notifiers/notifications.html
     */
    private fun setupNotification() {

        val notificationChannelId = "$TAG.RotatorlatorNotification"
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager

        // Create the notification channel
        val notificationChannel = NotificationChannel(
            notificationChannelId,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_MIN
        )

        // Add the channel to the notification manager
        notificationManager?.createNotificationChannel(notificationChannel)

        // Create the initial notification
        val builder = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle(getString(R.string.notification_channel_name))
            .setSmallIcon(R.drawable.ic_adjust_black)
            .setContentText(getString(R.string.lbl_status_blank))
            .setOngoing(true)

            // Set the action when tapping the notification itself
            .setContentIntent(pendingIntentOpenApp())

            // Set the Cancel button action
            .addAction(
                R.drawable.ic_cancel_black,
                getString(R.string.btn_stop_monitoring),
                pendingIntentStopMonitoring()
            )

        notificationBuilder = builder

        // Build the notification
        val newNotification = builder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+ additionally requires the FGS type here (must match the manifest declaration)
            startForeground(
                notificationId,
                newNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )

        } else {
            // startForeground needs to be called within 5 seconds of startForegroundService()
            startForeground(notificationId, newNotification)
        }
    }

    /**
     * Update the monitoring notification with the latest port status + rotation mode
     */
    private fun updateNotification(powerStatus: PowerStatus, rotationMode: RotationMode) {

        val builder = notificationBuilder ?: return

        // Set the new Content Text
        val statusLabel = when (powerStatus) {
            PowerStatus.DISCONNECTED -> getString(R.string.lbl_status_unplugged)
            PowerStatus.PLUGGED_IN -> getString(R.string.lbl_status_plugged)
            PowerStatus.WIRELESSLY_CHARGING -> getString(R.string.lbl_status_wireless)
        }
        builder.setContentText("${getString(R.string.lbl_current_port_status)} $statusLabel")

        // Set the new icon
        when (rotationMode) {
            RotationMode.PORTRAIT,
            RotationMode.PORTRAIT_INVERTED ->
                builder.setSmallIcon(R.drawable.ic_stay_primary_portrait_black)

            RotationMode.LANDSCAPE,
            RotationMode.LANDSCAPE_INVERTED ->
                builder.setSmallIcon(R.drawable.ic_stay_primary_landscape_black)

            RotationMode.AUTO_ROTATE ->
                builder.setSmallIcon(R.drawable.ic_screen_rotation_black)

            RotationMode.NO_CHANGE -> {
                // Leave the current icon as-is
            }
        }

        // Build and update the resultant notification
        notificationManager?.notify(notificationId, builder.build())
    }

    /**
     * Remove the monitoring notification (if it's still there)
     */
    private fun clearNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(notificationId)
    }

    // endregion

    // region ================== PENDINGINTENTS ==================
    // ====== ================== ============== ==================

    private fun pendingIntentStopMonitoring(): PendingIntent {

        val intentStopMonitoring = Intent(this, ServicePortStatusHandler::class.java)
        intentStopMonitoring.action = ACTION_KEY_STOP_MONITORING

        return PendingIntent.getService(
            this,
            System.currentTimeMillis().toInt(),
            intentStopMonitoring,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun pendingIntentOpenApp(): PendingIntent {

        val intentOpenApp = Intent(this, MainActivity::class.java)
        intentOpenApp.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        return PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intentOpenApp,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    // endregion
}
