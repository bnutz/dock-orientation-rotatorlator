/*
 * Overhaul Phase 2, 2026-07: ViewModel between the repositories and the UI.
 * Phase 3: consumed by MainActivity's Compose tree (RotatorlatorScreens.kt).
 */

package com.justbnutz.dockorientationrotatorlator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Snapshot of the system rotation-lock settings, re-read on every settings change
 * (the UI maps it to a label relative to the device's natural orientation).
 */
data class RotationLockUi(
    val isAutoRotate: Boolean,
    val userRotation: Int,
    val naturalOrientation: Int
)

class RotatorlatorViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PrefsRepository(application)
    private val powerRepo = PowerStateRepository(application)

    /** Live pref snapshots (kept warm as a StateFlow) */
    val prefs: StateFlow<RotatorlatorPrefs?> =
        prefsRepo.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Current + subsequent power/dock states (receiver registered only while collected) */
    val powerStatus: Flow<PowerStatus> = powerRepo.powerStatusFlow()

    /** Re-reads the rotation-lock settings whenever they change (seeds on collect) */
    val rotationLock: Flow<RotationLockUi> = powerRepo.rotationSettingFlow().map {
        RotationLockUi(
            isAutoRotate = powerRepo.isAutoRotate(),
            userRotation = powerRepo.getUserRotation(),
            naturalOrientation = powerRepo.getNaturalOrientation()
        )
    }

    /**
     * Master toggle: persist the choice and start/stop the monitoring Service.
     * (The Service applies the rotation setting itself as soon as it starts collecting.)
     */
    fun setMonitorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setMonitorEnabled(enabled)

            if (enabled) {
                ServicePortStatusHandler.startRotatorlatorService(getApplication())
            } else {
                ServicePortStatusHandler.stopRotatorlatorService(getApplication())
            }
        }
    }

    /**
     * If the device was previously rebooted then the Service will be stopped — restart it
     * when the UI opens and the monitor pref says it should be running. (If the Service is
     * already running this just re-triggers onStartCommand on the existing instance.)
     */
    fun ensureServiceRunningIfEnabled() {
        viewModelScope.launch {
            if (prefsRepo.current().monitorEnabled) {
                ServicePortStatusHandler.startRotatorlatorService(getApplication())
            }
        }
    }

    /** Advance the given power state's RotationMode to the next one in the cycle */
    fun cycleRotationMode(powerStatus: PowerStatus) {
        viewModelScope.launch {
            val current = prefsRepo.current().modeFor(powerStatus)
            prefsRepo.setRotationMode(powerStatus, current.next())
        }
    }

    /** Show/hide the Wireless Charging config panel */
    fun toggleWirelessPanel() {
        viewModelScope.launch {
            val show = !prefsRepo.current().showWirelessPanel
            prefsRepo.setShowWirelessPanel(show)
        }
    }
}
