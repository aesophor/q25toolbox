package com.kgr.q25toolbox.xposed

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Keeps the BlackBerry keyboard (com.blackberry.keyboard) on its suggestions strip on the Q25's LineageOS.
 *
 * Why (decompiled from the installed APK, AA001.021Q25P): its `S()` is `Build.DEVICE != "venice" && orientation ==
 * LANDSCAPE`, and `S()` makes it show the full on-screen keyboard. On the Q25's square 720x720 screen the keyboard
 * process gets a landscape configuration (597 x 569 dp: the status bar takes 34 px) after some configuration events
 * (a display-size round trip reproduces it on demand), although the display itself reports portrait. Here
 * `Build.DEVICE` is "Q25", so the exemption never applies. This hook sets `Build.DEVICE` to "venice" inside that one
 * process, which makes `S()` false whatever the orientation. It touches no other process.
 *
 * It is a first step and a causal test: other reads of the orientation in the keyboard still see the real value.
 *
 * Gated by the world-readable `Settings.Global` key [PREF_ON] (0 = off, the default; the app only offers it on
 * LineageOS). Applied once per keyboard process, in `Application.attach`, before the keyboard's own code runs. On
 * success it leaves a proof-of-life file in the keyboard's data dir for the app to read.
 *
 * Debug on device:  `adb logcat | grep Q25Toolbox-Xposed`
 */
class KeyboardCompatHookInit : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "Q25Toolbox-Xposed"
        const val PKG = "com.blackberry.keyboard"
        const val PREF_ON = "q25_blackberry_ime_compat"
        const val STATE_FILE = "q25toolbox_ime_compat.state"
        const val FAKE_DEVICE = "venice"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != PKG) return
        runCatching {
            XposedHelpers.findAndHookMethod(Application::class.java, "attach", Context::class.java, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val ctx = param.args[0] as? Context ?: return
                    apply(ctx)
                }
            })
        }.onFailure { XposedBridge.log("[$TAG] ime compat: could not hook Application.attach: ${it.message}") }
    }

    private fun apply(ctx: Context) {
        val on = try { Settings.Global.getInt(ctx.contentResolver, PREF_ON, 0) == 1 } catch (_: Throwable) { false }
        if (!on) { XposedBridge.log("[$TAG] ime compat: off"); return }
        val before = Build.DEVICE
        try {
            XposedHelpers.setStaticObjectField(Build::class.java, "DEVICE", FAKE_DEVICE)
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] ime compat: could not set Build.DEVICE: ${t.message}")
        }
        val ok = Build.DEVICE == FAKE_DEVICE
        XposedBridge.log("[$TAG] ime compat: Build.DEVICE $before -> ${Build.DEVICE} ok=$ok")
        try {
            @Suppress("DEPRECATION")
            val versionCode = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
            java.io.File(ctx.filesDir, STATE_FILE).writeText("ok=${if (ok) 1 else 0}\nlauncher=$versionCode\nbefore=$before\n")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] ime compat: proof write failed: ${t.message}")
        }
    }
}
