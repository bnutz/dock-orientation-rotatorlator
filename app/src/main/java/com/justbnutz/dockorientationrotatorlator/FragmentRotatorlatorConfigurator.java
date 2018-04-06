/*
 * Created by Brian Lau on 2018-03-20
 * Copyright (c) 2018. All rights reserved.
 *
 * Last modified: 2018-03-28
 */

package com.justbnutz.dockorientationrotatorlator;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextSwitcher;
import android.widget.TextView;


/**
 * Fragment for configuring the different dock/orientation profiles
 * - 2018/03/20
 */
public class FragmentRotatorlatorConfigurator extends Fragment implements View.OnClickListener, SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = ActivityRotatorlator.PACKAGE_NAME + ".FragmentRotatorlatorConfigurator";


    // Preferences
    private SharedPreferences mSharedPrefs;

    // This won't actually be used as a BroadcastReceiever, but it holds the methods for checking current Port Statuses
    private ReceiverPortStatus mReceiverPortStatus;

    // ContentObserver for getting notified when Rotation Settings are notified
    private ObserverRotationSetting mRotationSettingsObserver;

    // Adapter for the RecyclerView Config Panel
    private AdapterRotatorlatorConfigs mAdapterRotatorlatorConfigs;

    // Views
    private Switch mToggleDockMonitor;

    private TextSwitcher mTxtSwchCurrentPowerStatus;
    private TextSwitcher mTxtSwchCurrentOrientationStatus;


    // Empty constructor for the FragmentManager to instantiate the fragment. Required by system.
    // (Used for situations like screen orientation changes).
    public FragmentRotatorlatorConfigurator() {}


    // region ================== STATIC METHODS ==================
    // ====== ================== ============== ==================


    /**
     * Use this factory method to create a new instance of this fragment using any provided parameters.
     * - newInstance vs Constructors: http://stackoverflow.com/a/14655001
     */
    public static FragmentRotatorlatorConfigurator newInstance() {
        return new FragmentRotatorlatorConfigurator();
    }

    // endregion


    // region ================== PRIMARY FLOW ==================
    // ====== ================== ============ ==================


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getContext() != null) {
            // Init the toolboxes
            mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            mReceiverPortStatus = new ReceiverPortStatus();
            mRotationSettingsObserver = new ObserverRotationSetting(new Handler());
            mAdapterRotatorlatorConfigs = new AdapterRotatorlatorConfigs(getContext(), mSharedPrefs);

            // If the device was previously rebooted then the Service will be stopped, check if we need to start it back up again
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    && mSharedPrefs.getBoolean(getContext().getString(R.string.prefkey_enable_dock_monitor), false)) {

                // If the Service is already running then this will just do onStartCommand() again on the existing Service
                ServicePortStatusHandler.startRotatorlatorService(getContext());
            }
        }
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_starter_motor, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Link the main toggle
        mToggleDockMonitor = view.findViewById(R.id.switch_toggle_port_monitor);

        // Link the status labels
        mTxtSwchCurrentPowerStatus = view.findViewById(R.id.txtswch_current_power_status);
        mTxtSwchCurrentOrientationStatus = view.findViewById(R.id.txtswch_current_orientation_status);

        // Link the RecyclerView
        RecyclerView recyclerConfigs = view.findViewById(R.id.recycler_rotatorlator_configs);
        recyclerConfigs.setAdapter(mAdapterRotatorlatorConfigs);

        // Link the Minimise button
        FloatingActionButton btnMinimise = view.findViewById(R.id.btn_minimise);

        // Click listeners
        mToggleDockMonitor.setOnClickListener(this);
        btnMinimise.setOnClickListener(this);

        // Wait for Views to be fully drawn before calling to update the labels
        btnMinimise.post(
                new Runnable() {
                    @Override
                    public void run() {
                        if (getContext() != null) {
                            // Update the current Status labels
                            updateCurrentStatusLabels();

                            // Insert the Config panels for each of the enabled Power States
                            mAdapterRotatorlatorConfigs.insertTogglePanel(
                                    getString(R.string.prefkey_set_autorotate_unplugged)
                            );

                            mAdapterRotatorlatorConfigs.insertTogglePanel(
                                    getString(R.string.prefkey_set_autorotate_plugged)
                            );

                            // The Wireless Charging toggle panel is optional, since not all devices have Wireless Charging
                            if (mSharedPrefs.getBoolean(getString(R.string.prefkey_show_wireless_options), true)) {
                                mAdapterRotatorlatorConfigs.insertTogglePanel(
                                        getString(R.string.prefkey_set_autorotate_wireless)
                                );
                            }
                        }
                    }
                }
        );
    }


    @Override
    public void onStart() {
        super.onStart();

        if (getContext() != null
                && mSharedPrefs != null
                && mRotationSettingsObserver != null) {

            initBroadcastReceivers();
            mRotationSettingsObserver.startObserver(getContext());
            mSharedPrefs.registerOnSharedPreferenceChangeListener(this);
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        // Sometimes the toggle doesn't refresh properly if Service was disabled from notification - double-check here
        updateCurrentStatusLabels();
    }


    @Override
    public void onStop() {
        super.onStop();

        if (getContext() != null
                && mSharedPrefs != null
                && mRotationSettingsObserver != null) {

            mSharedPrefs.unregisterOnSharedPreferenceChangeListener(this);
            mRotationSettingsObserver.stopObserver();
            clearBroadcastReceivers();
        }
    }


    @Override
    public void onClick(View view) {

        switch (view.getId()) {
            case R.id.switch_toggle_port_monitor:
                // Update the preference
                mSharedPrefs.edit()
                        .putBoolean(
                                getString(R.string.prefkey_enable_dock_monitor),
                                mToggleDockMonitor.isChecked()
                        ).apply();

                // If on Oreo or higher, need to place the Receiver in a Service for it to register
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

                    if (mToggleDockMonitor.isChecked()) {
                        ServicePortStatusHandler.startRotatorlatorService(getContext());

                    } else {
                        ServicePortStatusHandler.stopRotatorlatorService(getContext());

                    }
                }

                // If enabling, see if we need to set the Rotation Mode already
                if (getContext() != null && mToggleDockMonitor.isChecked()) {
                    mReceiverPortStatus.checkSetDeviceRotation(getContext());
                }
                break;

            case R.id.btn_minimise:
                // "Exit" the app
                if (getActivity() != null) {
                    getActivity().finish();
                }
                break;

            default:
                break;
        }
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String prefKey) {

        // Always update the status labels whenever any Preference is changed
        updateCurrentStatusLabels();

        // Check which Preference was updated and update the UI accordingly
        if (prefKey.equals(getString(R.string.prefkey_set_autorotate_unplugged))
                || prefKey.equals(getString(R.string.prefkey_set_autorotate_plugged))
                || prefKey.equals(getString(R.string.prefkey_set_autorotate_wireless))) {

            // If one of the Rotation Mode ImageButtons were clicked, then need to update the labels
            mAdapterRotatorlatorConfigs.updateTogglePanel(prefKey);

            // Also need to check if we need to set update the device rotate-lock setting as well
            if (getContext() != null) {
                mReceiverPortStatus.checkSetDeviceRotation(getContext());
            }

        } else if (prefKey.equals(getString(R.string.prefkey_show_wireless_options))) {

            // If the "Show Wireless Option" preference was changed, add/remove its Toggle Panel from the list
            if (sharedPreferences.getBoolean(getString(R.string.prefkey_show_wireless_options), true)) {
                mAdapterRotatorlatorConfigs.insertTogglePanel(
                        getString(R.string.prefkey_set_autorotate_wireless)
                );
            } else {
                mAdapterRotatorlatorConfigs.removeTogglePanel(
                        getString(R.string.prefkey_set_autorotate_wireless)
                );
            }
        }
    }

    // endregion


    // region ================== BROADCASTRECEIVER OPERATIONS ==================
    // ====== ================== ============================ ==================


    /**
     * Handler for receiving incoming Local Intents.
     */
    private final BroadcastReceiver mLocalReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            String actionKey = intent.getAction();

            if (!TextUtils.isEmpty(actionKey)) {

                switch (actionKey) {
                    case ReceiverPortStatus.ACTION_KEY_POWER_STATUS_UPDATED:
                    case ObserverRotationSetting.ACTION_KEY_ROTATION_SETTING_UPDATED:
                        updateCurrentStatusLabels();
                        break;

                    default:
                        break;
                }
            }
        }
    };


    /**
     * Register to receive incoming Intents with the given Filters.
     */
    private void initBroadcastReceivers() {
        if (getContext() != null) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ObserverRotationSetting.ACTION_KEY_ROTATION_SETTING_UPDATED);
            intentFilter.addAction(ReceiverPortStatus.ACTION_KEY_POWER_STATUS_UPDATED);

            LocalBroadcastManager.getInstance(getContext()).registerReceiver(
                    mLocalReceiver,
                    intentFilter
            );
        }
    }


    /**
     * Unregister the MessageReceiver when leaving the Activity
     */
    private void clearBroadcastReceivers() {
        if (getContext() != null) {
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(
                    mLocalReceiver
            );
        }
    }

    // endregion


    // region ================== CURRENT STATUS LABELS ==================
    // ====== ================== ===================== ==================


    /**
     * Update the Current Port Status label
     */
    private void updateCurrentStatusLabels() {

        boolean monitoringPowerStatus = mSharedPrefs.getBoolean(
                getString(R.string.prefkey_enable_dock_monitor), false
        );

        // Update the Monitor toggle
        mToggleDockMonitor.setChecked(monitoringPowerStatus);

        // Init the text label placeholders
        String currentPowerText = getString(R.string.lbl_status_blank);
        String currentOrientationText = getString(R.string.lbl_status_blank);

        Drawable currentPowerIcon = null;
        Drawable currentOrientationIcon = null;

        if (getContext() != null
                && monitoringPowerStatus
                && mReceiverPortStatus != null) {

            // Fetch the current Power Status
            ReceiverPortStatus.PowerStatus powerStatus = mReceiverPortStatus.getCurrentPowerStatus(getContext());

            // Set the Power Status text
            switch (powerStatus) {
                case DISCONNECTED:
                    currentPowerText = getString(R.string.lbl_status_unplugged);
                    currentPowerIcon = getResources().getDrawable(R.drawable.ic_dock_grey_400);
                    break;

                case PLUGGED_IN:
                    currentPowerText = getString(R.string.lbl_status_plugged);
                    currentPowerIcon = getResources().getDrawable(R.drawable.ic_power_grey_400);
                    break;

                case WIRELESSLY_CHARGING:
                    currentPowerText = getString(R.string.lbl_status_wireless);
                    currentPowerIcon = getResources().getDrawable(R.drawable.ic_tap_and_play_grey_400);
                    break;

                default:
                    break;
            }

            // Set the current Rotation-Lock setting text
            if (isAutoRotate()) {
                currentOrientationText = getString(R.string.lbl_status_auto_rotate);
                currentOrientationIcon = getResources().getDrawable(R.drawable.ic_screen_rotation_grey_400);

            } else {
                // If Auto-Rotate is disabled, then will need to work out the current User Rotation setting relative to the natural orientation
                if (mReceiverPortStatus != null) {
                    int naturalOrientation = mReceiverPortStatus.getNaturalOrientation(getContext());

                    // Only set the label if we have a baseline orientation to check against
                    if (naturalOrientation != Configuration.ORIENTATION_UNDEFINED) {

                        switch (getUserRotation()) {
                            case Surface.ROTATION_0:
                                if (naturalOrientation == Configuration.ORIENTATION_PORTRAIT) {
                                    currentOrientationText = getString(R.string.lbl_status_portrait);
                                    currentOrientationIcon = getResources().getDrawable(R.drawable.ic_stay_primary_portrait_grey_400);
                                } else {
                                    currentOrientationText = getString(R.string.lbl_status_landscape);
                                    currentOrientationIcon = getResources().getDrawable(R.drawable.ic_stay_primary_landscape_grey_400);
                                }
                                break;

                            case Surface.ROTATION_90:
                                if (naturalOrientation == Configuration.ORIENTATION_PORTRAIT) {
                                    currentOrientationText = getString(R.string.lbl_status_landscape);
                                    currentOrientationIcon = getResources().getDrawable(R.drawable.ic_stay_primary_landscape_grey_400);
                                } else {
                                    currentOrientationText = getString(R.string.lbl_status_portrait);
                                    currentOrientationIcon = getResources().getDrawable(R.drawable.ic_stay_primary_portrait_grey_400);
                                }
                                break;

                            case Surface.ROTATION_180:
                                if (naturalOrientation == Configuration.ORIENTATION_PORTRAIT) {
                                    currentOrientationText = getString(R.string.lbl_status_portrait_inverted);
                                    currentOrientationIcon = getResources().getDrawable(R.drawable.ic_stay_primary_portrait_grey_400);
                                } else {
                                    currentOrientationText = getString(R.string.lbl_status_landscape_inverted);
                                    currentOrientationIcon = getResources().getDrawable(R.drawable.ic_stay_primary_landscape_grey_400);
                                }
                                break;

                            case Surface.ROTATION_270:
                                if (naturalOrientation == Configuration.ORIENTATION_PORTRAIT) {
                                    currentOrientationText = getString(R.string.lbl_status_landscape_inverted);
                                    currentOrientationIcon = getResources().getDrawable(R.drawable.ic_stay_primary_landscape_grey_400);
                                } else {
                                    currentOrientationText = getString(R.string.lbl_status_portrait_inverted);
                                    currentOrientationIcon = getResources().getDrawable(R.drawable.ic_stay_primary_portrait_grey_400);
                                }
                                break;

                            default:
                                break;
                        }
                    }
                }
            }
        }

        // Only update the labels if they're actually different from the current text
        if (!((TextView) mTxtSwchCurrentPowerStatus.getCurrentView()).getText().equals(currentPowerText)) {

            // Prep the Power State icon for when the label updates
            ((TextView) mTxtSwchCurrentPowerStatus.getNextView())
                    .setCompoundDrawablesWithIntrinsicBounds(null, null, currentPowerIcon, null);

            // Set the Power State text
            mTxtSwchCurrentPowerStatus.setText(currentPowerText);
        }

        if (!((TextView) mTxtSwchCurrentOrientationStatus.getCurrentView()).getText().equals(currentOrientationText)) {

            // Prep the Orientation State icon for when the label updates
            ((TextView) mTxtSwchCurrentOrientationStatus.getNextView())
                    .setCompoundDrawablesWithIntrinsicBounds(null, null, currentOrientationIcon, null);

            // Set the Orientation State text
            mTxtSwchCurrentOrientationStatus.setText(currentOrientationText);
        }
    }


    /**
     * Return the current Rotation-Lock setting of the device
     */
    private boolean isAutoRotate() {

        int rotationSetting = 0;

        if (getContext() != null) {

            // Read the current rotation setting value from Settings
            rotationSetting = Settings.System.getInt(
                    getContext().getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION,
                    0
            );
            // 0: Portrait | 1: Auto-Rotate
        }

        return (rotationSetting == 1);
    }


    /**
     * Return the current User Rotation setting of the device
     */
    private int getUserRotation() {

        int userRotation = -1;

        if (getContext() != null) {

            // Read the user rotation setting value from Settings
            userRotation = Settings.System.getInt(
                    getContext().getContentResolver(),
                    Settings.System.USER_ROTATION,
                    -1
            );
            // Corresponds to Surface.Rotation contants
        }

        return userRotation;
    }

    // endregion
}
