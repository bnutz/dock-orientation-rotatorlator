package com.justbnutz.dockorientationrotatorlator;
/*
 * Created by Brian Lau on 2018-03-20
 * Copyright (c) 2018. All rights reserved.
 *
 * Last modified: 2018-03-23
 */

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;

/**
 * Activity for showing a splash screen while we're waiting for the app to load.
 *
 * Reference: https://android.jlelse.eu/right-way-to-create-splash-screen-on-android-e7f1709ba154
 * - 2018/03/20
 */
public class ActivitySplashScreen extends AppCompatActivity {


    private static final int PERMISSION_REQUEST_WRITE_SETTINGS = 10;

    private AlertDialog mAlertDialogPermissionExplainer;


    // region ================== PRIMARY FLOW ==================
    // ====== ================== ============ ==================

    @Override
    protected void onResume() {
        super.onResume();

        // Run the permission check, placing in onResume so that it can rerun when coming back from Permission Settings menu
        startPermissionCheckFlow();

    }


    /**
     * Start the actual Main Activity
     */
    private void startMainActivity() {

        // Start Main Activity
        startActivity(
                new Intent(
                        ActivitySplashScreen.this,
                        ActivityRotatorlator.class
                )
        );

        // Close Splash Screen Activity
        finish();
    }

    // endregion


    // region ================== PERMISSION OPERATORS ==================
    // ====== ================== ==================== ==================


    private void startPermissionCheckFlow() {

        // Check permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Marshmallow and higher needs a slightly different approach for WRITE_SETTINGS

            if (Settings.System.canWrite(this)) {
                // If we already have permissions, just launch the main Fragment
                startMainActivity();

            } else {
                // If we don't have permissions, pop the Explainer with the option to launch the mod System Settings Android menu
                popPermissionRequestRationale(R.string.btn_permissions_menu);
            }

        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            // For devices below Marshmallow, check permissions normally

            if (checkRationale()) {
                // User previously denied the permissions, need to pop the Explainer with the Try Again option
                popPermissionRequestRationale(R.string.btn_try_again);

            } else {
                // If we don't yet have permissions, request it
                requestReadPermission();

            }

        } else {
            // Good permissions, launch the main app.
            startMainActivity();

        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0) {

            switch (grantResults[0]) {
                case PackageManager.PERMISSION_GRANTED:
                    // Permission granted, start the main app!
                    startMainActivity();
                    break;

                case PackageManager.PERMISSION_DENIED:
                    // Permission denied, check if user has clicked the "Don't show again" checkbox via the Rationale flag
                    if (!checkRationale()) {
                        popPermissionRequestRationale(R.string.btn_settings);

                    } else {
                        popPermissionRequestRationale(R.string.btn_try_again);

                    }
                    break;

                default:
                    finish();
                    break;
            }
        }
    }


    /**
     * Request the Write Settings permission
     */
    private void requestReadPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{ Manifest.permission.WRITE_SETTINGS },
                PERMISSION_REQUEST_WRITE_SETTINGS
        );
    }


    /**
     * Check if we need to show the rationale popup, if the user has previously denied and this still
     * comes back as false then it means they clicked the "Don't show again" checkbox
     */
    private boolean checkRationale() {
        return ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_SETTINGS);
    }


    /**
     * Pop a dialogue explaining that we need Write Settings permissions
     */
    private void popPermissionRequestRationale(int permissionTypeResourceId) {

        // If a previous dialog was showing, close it
        if (mAlertDialogPermissionExplainer != null && mAlertDialogPermissionExplainer.isShowing()) {
            mAlertDialogPermissionExplainer.dismiss();
        }

        // Using the Builder class for convenient dialog construction
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        // Set the properties
        dialogBuilder
                .setTitle(getString(R.string.permission_explain_title))
                .setMessage(R.string.permission_explain_message)
                .setCancelable(false);

        // Show on of the three buttons depending on how far along the Permission flow we're at
        switch (permissionTypeResourceId) {
            case R.string.btn_permissions_menu:
                dialogBuilder.setPositiveButton(
                        R.string.btn_permissions_menu,
                        mRationaleDialogListeners
                );
                break;

            case R.string.btn_settings:
                dialogBuilder.setNegativeButton(
                        R.string.btn_settings,
                        mRationaleDialogListeners
                );
                break;

            case R.string.btn_try_again:
                dialogBuilder.setNeutralButton(
                        R.string.btn_try_again,
                        mRationaleDialogListeners
                );
                break;

            default:
                break;
        }

        // Link the AlertDialog and pop it
        mAlertDialogPermissionExplainer = dialogBuilder.create();
        mAlertDialogPermissionExplainer.show();
    }


    /**
     * Handle rationale dialogue clicks
     */
    private DialogInterface.OnClickListener mRationaleDialogListeners = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    // Open the Modify System Settings permissions menu for API >= 23 devices
                    openPermissionsMenu();
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    // User has selected "Never show again" on permission box, so need to direct them to App Settings
                    openSettingsMenu();
                    break;

                case DialogInterface.BUTTON_NEUTRAL:
                    // User hasn't selected "Never show again" on permission box, so can just pop the request again (Try Again option)
                    requestReadPermission();
                    break;

                default:
                    break;
            }
        }
    };


    /**
     * Open the main Settings app
     */
    private void openSettingsMenu() {

        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)
        );

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }


    /**
     * Open the "Modify System Settings" menu for this app, note this is different from the regular
     * app permission menu
     *
     * (Settings > Apps & Notifications > Special App Access > Modify System Settings)
     */
    private void openPermissionsMenu() {

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {

            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)
            );

            startActivity(intent);
        }
    }

    // endregion

}
