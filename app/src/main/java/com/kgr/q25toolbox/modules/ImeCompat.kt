package com.kgr.q25toolbox.modules

import android.content.Context
import android.util.Log
import com.kgr.q25toolbox.core.RootShell
import com.kgr.q25toolbox.service.Q25AccessibilityService

/**
 * Keeps the BlackBerry keyboard (com.blackberry.keyboard) on its suggestions strip on the Q25's LineageOS (LineageOS
 * only; no Xposed needed).
 *
 * Cause, verified on the device and in AOSP (android16-release): the keyboard shows its full on-screen keyboard
 * when its configuration says LANDSCAPE (its code: `Build.DEVICE != "venice" && orientation == LANDSCAPE`). On the
 * Q25's square 720x720 screen, `ConfigurationContainer.applySizeOverrideIfNeeded` gives a process that targets an
 * old SDK (the keyboard: targetSdk 27) the legacy configuration with the status bar (34 px) taken off the height:
 * 597 x 569 dp, which is landscape, although the display itself is 597 x 597 dp (portrait). Apps that target
 * SDK 35+ ("insets decoupled configuration" enforced) never get that. The framework offers a per-app switch for it,
 * the overridable compat change [CHANGE]; turning it on for the keyboard gives it the display's own configuration
 * (portrait) for good. Measured: with it on, a display-size round trip (which broke the strip every time without
 * it) leaves the keyboard's process and window at `h597dp port`.
 *
 * `am compat enable` restarts the app's process, so [apply] checks the current state first and only acts on a change.
 */
object ImeCompat {

    const val KEY_ENABLED = "ime_compat_enabled"
    const val PKG = "com.blackberry.keyboard"
    const val CHANGE = "OVERRIDE_ENABLE_INSETS_DECOUPLED_CONFIGURATION"

    fun isEnabled(context: Context) =
        context.getSharedPreferences(Q25AccessibilityService.PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(Q25AccessibilityService.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    /**
     * Whether [dump] (the `dumpsys platform_compat` line of [CHANGE]) says the override is on for [pkg], e.g.
     * `ChangeId(327313645; name=...; disabled; packageOverrides={com.blackberry.keyboard=true}; ...; overridable)`.
     * Pure, for tests.
     */
    internal fun parseActive(dump: String, pkg: String = PKG): Boolean {
        for (line in dump.lineSequence()) {
            if (!line.contains(CHANGE)) continue
            val overrides = Regex("""packageOverrides=\{([^}]*)\}""").find(line)?.groupValues?.get(1) ?: continue
            if (overrides.split(',').any { it.trim() == "$pkg=true" }) return true
        }
        return false
    }

    /** Whether the override is on right now (root; blocking: off the main thread). */
    fun isActive(): Boolean = parseActive(RootShell.run("dumpsys platform_compat 2>/dev/null | grep $CHANGE").outString)

    /** What to run to bring the system in line with the switch, or null if it already is. Pure, for tests. */
    internal fun commandFor(wanted: Boolean, active: Boolean): String? = when {
        wanted && !active -> "am compat enable $CHANGE $PKG"
        !wanted && active -> "am compat reset $CHANGE $PKG"
        else -> null
    }

    /** Brings the override in line with the saved switch; acts only when they differ (it restarts the keyboard). */
    fun apply(context: Context) {
        val cmd = commandFor(isEnabled(context), isActive()) ?: return
        val res = RootShell.run(cmd)
        Log.i("Q25Toolbox", "ImeCompat: $cmd -> ${res.outString.trim()}")
    }
}
