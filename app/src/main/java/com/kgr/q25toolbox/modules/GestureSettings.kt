package com.kgr.q25toolbox.modules

import android.content.Context
import com.kgr.q25toolbox.service.Q25AccessibilityService

/**
 * Persisted configuration for the custom edge gestures. Stored in the shared "q25tweaks" prefs (the service
 * already listens to that file, so a change is applied live) under the "gest_" prefix.
 *
 * Two zones are configured independently: LATERAL (both side edges share one setup) and BOTTOM. Each zone is
 * Off or Custom. A "Native" mode is deliberately absent: on the Q25's LineageOS the system gesture navigation
 * cannot be switched on (see the lineage-support notes), so there is nothing native to hand over to.
 */
object GestureSettings {

    enum class Zone(val prefix: String) { LATERAL("gest_lat_"), BOTTOM("gest_bot_") }
    enum class Mode { OFF, CUSTOM }

    const val KEY_PREFIX = "gest_"
    const val KEY_SHOW_STRIPS = "gest_show_strips"
    private const val K_MODE = "mode"
    private const val K_THICKNESS = "thickness_dp"
    private const val K_LENGTH = "length_pct"
    private const val K_DISTANCE = "distance_dp"
    private const val K_HAPTIC = "haptic"

    // Ranges are in dp / percent so they scale with the display density.
    val THICKNESS_RANGE = 6f..32f
    val LENGTH_RANGE = 20f..100f
    /** Swipe distance that triggers the action: small = sensitive. */
    val DISTANCE_RANGE = 8f..80f

    /** How long a bottom swipe must be held after crossing the threshold to count as "up and hold" (Recents). */
    const val HOLD_MS = 350L

    data class Config(
        val mode: Mode,
        val thicknessDp: Int,
        val lengthPct: Int,
        val distanceDp: Int,
        val haptic: Boolean,
    )

    private fun defaults(zone: Zone) = when (zone) {
        // Side strips: thin, covering the middle 60% of the height (keeps clear of the corners and status bar).
        Zone.LATERAL -> Config(Mode.OFF, 14, 60, 28, true)
        // Bottom strip: thin so it takes little of the keyboard/app UI; 50% of the width, centred.
        Zone.BOTTOM -> Config(Mode.OFF, 12, 50, 32, true)
    }

    fun get(context: Context, zone: Zone): Config {
        val p = context.getSharedPreferences(Q25AccessibilityService.PREFS, Context.MODE_PRIVATE)
        val d = defaults(zone)
        val mode = runCatching { Mode.valueOf(p.getString(zone.prefix + K_MODE, d.mode.name)!!) }.getOrDefault(d.mode)
        return Config(
            mode = mode,
            thicknessDp = p.getInt(zone.prefix + K_THICKNESS, d.thicknessDp).coerceIn(THICKNESS_RANGE.start.toInt(), THICKNESS_RANGE.endInclusive.toInt()),
            lengthPct = p.getInt(zone.prefix + K_LENGTH, d.lengthPct).coerceIn(LENGTH_RANGE.start.toInt(), LENGTH_RANGE.endInclusive.toInt()),
            distanceDp = p.getInt(zone.prefix + K_DISTANCE, d.distanceDp).coerceIn(DISTANCE_RANGE.start.toInt(), DISTANCE_RANGE.endInclusive.toInt()),
            haptic = p.getBoolean(zone.prefix + K_HAPTIC, d.haptic),
        )
    }

    fun set(context: Context, zone: Zone, c: Config) {
        context.getSharedPreferences(Q25AccessibilityService.PREFS, Context.MODE_PRIVATE).edit()
            .putString(zone.prefix + K_MODE, c.mode.name)
            .putInt(zone.prefix + K_THICKNESS, c.thicknessDp)
            .putInt(zone.prefix + K_LENGTH, c.lengthPct)
            .putInt(zone.prefix + K_DISTANCE, c.distanceDp)
            .putBoolean(zone.prefix + K_HAPTIC, c.haptic)
            .apply()
    }

    // Vibration is shared by all zones (each zone still has its own on/off switch).
    private const val KEY_VIB_TICK = "gest_vib_tick_ms"
    private const val KEY_VIB_ACTION = "gest_vib_action_ms"
    private const val KEY_VIB_STRENGTH = "gest_vib_strength_pct"
    val VIB_TICK_RANGE = 0f..80f       // 0 = no tick at the threshold
    val VIB_ACTION_RANGE = 10f..200f
    val VIB_STRENGTH_RANGE = 10f..100f // only honoured by motors with amplitude control

    data class Vibration(val tickMs: Int, val actionMs: Int, val strengthPct: Int)

    fun vibration(context: Context): Vibration {
        val p = context.getSharedPreferences(Q25AccessibilityService.PREFS, Context.MODE_PRIVATE)
        return Vibration(p.getInt(KEY_VIB_TICK, 15), p.getInt(KEY_VIB_ACTION, 35), p.getInt(KEY_VIB_STRENGTH, 100))
    }

    fun setVibration(context: Context, v: Vibration) {
        context.getSharedPreferences(Q25AccessibilityService.PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_VIB_TICK, v.tickMs).putInt(KEY_VIB_ACTION, v.actionMs).putInt(KEY_VIB_STRENGTH, v.strengthPct).apply()
    }

    /** Every pref key this module owns (for backup/restore). */
    fun allKeys(): List<String> = Zone.entries.flatMap { z ->
        listOf(K_MODE, K_THICKNESS, K_LENGTH, K_DISTANCE, K_HAPTIC).map { z.prefix + it }
    } + KEY_SHOW_STRIPS + listOf(KEY_VIB_TICK, KEY_VIB_ACTION, KEY_VIB_STRENGTH)

    fun showStrips(context: Context): Boolean =
        context.getSharedPreferences(Q25AccessibilityService.PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHOW_STRIPS, false)

    fun setShowStrips(context: Context, on: Boolean) {
        context.getSharedPreferences(Q25AccessibilityService.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOW_STRIPS, on).apply()
    }
}
