package com.kgr.q25toolbox.modules

import android.content.Context
import com.kgr.q25toolbox.core.RootShell
import com.kgr.q25toolbox.modules.RecentsTweaksController.HookHealth
import com.kgr.q25toolbox.service.Q25AccessibilityService
import com.kgr.q25toolbox.xposed.KeyboardCompatHookInit

/**
 * App side of [KeyboardCompatHookInit] (LineageOS only): the switch, the Settings.Global flag the hook reads in the
 * keyboard's process, a proof-of-life check, and a keyboard restart (the hook applies once per keyboard process).
 */
object ImeCompat {

    const val KEY_ENABLED = "ime_compat_enabled"

    fun isEnabled(context: Context) =
        context.getSharedPreferences(Q25AccessibilityService.PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(Q25AccessibilityService.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    /** Writes the Settings.Global flag the hook reads from the saved switch (root; blocking: off the main thread). */
    fun sync(context: Context) {
        RootShell.run("settings put global ${KeyboardCompatHookInit.PREF_ON} ${if (isEnabled(context)) 1 else 0}")
    }

    /** Where the hook leaves its proof of life: inside the keyboard's own `files/` dir. Pure, for tests. */
    internal fun statePath() = "/data/user/0/${KeyboardCompatHookInit.PKG}/files/${KeyboardCompatHookInit.STATE_FILE}"

    /**
     * OK if the hook has applied in the installed keyboard build (the file records its versionCode); UNKNOWN if it
     * has not run yet (switch off, module not enabled for the keyboard, or keyboard not restarted). Blocking (root).
     */
    fun health(): HookHealth {
        val state = RootShell.run(RecentsTweaksController.inGlobalNs("cat ${statePath()} 2>/dev/null")).outString
        if (state.isBlank()) return HookHealth.UNKNOWN
        return RecentsTweaksController.parseHandshake(state, RecentsTweaksController.installedVersionCode(KeyboardCompatHookInit.PKG))
    }

    /** Restarts the keyboard's process so the hook (or its removal) takes effect; the system rebinds it on demand. */
    fun restartKeyboard() {
        RootShell.run("kill \$(pidof ${KeyboardCompatHookInit.PKG}) 2>/dev/null")
    }
}
