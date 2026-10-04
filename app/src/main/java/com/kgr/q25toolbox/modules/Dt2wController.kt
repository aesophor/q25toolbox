package com.kgr.q25toolbox.modules

import android.content.Context
import com.kgr.q25toolbox.core.AssetInstaller
import com.kgr.q25toolbox.core.RomProfile
import com.kgr.q25toolbox.core.RootShell
import com.kgr.q25toolbox.core.ShellResult

/**
 * DT2W (Double Tap to Wake), implemented in software.
 *
 * The Q25 touch panel has no hardware/driver gesture-wake and the
 * double_tap_to_wake secure setting is not wired to anything on this ROM, so a
 * root watchdog daemon (/data/adb/service.d/dt2w.sh) watches the touchscreen
 * for a quick double-tap while the screen is off and injects KEYCODE_WAKEUP.
 *
 * Root-daemon based, so there's no live "sysfs write" - "enabled" means the
 * daemon is installed for next boot and launched now.
 */
object Dt2wController {

    private const val SCRIPT_NAME = "dt2w.sh"
    private const val TARGET = "/data/adb/service.d/$SCRIPT_NAME"
    private const val LOCK = "/data/adb/.dt2w.lock"

    // LineageOS: a listener that exists only while the screen is off (see assets/dt2w_lineage.sh).
    private const val LINEAGE_ASSET = "dt2w_lineage.sh"
    private const val LINEAGE_DIR = "/data/adb/q25toolbox"
    private const val LINEAGE_SCRIPT = "$LINEAGE_DIR/dt2w_lineage.sh"
    private const val LINEAGE_LOCK = "/data/adb/.dt2w_lineage.lock"
    const val KEY_LINEAGE_ENABLED = "dt2w_screen_off_listener"
    private const val PREFS = "q25tweaks"

    /** The BenOS watchdog stays as it was (and hidden); LineageOS uses the screen-off listener. */
    fun usesScreenOffListener(): Boolean = RomProfile.autoDetectedLineage()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Enabled = the daemon is installed for next boot (BenOS) / the screen-off listener is switched on (Lineage). */
    fun isPersisted(context: Context? = null): Boolean =
        if (usesScreenOffListener()) {
            context?.let { prefs(it).getBoolean(KEY_LINEAGE_ENABLED, false) } ?: AssetInstaller.fileExists(LINEAGE_SCRIPT)
        } else AssetInstaller.fileExists(TARGET)

    /** Whether a DT2W process is currently running (on Lineage that is only ever while the screen is off). */
    fun isRunning(): Boolean =
        if (usesScreenOffListener()) {
            RootShell.run("p=\$(cat $LINEAGE_LOCK 2>/dev/null); [ -n \"\$p\" ] && grep -q dt2w_lineage /proc/\$p/cmdline 2>/dev/null && echo yes || echo no")
                .outString.trim() == "yes"
        } else {
            RootShell.run("pgrep -f $SCRIPT_NAME >/dev/null 2>&1 && echo yes || echo no")
                .outString.trim() == "yes"
        }

    /**
     * Enables or disables DT2W. On LineageOS this only installs the script and flips the preference; the
     * accessibility service starts and stops the listener with the screen ([onScreenOff] / [onScreenOn]).
     * On BenOS it installs/launches (or stops/removes) the old watchdog, exactly as before.
     */
    fun setEnabled(context: Context, enabled: Boolean): ShellResult {
        if (usesScreenOffListener()) {
            prefs(context).edit().putBoolean(KEY_LINEAGE_ENABLED, enabled).apply()
            return if (enabled) {
                RootShell.run("mkdir -p $LINEAGE_DIR")
                AssetInstaller.installFromAsset(context, LINEAGE_ASSET, LINEAGE_SCRIPT)
            } else {
                onScreenOn()
                AssetInstaller.removeFile(LINEAGE_SCRIPT)
            }
        }

        // "pkill -f" was found unreliable on this device's toybox build - it can report
        // success without actually killing the match. kill+pgrep does actually work.
        RootShell.run("kill \$(pgrep -f $SCRIPT_NAME) 2>/dev/null; rm -f $LOCK")

        return if (enabled) {
            val result = AssetInstaller.installFromAsset(context, SCRIPT_NAME, TARGET)
            // setsid detaches into its own session so it doesn't get dragged down when the
            // invoking root shell (a transient libsu session) is later recycled - see
            // ExtraDimController for the same fix and why it was needed.
            RootShell.run("nohup setsid sh $TARGET </dev/null >/dev/null 2>&1 &")
            result
        } else {
            AssetInstaller.removeFile(TARGET)
        }
    }

    /** Called when the screen turns off. Blocking (root shell): never from the main thread. */
    fun onScreenOff(context: Context) {
        if (!usesScreenOffListener() || !prefs(context).getBoolean(KEY_LINEAGE_ENABLED, false)) return
        // Re-deploy if the file went missing (data wipe of /data/adb/q25toolbox, restored backup, ...).
        if (!AssetInstaller.fileExists(LINEAGE_SCRIPT)) {
            RootShell.run("mkdir -p $LINEAGE_DIR")
            AssetInstaller.installFromAsset(context, LINEAGE_ASSET, LINEAGE_SCRIPT)
        }
        // setsid: own session + process group, so the script's `kill 0` ends exactly its own getevent/awk pipeline.
        RootShell.run("nohup setsid sh $LINEAGE_SCRIPT </dev/null >/dev/null 2>&1 &")
    }

    /** Called when the screen turns on (and on disable): stops the listener if it is still running. Blocking. */
    fun onScreenOn() {
        if (!usesScreenOffListener()) return
        RootShell.run("p=\$(cat $LINEAGE_LOCK 2>/dev/null); [ -n \"\$p\" ] && kill \$p 2>/dev/null; true")
    }
}
