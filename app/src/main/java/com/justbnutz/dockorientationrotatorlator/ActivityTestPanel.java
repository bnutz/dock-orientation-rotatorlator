/*
 * Created by Brian Lau on 2018-03-31
 * Copyright (c) 2018. All rights reserved.
 *
 * Last modified: 2018-03-31
 */

package com.justbnutz.dockorientationrotatorlator;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity for testing the different dock/orientation profiles
 */
public class ActivityTestPanel extends AppCompatActivity {

    public static final String TAG = ActivityRotatorlator.PACKAGE_NAME + ".ActivityTestPanel";

    private static final String BUNDLEKEY_EVENT_LOG = TAG + ".BUNDLEKEY_EVENT_LOG";

    // ContentObserver for the device Rotation setting
    private ObserverRotationSetting mRotationSettingsObserver;

    // Views
    TextView mTxtOsBuildVersion;
    TextView mTxtCanWriteSystemSettings;
    TextView mTxtRotationAccelerometer;
    TextView mTxtRotationUser;
    TextView mTxtStateDock;
    TextView mTxtStateBattery;
    TextView mTxtEventLog;

    FloatingActionButton mBtnCopyLogs;

    // BATTERY_CHANGED events can easily flood the log, but might still need to check for them in future.
    MenuItem mMnuShowBatteryChanged;

    // Fixed text labels
    final String mInitSetting = "---";
    final String mNoSetting = "Unchecked Setting: %s";


    // region ================== PRIMARY FLOW ==================
    // ====== ================== ============ ==================


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Init the ContentObserver
        mRotationSettingsObserver = new ObserverRotationSetting(new Handler());

        // Init the layout
        setContentView(R.layout.activity_test_panel);

        // Link the TextViews
        mTxtOsBuildVersion = findViewById(R.id.txt_os_build_version);
        mTxtCanWriteSystemSettings = findViewById(R.id.txt_can_write_system_settings);
        mTxtRotationAccelerometer = findViewById(R.id.txt_rotation_accelerometer);
        mTxtRotationUser = findViewById(R.id.txt_rotation_user);
        mTxtStateDock = findViewById(R.id.txt_dock_state);
        mTxtStateBattery = findViewById(R.id.txt_battery_state);
        mTxtEventLog = findViewById(R.id.txt_event_log);

        mBtnCopyLogs = findViewById(R.id.btn_copy_logs);
        mBtnCopyLogs.setOnClickListener(mFabClickListener);

        // Disable word wrap and make the text selectable and scrollable in the Event Log
        mTxtEventLog.setMovementMethod(new ScrollingMovementMethod());

        mTxtEventLog.setHorizontallyScrolling(true);
        mTxtEventLog.setHorizontalScrollBarEnabled(true);
        mTxtEventLog.setVerticalScrollBarEnabled(true);

        mTxtEventLog.setTextIsSelectable(true);

        // Restore the Event contents if we rotated the screen
        if (savedInstanceState != null) {
            mTxtEventLog.setText(
                    savedInstanceState.getString(BUNDLEKEY_EVENT_LOG, "")
            );
        }
    }


    @Override
    protected void onStart() {
        super.onStart();

        mRotationSettingsObserver.startObserver(this);
        initBroadcastReceivers();
    }


    @Override
    protected void onResume() {
        super.onResume();

        // Update the state labels
        setStateLabels();
    }


    @Override
    protected void onStop() {
        clearBroadcastReceivers();
        mRotationSettingsObserver.stopObserver();

        super.onStop();
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(
                BUNDLEKEY_EVENT_LOG,
                mTxtEventLog.getText().toString()
        );

        super.onSaveInstanceState(outState);
    }


    @Override
    public void onBackPressed() {
        // Close Test Panel
        finish();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_test_panel, menu);

        // Unchecked by default (in XML)
        mMnuShowBatteryChanged = menu.findItem(R.id.mnu_show_battery_changed);

        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.mnu_show_battery_changed:
                // Invert the checkbox
                item.setChecked(!item.isChecked());
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }


    // endregion


    // region ================== BROADCASTRECEIVER OPERATIONS ==================
    // ====== ================== ============================ ==================


    /**
     * Handler for receiving incoming Local and System Intents.
     *
     * Should be able to use the same BroadcastReceiver for both since we're doing the same action
     * for all incoming Intents; Just opening them up and seeing whats inside.
     */
    private final BroadcastReceiver mIntentCheckerReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            String actionKey = intent.getAction();

            // Since we're just analysing Intents, can just do the same action for all Intents
            if (!TextUtils.isEmpty(actionKey)) {

                // Update labels
                setStateLabels();

                // Log the Intent
                logEvent(actionKey, intent.getExtras());
            }
        }
    };


    /**
     * Register to receive incoming Intents with the given Filters.
     */
    private void initBroadcastReceivers() {

        // Register the listener for LocalBroadcast Intents (for Rotation-Lock updates)
        IntentFilter localFilter = new IntentFilter();
        localFilter.addAction(ObserverRotationSetting.ACTION_KEY_ROTATION_SETTING_UPDATED);

        LocalBroadcastManager.getInstance(this).registerReceiver(
                mIntentCheckerReceiver,
                localFilter
        );

        // Register the listener for device-wide Intents (for Power Status updates)
        IntentFilter systemFilter = new IntentFilter();
        systemFilter.addAction(Intent.ACTION_DOCK_EVENT);
        systemFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        systemFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        systemFilter.addAction(Intent.ACTION_BATTERY_CHANGED);

        registerReceiver(
                mIntentCheckerReceiver,
                systemFilter
        );
    }


    /**
     * Unregister the MessageReceivers when leaving the Activity
     */
    private void clearBroadcastReceivers() {

        LocalBroadcastManager.getInstance(this).unregisterReceiver(
                mIntentCheckerReceiver
        );

        unregisterReceiver(
                mIntentCheckerReceiver
        );
    }

    // endregion


    // region ================== STATE LABELS ==================
    // ====== ================== ============ ==================


    /**
     * Update the header label fields in the Test Panel
     */
    private void setStateLabels() {
        showOsBuildVersion();
        showSystemSettingsPermission();
        showAccelerometerRotation();
        showUserRotation();
        showDockState();
        showBatteryState();
    }


    /**
     * Show the API version number of the device
     */
    private void showOsBuildVersion() {
        mTxtOsBuildVersion.setText(
                String.valueOf(Build.VERSION.SDK_INT)
        );
    }


    /**
     * Show whether we have permission to write to System Settings
     */
    private void showSystemSettingsPermission() {

        Boolean canWrite;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            canWrite = Settings.System.canWrite(this);

        } else {
            canWrite = (
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_SETTINGS)
                            == PackageManager.PERMISSION_GRANTED
            );
        }

        mTxtCanWriteSystemSettings.setText(
                String.valueOf(canWrite)
        );
    }


    /**
     * Show the current Rotation-Lock status (0: Lock Rotation, 1: Auto-Rotate)
     *
     * Reference: https://stackoverflow.com/a/4909079
     */
    private void showAccelerometerRotation() {

        int accelerometerRotation = Settings.System.getInt(
                getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION,
                -1
        );

        String label;

        switch (accelerometerRotation) {
            case 0:
                label = "0 (User Rotation)";
                break;

            case 1:
                label = "1 (Auto Rotation)";
                break;

            default:
                label = String.format(mNoSetting, String.valueOf(accelerometerRotation));
                break;
        }

        mTxtRotationAccelerometer.setText(label);
    }


    /**
     * Show the current User-Rotation setting of the device.
     * This is the rotation that will be used when Accelerometer Rotation is disabled (0).
     */
    private void showUserRotation() {

        int userRotation = Settings.System.getInt(
                getContentResolver(),
                Settings.System.USER_ROTATION,
                -1
        );

        String label;

        switch (userRotation) {
            case Surface.ROTATION_0:
                label = "0 degrees";
                break;

            case Surface.ROTATION_90:
                label = "90 degrees";
                break;

            case Surface.ROTATION_180:
                label = "180 degrees";
                break;

            case Surface.ROTATION_270:
                label = "270 degrees";
                break;

            default:
                label = String.format(mNoSetting, String.valueOf(userRotation));
                break;
        }

        mTxtRotationUser.setText(label);
    }


    /**
     * Show the current Dock State of the device.
     *
     * Reference: https://developer.android.com/training/monitoring-device-state/docking-monitoring.html
     */
    private void showDockState() {

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_DOCK_EVENT);
        Intent dockStatus = registerReceiver(null, intentFilter);

        String label = mInitSetting;

        if (dockStatus != null) {
            int dockState = dockStatus.getIntExtra(Intent.EXTRA_DOCK_STATE, -1);

            switch (dockState) {
                case Intent.EXTRA_DOCK_STATE_UNDOCKED:
                    label = "0 (Undocked)";
                    break;

                case Intent.EXTRA_DOCK_STATE_DESK:
                    label = "1 (Desk)";
                    break;

                case Intent.EXTRA_DOCK_STATE_CAR:
                    label = "2 (Car)";
                    break;

                case Intent.EXTRA_DOCK_STATE_LE_DESK:
                    label = "3 (Analogue Desk)";
                    break;

                case Intent.EXTRA_DOCK_STATE_HE_DESK:
                    label = "4 (Digital Desk)";
                    break;

                default:
                    label = String.format(mNoSetting, String.valueOf(dockState));
                    break;
            }
        }

        mTxtStateDock.setText(label);
    }


    /**
     * Show the current Battery State of the device.
     *
     * Reference: https://developer.android.com/training/monitoring-device-state/battery-monitoring.html
     */
    private void showBatteryState() {

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, intentFilter);

        String label = mInitSetting;

        if (batteryStatus != null) {
            int plugState = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

            switch (plugState) {
                case BatteryManager.BATTERY_PLUGGED_AC:
                    label = "1 (AC)";
                    break;

                case BatteryManager.BATTERY_PLUGGED_USB:
                    label = "2 (USB)";
                    break;

                case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                    label = "4 (Wireless)";
                    break;

                default:
                    label = String.format(mNoSetting, String.valueOf(plugState));
                    break;
            }
        }

        mTxtStateBattery.setText(label);
    }

    // endregion


    // region ================== EVENT LOG OPS ==================
    // ====== ================== ============= ==================


    /**
     * Add an entry to the Event Log textbox, showing the ActionKey of a given
     * Intent and the keys of any Extras it might be carrying.
     */
    private void logEvent(String actionKey, @Nullable Bundle intentExtras) {

        if (!actionKey.equals(Intent.ACTION_BATTERY_CHANGED) || (mMnuShowBatteryChanged != null && mMnuShowBatteryChanged.isChecked())) {

            // If it exists, trim off the package name prefix from the front of the Tag
            if (actionKey.startsWith(ActivityRotatorlator.PACKAGE_NAME)) {
                actionKey = actionKey.substring(
                        ActivityRotatorlator.PACKAGE_NAME.length()
                );
            }

            // Enter the Intent ActionKey to the box
            mTxtEventLog.append(
                    String.format("%s:\n", actionKey)
            );

            // If present, enter the list of Extra Keys underneath
            if (intentExtras != null) {
                for (String eachKey : intentExtras.keySet()) {
                    mTxtEventLog.append(
                            String.format("- %s\n", eachKey)
                    );
                }
            }
        }
    }


    /**
     * Listener to copy out the current contents of the Test Panel to the Clipboard
     *
     * Reference: https://developer.android.com/guide/topics/text/copy-paste.html
     */
    private View.OnClickListener mFabClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {

            // Get a handle to the Clipboard Service.
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

            if (clipboardManager != null) {

                // Build the diagnostic text to stick in the clipboard
                ClipData clip = ClipData.newPlainText("Log Text", buildLogOutput());

                // Stick the clip on the board
                clipboardManager.setPrimaryClip(clip);

                // Notify the user
                Toast.makeText(ActivityTestPanel.this, R.string.toast_log_copied, Toast.LENGTH_SHORT).show();
            }

        }
    };


    /**
     * Collect all the outputs from all the TextViews in the Activity into a nicely formatted plain-text output
     */
    private String buildLogOutput() {

        return buildLogLine(R.string.lbl_os_build_version, mTxtOsBuildVersion.getText()) +
                buildLogLine(R.string.lbl_can_write_system_settings, mTxtCanWriteSystemSettings.getText()) +
                buildLogLine(R.string.lbl_rotation_accelerometer, mTxtRotationAccelerometer.getText()) +
                buildLogLine(R.string.lbl_rotation_user, mTxtRotationUser.getText()) +
                buildLogLine(R.string.lbl_dock_state, mTxtStateDock.getText()) +
                buildLogLine(R.string.lbl_battery_state, mTxtStateBattery.getText()) +
                "\n" +
                buildLogLine(R.string.lbl_log_hint, mTxtEventLog.getText());
    }


    /**
     * Quick method to more easily build a line in the clipboard output text
     */
    private String buildLogLine(int lblResId, CharSequence lineContents) {

        return getString(lblResId)
                + " "
                + lineContents
                + "\n";
    }

    // endregion
}
