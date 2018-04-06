/*
 * Created by Brian Lau on 2018-03-23
 * Copyright (c) 2018. All rights reserved.
 *
 * Last modified: 2018-03-25
 */

package com.justbnutz.dockorientationrotatorlator;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;


/**
 * Service for hosting the ReceiverPortStatus BroadcastReceiver.
 *
 * Needs to be placed in a Service as doing IntentFilter Receivers in the Manifest is no longer
 * supported from Oreo onwards
 *
 * - 2018/03/23
 */
public class ServicePortStatusHandler extends Service {

    private static final String TAG = ActivityRotatorlator.PACKAGE_NAME + ".ServicePortStatusHandler";

    private static final String ACTION_KEY_STOP_MONITORING = TAG + ".ACTION_KEY_STOP_MONITORING";


    // Preferences
    private SharedPreferences mSharedPrefs;

    // Notification Tools
    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mNotificationBuilder;
    private final int mNotificationId;


    public ServicePortStatusHandler() {
        mNotificationId = 10;
    }


    // region ================== STATIC METHODS ==================
    // ====== ================== ============== ==================


    public static void startRotatorlatorService(Context context) {

        Intent serviceIntent = new Intent(context, ServicePortStatusHandler.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);

        } else {
            context.startService(serviceIntent);

        }
    }


    public static void stopRotatorlatorService(Context context) {

        Intent serviceIntent = new Intent(context, ServicePortStatusHandler.class);
        context.stopService(serviceIntent);
    }

    // endregion


    // region ================== SERVICE DEFAULT ACTIONS ==================
    // ====== ================== ======================= ==================


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // Not binding to anything so can return null
        return null;
    }


    @Override
    public void onCreate() {
        super.onCreate();

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Running with START_STICKY, so Intent might be null - if not, check if there is an Action Key in the Intent
        if (intent != null
                && !TextUtils.isEmpty(intent.getAction())
                && intent.getAction().equals(ACTION_KEY_STOP_MONITORING)) {

            // If there is an Action Key then the only one we need to be concerned about is the Stop Action
            stopSelf();
        }

        // If we get here then proceed as normal -  Only do actions if the Monitor is enabled
        if (mSharedPrefs.getBoolean(getString(R.string.prefkey_enable_dock_monitor), false)) {

            // Start the internal and external Broadcast listeners
            initLocalBroadcastReceivers();
            initDeviceBroadcastReceivers();

            // Start the Service Notification
            setupNotification();

        } else {
            stopSelf();

        }

        // https://developer.android.com/reference/android/app/Service.html#START_STICKY
        return START_STICKY;
    }


    @Override
    public void onDestroy() {

        // Reset the preference
        mSharedPrefs.edit().putBoolean(getString(R.string.prefkey_enable_dock_monitor), false).apply();

        // Clean up resources
        clearBroadcastReceivers(true, true);
        clearNotification();

        super.onDestroy();
    }

    // endregion


    // region ================== BROADCASTRECEIVER OPERATIONS ==================
    // ====== ================== ============================ ==================


    // Receiver for port status update Intents (System Intents)
    private ReceiverPortStatus mReceiverPortStatus;

    // Receiver for LocalBroadcast Intents
    private BroadcastReceiver mLocalReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (context != null) {

                String intentAction = intent.getAction();

                if (!TextUtils.isEmpty(intentAction)) {

                    switch (intentAction) {
                        case ReceiverPortStatus.ACTION_KEY_POWER_STATUS_UPDATED:
                            // Update the current Plugged In status
                            updateNotification();
                            break;

                        default:
                            break;
                    }
                }
            }
        }
    };


    /**
     * Register to receive incoming LocalBroadcast Intents from the app
     */
    private void initLocalBroadcastReceivers() {
        // Make sure any previously registered receivers are cleared first (just in case)
        clearBroadcastReceivers(true, false);

        // Set up the IntentFilter for listening to LocalBroadcast events
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ReceiverPortStatus.ACTION_KEY_POWER_STATUS_UPDATED);

        // Register the receiver
        LocalBroadcastManager
                .getInstance(this)
                .registerReceiver(
                        mLocalReceiver,
                        intentFilter
                );
    }


    /**
     * Register to receive incoming Broadcast Intents from the device
     */
    private void initDeviceBroadcastReceivers() {
        // Make sure any previously registered receivers are cleared first (just in case)
        clearBroadcastReceivers(false, true);

        // Set up the IntentFilter for listening to port events
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_DOCK_EVENT);
        intentFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        intentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);

        // Init the Port Status BroadcastReceiver
        mReceiverPortStatus = new ReceiverPortStatus();

        // Register the receiver
        registerReceiver(
                mReceiverPortStatus,
                intentFilter
        );
    }


    /**
     * Unregister all MessageReceivers
     */
    private void clearBroadcastReceivers(boolean clearLocal, boolean clearGlobal) {

        if (clearLocal) {
            LocalBroadcastManager
                    .getInstance(this)
                    .unregisterReceiver(mLocalReceiver);

        }

        if (clearGlobal && mReceiverPortStatus != null) {
            unregisterReceiver(
                    mReceiverPortStatus
            );
        }
    }

    // endregion


    // region ================== NOTIFICATION OPERATIONS ==================
    // ====== ================== ======================= ==================


    /**
     * Preps the elements needed for the Service notification
     * Reference: https://developer.android.com/guide/topics/ui/notifiers/notifications.html
     */
    private void setupNotification() {

        String notificationChannelId = TAG + ".RotatorlatorNotification";
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // If Oreo or higher, create the notification channel
            NotificationChannel notificationChannel = new NotificationChannel(
                    notificationChannelId,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_MIN
            );

            if (mNotificationManager != null) {
                // Add the channel to the notification manager
                mNotificationManager.createNotificationChannel(notificationChannel);
            }
        }

        // Create the initial notification
        mNotificationBuilder = new NotificationCompat.Builder(this, notificationChannelId)
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
                );

        // Build the notification
        Notification newNotification = mNotificationBuilder.build();

        // Start the Notification
        showNotification(newNotification);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // If Oreo or higher, startForeground needs to be called within 5 seconds of startForegroundService()
            startForeground(
                    mNotificationId,
                    newNotification
            );
        }

        // Update the initial values
        updateNotification();
    }


    /**
     * Update the monitoring notification with the latest port status
     */
    private void updateNotification() {

        if (mNotificationBuilder != null && mReceiverPortStatus != null) {

            // Fetch the current Power Status
            ReceiverPortStatus.PowerStatus powerStatus = mReceiverPortStatus.getCurrentPowerStatus(this);

            // Set the new Content Text
            switch (powerStatus) {
                case DISCONNECTED:
                    mNotificationBuilder.setContentText(
                            String.format(
                                    "%s %s",
                                    getString(R.string.lbl_current_port_status),
                                    getString(R.string.lbl_status_unplugged)
                            )
                    );
                    break;

                case PLUGGED_IN:
                    mNotificationBuilder.setContentText(
                            String.format(
                                    "%s %s",
                                    getString(R.string.lbl_current_port_status),
                                    getString(R.string.lbl_status_plugged)
                            )
                    );
                    break;

                case WIRELESSLY_CHARGING:
                    mNotificationBuilder.setContentText(
                            String.format(
                                    "%s %s",
                                    getString(R.string.lbl_current_port_status),
                                    getString(R.string.lbl_status_wireless)
                            )
                    );
                    break;

                default:
                    break;
            }

            // Fetch the preferred Rotation Mode for this Power Status
            ReceiverPortStatus.RotationMode rotationMode = mReceiverPortStatus.getRotationMode(this, powerStatus);

            // Set the new icon
            switch (rotationMode) {
                case PORTRAIT:
                case PORTRAIT_INVERTED:
                    mNotificationBuilder.setSmallIcon(R.drawable.ic_stay_primary_portrait_black);
                    break;

                case LANDSCAPE:
                case LANDSCAPE_INVERTED:
                    mNotificationBuilder.setSmallIcon(R.drawable.ic_stay_primary_landscape_black);
                    break;

                case AUTO_ROTATE:
                    mNotificationBuilder.setSmallIcon(R.drawable.ic_screen_rotation_black);
                    break;

                case NO_CHANGE:
                default:
                    break;
            }

            // Build and update the resultant notification
            showNotification(mNotificationBuilder.build());
        }
    }


    /**
     * Run the notification with the default properties and the given Notification object
     */
    private void showNotification(Notification builtNotification) {

        if (mNotificationManager != null) {
            mNotificationManager.notify(
                    mNotificationId,
                    builtNotification
            );
        }
    }


    /**
     * Remove the monitoring notification (if it's still there)
     */
    private void clearNotification() {

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(mNotificationId);
        }
    }

    // endregion


    // region ================== PENDINGINTENTS ==================
    // ====== ================== ============== ==================


    private PendingIntent pendingIntentStopMonitoring() {

        Intent intentStopMonitoring = new Intent(this, ServicePortStatusHandler.class);
        intentStopMonitoring.setAction(ACTION_KEY_STOP_MONITORING);

        return PendingIntent.getService(
                this,
                (int) System.currentTimeMillis(),
                intentStopMonitoring,
                0
        );
    }


    private PendingIntent pendingIntentOpenApp() {

        Intent intentOpenApp = new Intent(this, ActivitySplashScreen.class);
        intentOpenApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                intentOpenApp,
                0
        );
    }

    // endregion

}
