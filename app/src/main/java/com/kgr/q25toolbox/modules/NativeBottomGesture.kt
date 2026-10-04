package com.kgr.q25toolbox.modules

import com.kgr.q25toolbox.core.RootShell
import com.kgr.q25toolbox.modules.RecentsTweaksController.HookHealth
import com.kgr.q25toolbox.xposed.GestureHookInit

/**
 * App side of [GestureHookInit]: tells the launcher hook to skip the native bottom swipe-up while the custom
 * bottom strip is on, and reads back whether the hook has really run in the launcher.
 */
object NativeBottomGesture {

    /** Pure rule: the native bottom gesture is switched off exactly when the custom bottom strip is on. */
    internal fun shouldDisableNative(bottom: GestureSettings.Mode) = bottom == GestureSettings.Mode.CUSTOM

    /** Writes the Settings.Global flag the hook reads (root; blocking: call off the main thread). */
    fun sync(bottom: GestureSettings.Mode) {
        val v = if (shouldDisableNative(bottom)) 1 else 0
        RootShell.run("settings put global ${GestureHookInit.PREF_OFF} $v")
    }

    /** OK if the hook has run in the installed launcher build; UNKNOWN if not seen yet. Blocking (root). */
    fun hookHealth(): HookHealth {
        for (pkg in RecentsTweaksController.HOOK_PACKAGES) {
            val state = RootShell.run(
                RecentsTweaksController.inGlobalNs("cat /data/user/0/$pkg/${GestureHookInit.STATE_FILE} 2>/dev/null")
            ).outString
            if (state.isBlank()) continue
            return RecentsTweaksController.parseHandshake(state, RecentsTweaksController.installedVersionCode(pkg))
        }
        return HookHealth.UNKNOWN
    }
}
