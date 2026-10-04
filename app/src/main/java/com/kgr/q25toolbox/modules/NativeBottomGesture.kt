package com.kgr.q25toolbox.modules

import com.kgr.q25toolbox.core.RootShell
import com.kgr.q25toolbox.modules.RecentsTweaksController.HookHealth
import com.kgr.q25toolbox.xposed.GestureHookInit

/**
 * App side of [GestureHookInit]: tells the launcher hook to skip the native bottom swipe-up while the custom
 * bottom strip is on, and reads back whether the hook has really run in the launcher.
 */
object NativeBottomGesture {

    /** Writes the Settings.Global flag the hook reads from the saved setting (root; blocking: off the main thread). */
    fun sync(context: android.content.Context) {
        val v = if (GestureSettings.nativeBottomOff(context)) 1 else 0
        RootShell.run("settings put global ${GestureHookInit.PREF_OFF} $v")
    }

    /** Where the hook leaves its proof of life: inside the launcher's own `files/` dir. Pure, for tests. */
    internal fun statePath(pkg: String) = "/data/user/0/$pkg/files/${GestureHookInit.STATE_FILE}"

    /** OK if the hook has run in the installed launcher build; UNKNOWN if not seen yet. Blocking (root). */
    fun hookHealth(): HookHealth {
        for (pkg in RecentsTweaksController.HOOK_PACKAGES) {
            val state = RootShell.run(
                RecentsTweaksController.inGlobalNs("cat ${statePath(pkg)} 2>/dev/null")
            ).outString
            if (state.isBlank()) { android.util.Log.i("Q25Toolbox", "gestureHook[$pkg]: no state file at ${statePath(pkg)}"); continue }
            val installed = RecentsTweaksController.installedVersionCode(pkg)
            val h = RecentsTweaksController.parseHandshake(state, installed)
            android.util.Log.i("Q25Toolbox", "gestureHook[$pkg]: state=${state.replace("\n", "|")} installed=$installed -> $h")
            return h
        }
        return HookHealth.UNKNOWN
    }
}
