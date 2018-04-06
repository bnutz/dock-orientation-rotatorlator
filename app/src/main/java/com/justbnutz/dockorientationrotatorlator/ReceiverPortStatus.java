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
import android.content.res.Configuration;
import android.os.BatteryManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.view.Surface;
import android.view.WindowManager;

import java.util.Timer;
import java.util.TimerTask;


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
        PORTRAIT_INVERTED,
        LANDSCAPE,
        LANDSCAPE_INVERTED,
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
                        rotationMode
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
    private void setDisplayRotationMode(@NonNull final Context context, final RotationMode rotationMode) {

        boolean rotatorlatorIsGo;

        // Double-check permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            rotatorlatorIsGo = Settings.System.canWrite(context);

        } else {
            rotatorlatorIsGo = (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SETTINGS) == PackageManager.PERMISSION_GRANTED);
        }

        if (rotatorlatorIsGo) {

            boolean isAutoRotate;

            // Permissions confirmed, set the device rotation accordingly
            switch (rotationMode) {

                case PORTRAIT:
                case PORTRAIT_INVERTED:
                case LANDSCAPE:
                case LANDSCAPE_INVERTED:
                    // Fixed Rotation set - so disable Auto-Rotate
                    isAutoRotate = false;
                    break;

                case AUTO_ROTATE:
                default:
                    // Only need to enable Auto-Rotate; User Rotation not needed
                    isAutoRotate = true;
                break;
            }

            // Apply the Auto-Rotate setting
            Settings.System.putInt(
                    context.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION,
                    isAutoRotate
                            ? 1
                            : 0
            );

            // If we're disabling Auto-Rotate, then also need to set the User Rotation value
            if (!isAutoRotate) {

                // Wait a little bit as it looks like some systems don't handle successive multiple changes in rotation too well
                Timer quickTimer = new Timer();
                TimerTask timerTask = new TimerTask() {

                    @Override
                    public void run() {
                        setUserRotatation(context, rotationMode);
                    }
                };

                // Reference: https://www.codementor.io/tips/0743378261/non-freezing-sleep-in-android-app
                quickTimer.schedule(timerTask, 150);
            }
        }
    }


    /**
     * If a fixed-orientation is chosen, then need to disable Auto-Rotate and then set the User Rotation
     * value accordingly - this will be relative to the "natural orientation" of the device, which needs
     * to be figured out separately.
     *
     * Reference: https://stackoverflow.com/a/9888357
     */
    private void setUserRotatation(@NonNull Context context, RotationMode userRotationMode) {

        // Fetch the "natural orientation" of the device (portrait vs landscape)
        int naturalOrientation = getNaturalOrientation(context);

        // Make sure we have an baseline orientation to reference against
        if (naturalOrientation != Configuration.ORIENTATION_UNDEFINED) {

            // Init the value that will be inserted
            int userRotation = -1;

            // Set the User Rotation value relative to the natural orientation of the device
            switch (userRotationMode) {
                case PORTRAIT:
                    userRotation = (naturalOrientation == Configuration.ORIENTATION_PORTRAIT)
                            ? Surface.ROTATION_0
                            : Surface.ROTATION_90;
                    break;

                case PORTRAIT_INVERTED:
                    userRotation = (naturalOrientation == Configuration.ORIENTATION_PORTRAIT)
                            ? Surface.ROTATION_180
                            : Surface.ROTATION_270;
                    break;

                case LANDSCAPE:
                    userRotation = (naturalOrientation == Configuration.ORIENTATION_LANDSCAPE)
                            ? Surface.ROTATION_0
                            : Surface.ROTATION_90;
                    break;

                case LANDSCAPE_INVERTED:
                    userRotation = (naturalOrientation == Configuration.ORIENTATION_LANDSCAPE)
                            ? Surface.ROTATION_180
                            : Surface.ROTATION_270;
                    break;

                default:
                    break;
            }

            // Make sure we have a proper value to set and apply it
            if (userRotation >= 0) {
                Settings.System.putInt(
                        context.getContentResolver(),
                        Settings.System.USER_ROTATION,
                        userRotation
                );
            }
        }
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
    int getNaturalOrientation(@NonNull Context context) {

        // Retrieve the system objects for determining device layout measurements
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Configuration configuration = context.getResources().getConfiguration();

        if (windowManager != null && configuration != null) {

            // Get the current rotation and orientation values
            int currentRotation = windowManager.getDefaultDisplay().getRotation();
            int currentOrientation = configuration.orientation;

            // Natural orientation can be determined by comparing the two values against each other
            if (
                    (((currentRotation == Surface.ROTATION_0) || (currentRotation == Surface.ROTATION_180))
                    && currentOrientation == Configuration.ORIENTATION_PORTRAIT)
                    ||
                    (((currentRotation == Surface.ROTATION_90) || (currentRotation == Surface.ROTATION_270))
                    && currentOrientation == Configuration.ORIENTATION_LANDSCAPE)
                    ) {

                return Configuration.ORIENTATION_PORTRAIT;

            } else {
                return Configuration.ORIENTATION_LANDSCAPE;

            }

        } else {
            // If the reference objects could not be retrieved, return an undefined result
            return Configuration.ORIENTATION_UNDEFINED;
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
