/*
 * Created by Brian Lau on 2018-03-28
 * Copyright (c) 2018. All rights reserved.
 *
 * Last modified: 2018-03-28
 */

package com.justbnutz.dockorientationrotatorlator;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Animatable;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextSwitcher;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;


/**
 * Simple RecyclerView Adapter to hold the Rotatorlator Config toggle options.
 * Doing this so we can selectively enable/disable the control panels with a nice animation
 */
public class AdapterRotatorlatorConfigs extends RecyclerView.Adapter<AdapterRotatorlatorConfigs.ViewHolderRotatorlatorConfig> {

    private static final String PAYLOAD_KEY_UPDATE_LABELS = "PAYLOAD_KEY_UPDATE_LABELS";

    private final Context mContext;
    private final SharedPreferences mSharedPrefs;

    private List<String> mPowerStatePrefKey;

    AdapterRotatorlatorConfigs(@NonNull Context context, @NonNull SharedPreferences sharedPreferences) {
        mContext = context;
        mSharedPrefs = sharedPreferences;
        mPowerStatePrefKey = new ArrayList<>();
    }


    /**
     * Add a Toggle Panel to the list, must be one of the known Power State types that is recognised
     * by ReceiverPortStatus
     */
    void insertTogglePanel(String powerStatePrefKey) {

        if (!TextUtils.isEmpty(powerStatePrefKey)
                && !mPowerStatePrefKey.contains(powerStatePrefKey)) {

            mPowerStatePrefKey.add(powerStatePrefKey);
            notifyItemInserted(getItemCount() - 1);
        }
    }


    /**
     * Remove a Toggle Panel from the list (if it exists)
     */
    void removeTogglePanel(String powerStatePrefKey) {

        if (!TextUtils.isEmpty(powerStatePrefKey)
                && mPowerStatePrefKey.contains(powerStatePrefKey)) {

            notifyItemRemoved(
                    mPowerStatePrefKey.indexOf(powerStatePrefKey)
            );
            mPowerStatePrefKey.remove(powerStatePrefKey);
        }
    }


    /**
     * Update the Rotation Mode text and ImageButton icon for the given Power State panel
     */
    void updateTogglePanel(String powerStatePrefKey) {

        int changeIndex = mPowerStatePrefKey.indexOf(powerStatePrefKey);

        if (changeIndex > -1) {
            notifyItemChanged(changeIndex, PAYLOAD_KEY_UPDATE_LABELS);
        }
    }


    @NonNull
    @Override
    public ViewHolderRotatorlatorConfig onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolderRotatorlatorConfig(
                LayoutInflater.from(mContext).inflate(R.layout.itemrow_rotatorlator_option, parent, false)
        );
    }


    @Override
    public void onBindViewHolder(@NonNull ViewHolderRotatorlatorConfig viewHolder, int position, @NonNull List<Object> payloads) {

        if (payloads.isEmpty()) {
            // Payloads should be normally empty, so just pass straight to the regular onBindViewHolder
            onBindViewHolder(viewHolder, position);

        } else {
            // If there is something in the payload, then we're doing a partial reload
            for (Object currentLoad : payloads) {

                // Loop through each payload object til we come to one we recognise
                if (currentLoad instanceof String) {

                    switch (String.valueOf(currentLoad)) {
                        case PAYLOAD_KEY_UPDATE_LABELS:
                            // Update the Rotation Mode labels only
                            setPowerStateRotationModeLabels(viewHolder);
                            break;

                        default:
                            break;
                    }
                }
            }
        }
    }


    @Override
    public void onBindViewHolder(@NonNull ViewHolderRotatorlatorConfig viewHolder, int position) {

        // Set the base properties of the Toggle Panel (these won't change)
        viewHolder.prefKey = mPowerStatePrefKey.get(position);
        viewHolder.rotationModeIndex = mSharedPrefs.getInt(viewHolder.prefKey, 0);

        // Update the Views depending on which Power State toggle we're setting up
        if (viewHolder.prefKey
                .equals(mContext.getString(R.string.prefkey_set_autorotate_unplugged))) {

            viewHolder.imgPowerStateIcon.setImageResource(R.drawable.ic_dock_black);
            viewHolder.lblPowerStateLabelHeader.setText(mContext.getString(R.string.lbl_setting_unplugged));

            setPowerStateRotationModeLabels(viewHolder);

        } else if (viewHolder.prefKey
                .equals(mContext.getString(R.string.prefkey_set_autorotate_plugged))) {

            viewHolder.imgPowerStateIcon.setImageResource(R.drawable.ic_power_black);
            viewHolder.lblPowerStateLabelHeader.setText(mContext.getString(R.string.lbl_setting_plugged));

            setPowerStateRotationModeLabels(viewHolder);

        } else if (viewHolder.prefKey
                .equals(mContext.getString(R.string.prefkey_set_autorotate_wireless))) {

            viewHolder.imgPowerStateIcon.setImageResource(R.drawable.ic_tap_and_play_black);
            viewHolder.lblPowerStateLabelHeader.setText(mContext.getString(R.string.lbl_setting_wireless));

            setPowerStateRotationModeLabels(viewHolder);
        }
    }


    private void setPowerStateRotationModeLabels(ViewHolderRotatorlatorConfig viewHolder) {

        // Fetch the stored RotationMode for the current Power State (index stored in the ViewHolder)
        ReceiverPortStatus.RotationMode rotationMode = getRotationMode(
                viewHolder.rotationModeIndex
        );

        // Update the TextSwitcher and ImageButton accordingly
        switch (rotationMode) {

            case PORTRAIT:
                updateRotationModeLabels(
                        viewHolder,
                        mContext.getString(R.string.lbl_status_portrait),
                        R.drawable.avd_no_change_to_portrait
                );
                break;

            case PORTRAIT_INVERTED:
                updateRotationModeLabels(
                        viewHolder,
                        mContext.getString(R.string.lbl_status_portrait_inverted),
                        R.drawable.avd_portrait_to_portrait_inverted
                );
                break;

            case LANDSCAPE:
                updateRotationModeLabels(
                        viewHolder,
                        mContext.getString(R.string.lbl_status_landscape),
                        R.drawable.avd_portrait_to_landscape
                );
                break;

            case LANDSCAPE_INVERTED:
                updateRotationModeLabels(
                        viewHolder,
                        mContext.getString(R.string.lbl_status_landscape_inverted),
                        R.drawable.avd_landscape_to_landscape_inverted
                );
                break;

            case AUTO_ROTATE:
                updateRotationModeLabels(
                        viewHolder,
                        mContext.getString(R.string.lbl_status_auto_rotate),
                        R.drawable.avd_landscape_to_rotate
                );
                break;

            case NO_CHANGE:
            default:
                updateRotationModeLabels(
                        viewHolder,
                        mContext.getString(R.string.lbl_status_no_change),
                        R.drawable.avd_rotate_to_no_change
                );
                break;
        }
    }


    /**
     * Check if the given TextView and ImageButton labels are being updated with new values, if so
     * then set the new values in place and start the animation.
     */
    private void updateRotationModeLabels(ViewHolderRotatorlatorConfig viewHolder,
                                          String powerStateRotationLabel,
                                          int animatedRotationModeResId) {

        // Check whether we actually need to update this View set
        if (!((TextView) viewHolder.txtswchPowerStateLabelSwitcher.getCurrentView()).getText().equals(powerStateRotationLabel)) {

            // Update the Rotation Mode text label
            viewHolder.txtswchPowerStateLabelSwitcher.setText(powerStateRotationLabel);

            // Update the AnimatedVectorDrawable in the ImageButton
            viewHolder.btnPowerStateOrientationToggle.setImageResource(animatedRotationModeResId);

            // Start the AVD animation to show the mode transition
            ((Animatable) viewHolder.btnPowerStateOrientationToggle.getDrawable()).start();
        }
    }


    /**
     * Fetch the corresponding RotationMode to the given index, defaults to NO_CHANGE if the index is invalid
     */
    private ReceiverPortStatus.RotationMode getRotationMode(int modeIndex) {

        ReceiverPortStatus.RotationMode rotationMode = ReceiverPortStatus.RotationMode.NO_CHANGE;

        if (modeIndex >= 0
                && modeIndex < ReceiverPortStatus.RotationMode.values().length) {

            rotationMode = ReceiverPortStatus.RotationMode.values()[modeIndex];
        }

        return rotationMode;
    }


    @Override
    public int getItemCount() {
        return mPowerStatePrefKey.size();
    }


    class ViewHolderRotatorlatorConfig extends RecyclerView.ViewHolder implements View.OnClickListener {

        final ImageView imgPowerStateIcon;
        final TextView lblPowerStateLabelHeader;
        final TextSwitcher txtswchPowerStateLabelSwitcher;
        final ImageButton btnPowerStateOrientationToggle;

        String prefKey;
        int rotationModeIndex;


        ViewHolderRotatorlatorConfig(View itemView) {
            super(itemView);

            imgPowerStateIcon = itemView.findViewById(R.id.img_power_state_icon);
            lblPowerStateLabelHeader = itemView.findViewById(R.id.lbl_power_state_label_header);
            txtswchPowerStateLabelSwitcher = itemView.findViewById(R.id.txtswch_power_state_label_switcher);
            btnPowerStateOrientationToggle = itemView.findViewById(R.id.btn_power_state_orientation_toggle);

            btnPowerStateOrientationToggle.setOnClickListener(this);
        }


        @Override
        public void onClick(View clickedView) {
            switch (clickedView.getId()) {
                case R.id.btn_power_state_orientation_toggle:
                    setNextRotationMode();
                    break;
                default:
                    break;
            }
        }


        /**
         * Set the stored orientation of the current Power State to the next Rotation Mode in the list
         * and save the change.
         */
        private void setNextRotationMode() {
            // Increment the current Rotation index
            rotationModeIndex++;

            // Make sure it stays within range
            if (rotationModeIndex >= ReceiverPortStatus.RotationMode.values().length) {
                rotationModeIndex = 0;
            }

            // Save the updated option
            mSharedPrefs.edit()
                    .putInt(prefKey, rotationModeIndex)
                    .apply();

            // (Callbacks will be handled in the OnSharedPreferenceChangeListener in the Configurator Fragment)
        }
    }
}
