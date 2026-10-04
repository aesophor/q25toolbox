package com.kgr.q25toolbox.xposed

import android.content.Context
import android.provider.Settings
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed hook that switches off the launcher's native bottom swipe-up gesture while the app's custom bottom
 * strip is on, so the two do not both fire. Kept apart from [RecentsHookInit] so the Recents hooks stay untouched.
 *
 * `TouchInteractionService.onInputEvent` is the single entry point of the launcher's "[Gesture Monitor]
 * swipe-up" spy window (home / recents / quick switch). The monitor is a spy: it sees the same touches as our
 * strip, so a window of ours cannot stop it, only skipping its handler can. Same technique as Key2 Toolbox's
 * NavBarHookInit (verified there on Android 15; the class and method also exist in this Android 16 Launcher3).
 *
 * Gated live on the world-readable Settings.Global key [PREF_OFF] (written with root by the app): 0 = stock.
 * On its first call the hook leaves a proof-of-life file in the launcher's data dir, which the app reads to tell
 * the user whether the hook is really running ("enabled in LSPosed" does not prove it).
 *
 * Debug on device:  `adb logcat | grep Q25Toolbox-Xposed`
 */
class GestureHookInit : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "Q25Toolbox-Xposed"
        const val PREF_OFF = "q25_native_bottom_gesture_off"
        const val STATE_FILE = "q25toolbox_gesture_hook.state"
        private const val TOUCH_INTERACTION = "com.android.quickstep.TouchInteractionService"
        private val LAUNCHER_PKGS = setOf("com.android.launcher3", "org.lineageos.trebuchet")

        @Volatile private var proofWritten = false
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName !in LAUNCHER_PKGS) return
        var attached = false
        runCatching {
            val tis = XposedHelpers.findClass(TOUCH_INTERACTION, lpparam.classLoader)
            XposedBridge.hookAllMethods(tis, "onInputEvent", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val ctx = currentApplication() ?: return
                    writeProof(ctx)
                    val off = try {
                        Settings.Global.getInt(ctx.contentResolver, PREF_OFF, 0) == 1
                    } catch (_: Throwable) { false }
                    if (off) param.result = null // skip: no swipe-up home / recents / quick switch
                }
            })
            attached = true
        }
        XposedBridge.log("[$TAG] TouchInteractionService.onInputEvent hook attached=$attached")
    }

    /** Once per launcher process: "the hook ran", with the launcher versionCode so a launcher update invalidates it. */
    private fun writeProof(ctx: Context) {
        if (proofWritten) return
        proofWritten = true
        try {
            @Suppress("DEPRECATION")
            val versionCode = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
            java.io.File(ctx.filesDir, STATE_FILE).writeText("ok=1\nlauncher=$versionCode\n")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] gesture proof write failed: ${t.message}")
        }
    }

    private fun currentApplication(): Context? = try {
        val at = XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.app.ActivityThread", null), "currentActivityThread")
        XposedHelpers.callMethod(at, "getApplication") as? Context
    } catch (_: Throwable) { null }
}
