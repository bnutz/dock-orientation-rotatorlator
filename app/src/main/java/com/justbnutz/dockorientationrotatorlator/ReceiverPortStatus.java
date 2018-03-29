/*
 * Created by Brian Lau on 2018-03-20
 * Copyright (c) 2018. All rights reserved.
 *
 * Last modified: 2018-03-28
 */

package com.justbnutz.dockorientationrotatorlator;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;


/**
 * Receiver for handling port status updates (Power & Dock Connection Events)
 *
 * This will be invoked from either:
 * - The Intent-Filter Receiver spec in the manifest (for API < 23)
 * - Or from ServicePortStatusHandler (for Oreo and above)
 *
 * To test Dock Mode, run:
 * ./adb shell am broadcast -a android.intent.action.DOCK_EVENT --ei android.intent.extra.DOCK_STATE 0
 *
 * - 2018/03/20.
 */
public class ReceiverPortStatus extends BroadcastReceiver {

    private static final String TAG = ActivityRotatorlator.PACKAGE_NAME + ".ReceiverPortStatus";

    static final String ACTION_KEY_POWER_STATUS_UPDATED = TAG + ".ACTION_KEY_POWER_STATUS_UPDATED";
    static final String INTENT_EXTRA_POWER_STATUS = TAG + ".INTENT_EXTRA_POWER_STATUS";

    // Consider changing these to @IntDef in future: https://android.jlelse.eu/android-performance-avoid-using-enum-on-android-326be0794dc3
    enum RotationMode {
        NO_CHANGE,
        PORTRAIT,
        AUTO_ROTATE
    }

    enum PowerStatus {
        DISCONNECTED,
        PLUGGED_IN,
        WIRELESSLY_CHARGING
    }


    @Override
    public void onReceive(Context context, Intent intent) {

        if (context != null && !TextUtils.isEmpty(intent.getAction())) {
            checkSetDeviceRotation(context);
        }
    }


    /**
     * Run the sequence of checking the current power status and setting the device rotation setting
     * accordingly.
     */
    void checkSetDeviceRotation(@NonNull Context context) {

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        // Only do actions if the monitor is enabled and we have an Intent Action to work with
        if (sharedPrefs.getBoolean(context.getString(R.string.prefkey_enable_dock_monitor), false)) {

            // Fetch the new Power Status
            PowerStatus currentPowerStatus = getCurrentPowerStatus(context);

            // Get the Rotation Mode for this Power State
            RotationMode rotationMode = getRotationMode(context, currentPowerStatus);

            // Only need to take action if we have a valid Rotation to set
            if (rotationMode != RotationMode.NO_CHANGE) {

                // Set the derived Rotation Mode
                setDisplayRotationMode(
                        context,
                        rotationMode == RotationMode.AUTO_ROTATE
                );
            }

            // Send the new Power Status out via LocalBroadcast as well
            Intent intentPortStatusUpdated = new Intent(ACTION_KEY_POWER_STATUS_UPDATED);
            intentPortStatusUpdated.putExtra(
                    INTENT_EXTRA_POWER_STATUS,
                    currentPowerStatus
            );

            LocalBroadcastManager.getInstance(context).sendBroadcast(
                    intentPortStatusUpdated
            );
        }
    }


    // region ================== ROTATION MODE OPS ==================
    // ====== ================== ================= ==================


    /**
     * Take a given PowerStatus and return what type of RotationMode has been set for that state
     */
    RotationMode getRotationMode(@NonNull Context context, PowerStatus powerStatus) {

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        int indexRotationMode = 0;

        switch (powerStatus) {
            case DISCONNECTED:
                indexRotationMode = sharedPrefs.getInt(context.getString(R.string.prefkey_set_autorotate_unplugged), 0);
                break;

            case PLUGGED_IN:
                indexRotationMode = sharedPrefs.getInt(context.getString(R.string.prefkey_set_autorotate_plugged), 0);
                break;

            case WIRELESSLY_CHARGING:
                indexRotationMode = sharedPrefs.getInt(context.getString(R.string.prefkey_set_autorotate_wireless), 0);
                break;

            default:
                break;
        }

        // Make sure the index is valid
        if (indexRotationMode >= 0 && indexRotationMode < RotationMode.values().length) {
            return RotationMode.values()[indexRotationMode];

        } else {
            return RotationMode.NO_CHANGE;

        }
    }


    /**
     * Verifies we have permission to alter the system settings and if so, set the device rotation
     * setting accordingly.
     */
    private void setDisplayRotationMode(@NonNull Context context, boolean setAutoRotate) {

        boolean rotatorlatorIsGo;

        // Double-check permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            rotatorlatorIsGo = Settings.System.canWrite(context);

        } else {
            rotatorlatorIsGo = (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SETTINGS) == PackageManager.PERMISSION_GRANTED);
        }

        if (rotatorlatorIsGo) {
            // Permissions confirmed, set the device rotation accordingly
            Settings.System.putInt(
                    context.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION,
                    (setAutoRotate ? 1 : 0)
            );
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
    PowerStatus getCurrentPowerStatus(@NonNull Context context) {

        if (isWirelesslyCharging(context)) {
            return PowerStatus.WIRELESSLY_CHARGING;

        } else if (isPluggedIn(context) || isDocked(context)) {
            return PowerStatus.PLUGGED_IN;

        } else {
            return PowerStatus.DISCONNECTED;
        }

    }


    /**
     * Retrieve the current Dock state via sticky broadcasts and return whether we are currently docked
     * or not.
     */
    private boolean isDocked(@NonNull Context context) {

        Intent dockIntent = getStateIntent(context, false);

        if (dockIntent != null) {
            // Read the EXTRA_DOCK_STATE value out of the dock status
            int dockState = dockIntent.getIntExtra(Intent.EXTRA_DOCK_STATE, -1);

            return (dockState != Intent.EXTRA_DOCK_STATE_UNDOCKED);
        }

        return false;
    }


    /**
     * Retrieve the current state of the USB port via sticky broadcasts and return whether we are actually
     * plugged in or not.
     */
    private boolean isPluggedIn(@NonNull Context context) {

        Intent batteryIntent = getStateIntent(context, true);

        if (batteryIntent != null) {
            // Read the EXTRA_PLUGGED value out of the battery status and return the overall plugged-in status
            int chargePlugState = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

            return (chargePlugState == BatteryManager.BATTERY_PLUGGED_AC)
                    || (chargePlugState == BatteryManager.BATTERY_PLUGGED_USB);
        }

        return false;
    }


    /**
     * Check if the device is currently wirelessly charging. Same logic as isCurrentlyPluggedIn()
     * Only applicable for Jelly Bean and above API 17
     */
    @SuppressLint("InlinedApi")
    private boolean isWirelesslyCharging(@NonNull Context context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {

            Intent batteryIntent = getStateIntent(context, true);

            if (batteryIntent != null) {
                // Read the EXTRA_PLUGGED value out of the battery status and return the overall plugged-in status
                int chargePlugState = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

                return (chargePlugState == BatteryManager.BATTERY_PLUGGED_WIRELESS);
            }
        }

        return false;
    }


    /**
     * Retrieve the "sticky" Intent of either Battery State or Dock State, used for checking current
     * power status of the device.
     *
     * References:
     * - https://developer.android.com/training/monitoring-device-state/docking-monitoring.html
     * - https://developer.android.com/training/monitoring-device-state/battery-monitoring.html
     */
    private Intent getStateIntent(@NonNull Context context, boolean checkingBatteryState) {

        IntentFilter intentFilter = new IntentFilter();

        // Set the filter according to which state we're checking
        if (checkingBatteryState) {
            intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);

        } else {
            intentFilter.addAction(Intent.ACTION_DOCK_EVENT);
        }

        // "Sticky" broadcast Intents can be retrieved immediately by passing a null BroadcastReceiver
        return context.registerReceiver(null, intentFilter);
    }

    // endregion
}
