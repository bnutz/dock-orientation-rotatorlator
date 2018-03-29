/*
 * Created by Brian Lau on 2018-03-22
 * Copyright (c) 2018. All rights reserved.
 *
 * Last modified: 2018-03-25
 */

package com.justbnutz.dockorientationrotatorlator;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;


/**
 * Quick ContentObserver to send out a LocalBroadcast when Rotation Settings has been updated
 *
 * Reference: https://www.adityathakker.com/android-content-observer-react-on-content-change/
 * - 2018/03/22
 */
public class ObserverRotationSetting extends ContentObserver {

    private static final String TAG = ActivityRotatorlator.PACKAGE_NAME + ".ObserverRotationSetting";

    static final String ACTION_KEY_ROTATION_SETTING_UPDATED = TAG + ".ACTION_KEY_ROTATION_SETTING_UPDATED";

    private Context mContext;


    ObserverRotationSetting(Handler handler) {
        super(handler);
    }


    /**
     * Start listening for Settings updates under the given Context
     */
    void startObserver(@NonNull Context context) {
        // Keep a reference to the given Context
        mContext = context;

        // Register the Observer against the Rotation Settings Uri
        context.getContentResolver().registerContentObserver(
                Uri.withAppendedPath(Settings.System.CONTENT_URI, Settings.System.ACCELEROMETER_ROTATION),
                true,
                this
        );
    }


    /**
     * Stop observing Settings updates.
     */
    void stopObserver() {
        if (mContext != null) {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }


    @Override
    public void onChange(boolean selfChange) {
        this.onChange(selfChange, null);
    }


    @Override
    public void onChange(boolean selfChange, Uri uri) {

        if (mContext != null) {
            // Send out a LocalBroadcast that the Display Rotation Setting has been updated
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(
                    new Intent(ACTION_KEY_ROTATION_SETTING_UPDATED)
            );
        }
    }
}
