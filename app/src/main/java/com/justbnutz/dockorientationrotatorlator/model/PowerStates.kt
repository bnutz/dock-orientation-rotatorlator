/*
 * .model — the domain vocabulary. Pure Kotlin: no Android imports, no resources, no
 * dependencies on any other layer. Everything else (data, service, ui) depends on this;
 * it depends on nothing. Labels and icons for these values live in the ui layer, so the
 * domain never has to know how it's displayed.
 *
 * (Extracted from ReceiverPortStatus during overhaul Phase 2, 2026-07.)
 */

package com.justbnutz.dockorientationrotatorlator.model

/**
 * The rotation-lock setting the user can assign to each power state.
 *
 * NOTE: ordinals are persisted (both in the legacy SharedPreferences data this app
 * migrated from, and in DataStore) — do not reorder or remove entries.
 */
enum class RotationMode {
    NO_CHANGE,
    PORTRAIT,
    PORTRAIT_INVERTED,
    LANDSCAPE,
    LANDSCAPE_INVERTED,
    AUTO_ROTATE;

    companion object {
        /** Ordinal → mode, defaulting to NO_CHANGE for anything out of range */
        fun fromIndex(index: Int): RotationMode = entries.getOrElse(index) { NO_CHANGE }
    }

    /** The next mode in the cycle (wraps around) — used by the config panel buttons */
    fun next(): RotationMode = entries[(ordinal + 1) % entries.size]
}

/**
 * The power/dock states the app distinguishes between.
 */
enum class PowerStatus {
    DISCONNECTED,
    PLUGGED_IN,
    WIRELESSLY_CHARGING
}
