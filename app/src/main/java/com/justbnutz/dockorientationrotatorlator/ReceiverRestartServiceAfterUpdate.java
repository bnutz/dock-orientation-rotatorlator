/*
 * Created by Brian Lau on 2018-04-02
 * Copyright (c) 2018. All rights reserved.
 *
 * Last modified: 2018-04-02
 */

package com.justbnutz.dockorientationrotatorlator;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;


/**
 * Receiver to restart the ServicePortStatusHandler after an app update.
 *
 * References:
 * - http://www.feelouttheform.net/restart-service-after-update/
 * - https://android.jlelse.eu/engage-your-android-users-with-new-content-after-app-upgrade-b8e160c4b0b8
 */
public class ReceiverRestartServiceAfterUpdate extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && !TextUtils.isEmpty(intent.getAction())
                && intent.getAction().equals(Intent.ACTION_MY_PACKAGE_REPLACED)
                && sharedPrefs.getBoolean(context.getString(R.string.prefkey_enable_dock_monitor), false)) {

            ServicePortStatusHandler.startRotatorlatorService(context);
        }
    }
}
