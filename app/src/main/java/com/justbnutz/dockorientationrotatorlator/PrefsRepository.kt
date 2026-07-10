/*
 * Overhaul Phase 2, 2026-07: DataStore replaces the framework SharedPreferences.
 */

package com.justbnutz.dockorientationrotatorlator

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * One immutable snapshot of everything the app persists.
 */
data class RotatorlatorPrefs(
    val monitorEnabled: Boolean,
    val modeUnplugged: RotationMode,
    val modePlugged: RotationMode,
    val modeWireless: RotationMode,
    val showWirelessPanel: Boolean
) {
    /** The user's chosen RotationMode for the given power state */
    fun modeFor(powerStatus: PowerStatus): RotationMode = when (powerStatus) {
        PowerStatus.DISCONNECTED -> modeUnplugged
        PowerStatus.PLUGGED_IN -> modePlugged
        PowerStatus.WIRELESSLY_CHARGING -> modeWireless
    }
}

// Single process-wide DataStore. The SharedPreferencesMigration imports the legacy
// "<packageName>_preferences" file on first run, preserving pre-overhaul user configs
// (key names below deliberately match the old strings_prefkeys.xml values).
private val Context.dataStore by preferencesDataStore(
    name = "rotatorlator_prefs",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "${context.packageName}_preferences"))
    }
)

/**
 * The app's single source of truth for persisted settings.
 */
class PrefsRepository(private val context: Context) {

    companion object {
        // Legacy key names — must stay stable for the SharedPreferences migration
        private val KEY_MONITOR_ENABLED = booleanPreferencesKey("prefkey_enable_dock_monitor")
        private val KEY_MODE_UNPLUGGED = intPreferencesKey("prefkey_set_autorotate_unplugged")
        private val KEY_MODE_PLUGGED = intPreferencesKey("prefkey_set_autorotate_plugged")
        private val KEY_MODE_WIRELESS = intPreferencesKey("prefkey_set_autorotate_wireless")
        private val KEY_SHOW_WIRELESS = booleanPreferencesKey("prefkey_show_wireless_options")
    }

    /** Live stream of pref snapshots — emits on every change */
    val prefs: Flow<RotatorlatorPrefs> = context.dataStore.data.map { p ->
        RotatorlatorPrefs(
            monitorEnabled = p[KEY_MONITOR_ENABLED] ?: false,
            modeUnplugged = RotationMode.fromIndex(p[KEY_MODE_UNPLUGGED] ?: 0),
            modePlugged = RotationMode.fromIndex(p[KEY_MODE_PLUGGED] ?: 0),
            modeWireless = RotationMode.fromIndex(p[KEY_MODE_WIRELESS] ?: 0),
            showWirelessPanel = p[KEY_SHOW_WIRELESS] ?: true
        )
    }

    /** One-shot read of the current snapshot */
    suspend fun current(): RotatorlatorPrefs = prefs.first()

    suspend fun setMonitorEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MONITOR_ENABLED] = enabled }
    }

    suspend fun setRotationMode(powerStatus: PowerStatus, mode: RotationMode) {
        val key = when (powerStatus) {
            PowerStatus.DISCONNECTED -> KEY_MODE_UNPLUGGED
            PowerStatus.PLUGGED_IN -> KEY_MODE_PLUGGED
            PowerStatus.WIRELESSLY_CHARGING -> KEY_MODE_WIRELESS
        }
        context.dataStore.edit { it[key] = mode.ordinal }
    }

    suspend fun setShowWirelessPanel(show: Boolean) {
        context.dataStore.edit {
            it[KEY_SHOW_WIRELESS] = show

            // Original behaviour kept: when the panel is being shown, its mode resets
            // to NO_CHANGE so a stale hidden setting can't surprise the user
            if (show) {
                it[KEY_MODE_WIRELESS] = RotationMode.NO_CHANGE.ordinal
            }
        }
    }
}
