/*
 * Created by Brian Lau on 2018-04-02
 * Copyright (c) 2018. All rights reserved.
 *
 * Overhaul Phase 2, 2026-07: reads the monitor pref from DataStore.
 */

package com.justbnutz.dockorientationrotatorlator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking

/**
 * Receiver to restart the ServicePortStatusHandler after an app update.
 *
 * References:
 * - http://www.feelouttheform.net/restart-service-after-update/
 * - https://android.jlelse.eu/engage-your-android-users-with-new-content-after-app-upgrade-b8e160c4b0b8
 */
class ReceiverRestartServiceAfterUpdate : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
            && runBlocking { PrefsRepository(context).current().monitorEnabled }
        ) {
            ServicePortStatusHandler.startRotatorlatorService(context)
        }
    }
}
