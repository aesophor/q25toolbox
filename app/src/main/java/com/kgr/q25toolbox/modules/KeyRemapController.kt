package com.kgr.q25toolbox.modules

import android.content.SharedPreferences
import android.util.Log
import com.kgr.q25toolbox.core.RomProfile
import com.kgr.q25toolbox.core.RootShell
import com.kgr.q25toolbox.core.ShellResult

/**
 * Hardware-level key remapping for the Q25 keyboard.
 *
 * It overrides the keylayout file `Q25_keyboard.kl` using a bind mount from
 * `/data/local/tmp/Q25_keyboard.kl` in the master mount namespace (`su -M`).
 *
 * The file's actual partition differs by ROM: stock BenOS ships it under
 * `/system/usr/keylayout/`, while a Treble-compliant build (e.g. the
 * unofficial LineageOS port, device tree `device/xelex/Q25`) ships it under
 * `/vendor/usr/keylayout/` instead - confirmed via that device tree's
 * `device.mk` (`PRODUCT_COPY_FILES` targets `$(TARGET_COPY_OUT_VENDOR)`).
 * Rather than hardcode one and silently no-op on the other (the original bug:
 * always targeting `/system`, so the boot script's "wait for the file" loop
 * spun forever on a Lineage build), every script here probes both at runtime
 * and uses whichever actually exists - including its matching SELinux label
 * (`system_file` vs `vendor_keylayout_file`; plain `vendor_file` is denied to system_server, verified
 * on LineageOS 23 where EventHub then silently falls back to Generic.kl), since the wrong one could make the
 * bind-mounted copy unreadable by system_server/EventHub under enforcing.
 *
 * Live reload is achieved by unbinding and rebinding the Q25_keyboard i2c driver.
 * Persistence across boots is managed via `/data/adb/service.d/key_remap.sh`.
 */
object KeyRemapController {

    const val KEY_REMAP_ENABLED = "key_remap_enabled"
    const val KEY_REMAP_SOURCE  = "key_remap_source"

    /**
     * The system always opens its own Overview on the physical Recents key (APP_SWITCH, scancode 580),
     * even when an accessibility service consumes the key (verified on LineageOS 23: the launcher resumes
     * before our overlay is up). So while a Recents overlay mode is selected, that scancode is remapped to
     * [RECENTS_TRIGGER_KEYCODE], which Android attaches no behaviour to; the accessibility service then
     * treats it as the "open Recents overlay" key.
     */
    const val KEY_RECENTS_OVERLAY_REMAP = "recents_overlay_key_remap"
    const val RECENTS_TRIGGER_KEYCODE = "PROG_RED"

    /**
     * "Leave the Recents key to another app" (Key Mapper). The scancode stays remapped to [RECENTS_TRIGGER_KEYCODE] so
     * the system does nothing with it, but our service no longer acts on the key: the other app sees a plain
     * PROG_RED (keycode 183, scancode 580) and decides what it does. It can ask us to open our overlay through
     * [com.kgr.q25toolbox.service.RunActionReceiver]. LineageOS only (the setting is ignored elsewhere): BenOS keeps its
     * behaviour.
     */
    const val KEY_RECENTS_KEY_EXTERNAL = "recents_key_external"
    private const val RECENTS_SCANCODE = 580

    private const val BOOT_SCRIPT = "/data/adb/service.d/key_remap.sh"
    private const val TMP_FILE = "/data/local/tmp/Q25_keyboard.kl"
    private const val SYSTEM_FILE = "/system/usr/keylayout/Q25_keyboard.kl"
    private const val VENDOR_FILE = "/vendor/usr/keylayout/Q25_keyboard.kl"

    /**
     * Resolves to whichever of [SYSTEM_FILE] / [VENDOR_FILE] exists on this device, setting
     * $SYS_FILE and $SELABEL for the rest of the script to use. Waits (rather than failing
     * outright) since this can run at boot before either partition's overlay is mounted yet.
     */
    private val resolveSysFile = """
        while [ ! -f $SYSTEM_FILE ] && [ ! -f $VENDOR_FILE ]; do
          sleep 1
        done
        if [ -f $SYSTEM_FILE ]; then
          SYS_FILE=$SYSTEM_FILE
          SELABEL=u:object_r:system_file:s0
        else
          SYS_FILE=$VENDOR_FILE
          SELABEL=u:object_r:vendor_keylayout_file:s0
        fi
    """.trimIndent()

    enum class SourceKey(
        val label: String,
        val description: String,
        val scancode: Int,
        val originalKeycode: String
    ) {
        GRAVE(
            "Currency key",
            "The € / £ / $ key next to Space. Currently mismapped as backtick by the firmware — " +
            "remapping it to Ctrl doesn't affect normal typing.",
            41,
            "GRAVE"
        ),
        RSHIFT(
            "Right Shift",
            "The right-hand Shift key. Left Shift still works normally for uppercase.",
            54,
            "SHIFT_RIGHT"
        ),
        RECENTS(
            "Recents (BlackBerry key)",
            "The dedicated recent-apps/task-switcher key. Remapping it to Ctrl means you lose " +
            "the hardware shortcut for recents.",
            580,
            "APP_SWITCH"
        )
    }

    fun isEnabled(prefs: SharedPreferences) =
        prefs.getBoolean(KEY_REMAP_ENABLED, false)

    fun setEnabled(prefs: SharedPreferences, enabled: Boolean) =
        prefs.edit().putBoolean(KEY_REMAP_ENABLED, enabled).apply()

    fun isRecentsOverlayRemap(prefs: SharedPreferences) =
        prefs.getBoolean(KEY_RECENTS_OVERLAY_REMAP, false)

    fun isRecentsKeyExternal(prefs: SharedPreferences) = prefs.getBoolean(KEY_RECENTS_KEY_EXTERNAL, false)

    fun setRecentsKeyExternal(prefs: SharedPreferences, on: Boolean) =
        prefs.edit().putBoolean(KEY_RECENTS_KEY_EXTERNAL, on).apply()

    /**
     * Whether scancode 580 must be remapped to the trigger keycode: when an overlay Recents mode needs it or the key is
     * left to another app, except if the user chose the Recents key itself as their Ctrl source (then it is Ctrl).
     * Pure, for tests.
     */
    internal fun wantsRecentsRemap(overlayRemap: Boolean, external: Boolean, ctrlOnRecents: Boolean) =
        (overlayRemap || external) && !ctrlOnRecents

    fun setRecentsOverlayRemap(prefs: SharedPreferences, on: Boolean) =
        prefs.edit().putBoolean(KEY_RECENTS_OVERLAY_REMAP, on).apply()

    fun getSourceKey(prefs: SharedPreferences): SourceKey {
        val raw = prefs.getString(KEY_REMAP_SOURCE, SourceKey.GRAVE.name) ?: SourceKey.GRAVE.name
        return try { SourceKey.valueOf(raw) } catch (_: Exception) { SourceKey.GRAVE }
    }

    fun setSourceKey(prefs: SharedPreferences, key: SourceKey) =
        prefs.edit().putString(KEY_REMAP_SOURCE, key.name).apply()

    /**
     * Shell snippet that makes EventHub re-open the keyboard (and re-parse its keylayout) WITHOUT
     * touching the i2c driver: a uevent remove/add on the input node makes ueventd delete and
     * recreate /dev/input/eventN, which EventHub sees via inotify. Same idea as Key2Toolbox's
     * `reloadInputDevice`. Used where unbinding the driver can panic the kernel
     * ([RomProfile.keyboardRebindUnsafe]); verified live on LineageOS 23.
     */
    private const val UEVENT_RELOAD =
        "for d in /sys/class/input/event*; do " +
            "if [ \"\$(cat \"\$d/device/name\" 2>/dev/null)\" = \"Q25_keyboard\" ]; then " +
            "echo remove > \"\$d/uevent\"; sleep 1; echo add > \"\$d/uevent\"; fi; done"

    /** Apply the remapping settings live and update the boot script. */
    fun applySettings(prefs: SharedPreferences) {
        val ctrlEnabled = isEnabled(prefs)
        val source = getSourceKey(prefs)
        val rebind = !RomProfile.keyboardRebindUnsafe()
        // If the user chose the Recents key itself as their Ctrl source, it is Ctrl, not an overlay trigger.
        val recentsRemap = wantsRecentsRemap(isRecentsOverlayRemap(prefs), isRecentsKeyExternal(prefs) && RomProfile.autoDetectedLineage(), ctrlEnabled && source == SourceKey.RECENTS)
        val enabled = ctrlEnabled || recentsRemap

        if (enabled) {
            val script = generateBootScript(if (ctrlEnabled) source else null, rebind, recentsRemap)
            // Write script to /data/adb/service.d/
            RootShell.run("cat << 'EOF' > $BOOT_SCRIPT\n$script\nEOF\nchmod 755 $BOOT_SCRIPT")
            // Execute the script live using mount-master namespace
            RootShell.run("su -M -c '$BOOT_SCRIPT'")
            Log.d("KeyRemapController", "Applied remap for ${source.name} and saved boot script (rebind=$rebind)")
        } else {
            // Delete boot script
            RootShell.run("rm -f $BOOT_SCRIPT")
            // Clean up the mount and reload the keyboard to restore defaults. Unmounts both
            // candidate paths unconditionally (whichever wasn't ever bind-mounted just no-ops)
            // rather than re-resolving which one applies - simpler and just as safe for cleanup.
            val reload = if (rebind) {
                "echo 6-001f > /sys/bus/i2c/drivers/Q25_keyboard/unbind ; sleep 1 ; "
            } else ""
            val reattach = if (rebind) " ; echo 6-001f > /sys/bus/i2c/drivers/Q25_keyboard/bind" else " ; $UEVENT_RELOAD"
            RootShell.run(
                "su -M -c '$reload" +
                "umount -l $SYSTEM_FILE 2>/dev/null" +
                " ; umount -l $VENDOR_FILE 2>/dev/null" +
                "$reattach" +
                " ; rm -f $TMP_FILE'"
            )
            Log.d("KeyRemapController", "Cleared remaps and restored defaults (rebind=$rebind)")
        }
    }

    /**
     * Unbinds and rebinds the Q25_keyboard i2c driver without touching any keylayout
     * remap/mount - a plain "power cycle the driver" recovery action, for when the physical
     * keyboard stops responding (observed after the phone's proximity sensor gets stuck
     * during/after a call - see Q25AccessibilityService's call-screen recovery). Any active
     * remap's bind mount is untouched by an unbind/rebind (it's a VFS construct independent
     * of the i2c driver being bound), so this is safe to call regardless of whether Key
     * Remap is enabled.
     */
    fun respawnKeyboard(): ShellResult =
        if (RomProfile.keyboardRebindUnsafe()) {
            ShellResult(false, listOf("driver rebind disabled: it can panic the kernel on this ROM"))
        } else respawnKeyboardUnsafe()

    private fun respawnKeyboardUnsafe(): ShellResult = RootShell.run(
        "su -M -c 'echo 6-001f > /sys/bus/i2c/drivers/Q25_keyboard/unbind" +
        " ; sleep 1" +
        " ; echo 6-001f > /sys/bus/i2c/drivers/Q25_keyboard/bind'"
    )

    /**
     * [rebind] = true (BenOS, unchanged behaviour): unbind the i2c driver before swapping the mount
     * and rebind after, so EventHub reloads the layout live. false (Lineage, see
     * [RomProfile.keyboardRebindUnsafe]): mount, then reload through a uevent remove/add instead.
     */
    internal fun generateBootScript(source: SourceKey?, rebind: Boolean, recentsRemap: Boolean = false): String {
        // $sysFileVar/$selabelVar are shell-side references to the variables resolveSysFile
        // sets, not Kotlin ones - built as plain strings so they paste into the script as
        // literal `$SYS_FILE`/`$SELABEL` for the shell to expand at runtime.
        val sysFileVar = "\$SYS_FILE"
        val selabelVar = "\$SELABEL"
        // The column padding between scancode and keycode in the layout file varies with the
        // scancode's digit width (e.g. "54    SHIFT_RIGHT" vs "580   APP_SWITCH"), so match on
        // one-or-more whitespace rather than a fixed run of spaces.
        // One sed pass applies every active remap. Ctrl source key (optional) and the Recents-overlay trigger.
        val exprs = buildList {
            if (source != null) add("s/key ${source.scancode}[[:space:]]+${source.originalKeycode}/key ${source.scancode} CTRL_RIGHT/")
            if (recentsRemap) add("s/key $RECENTS_SCANCODE[[:space:]]+APP_SWITCH/key $RECENTS_SCANCODE $RECENTS_TRIGGER_KEYCODE/")
        }
        val sedCommand = "sed -E ${exprs.joinToString(" ") { "-e \"$it\"" }} $sysFileVar > $TMP_FILE.tmp"
        val driver = "/sys/bus/i2c/drivers/Q25_keyboard"
        val unbindBlock = if (!rebind) "" else """
# Unbind the driver FIRST if it's already bound (e.g. switching source key while
# remap is enabled, not a fresh boot). This closes EventHub's file descriptor on
# the currently bind-mounted layout file, which is required before the unmount
# below can actually take effect - otherwise it stays lazily "unmounted but
# still open", and the cp right after would read through the stale mount (or a
# deleted-file dangling reference once rm -f runs), corrupting the copy instead
# of getting the pristine original.
if [ -d $driver ]; then
  echo 6-001f > $driver/unbind
  sleep 1
fi
"""
        val bindBlock = if (!rebind) "\n# Reload via uevent (no driver unbind, see UEVENT_RELOAD)\n$UEVENT_RELOAD\n" else """
# Rebind the driver so EventHub reloads the (now modified) keylayout
if [ -d $driver ]; then
  echo 6-001f > $driver/bind
fi
"""
        val bailRebind = if (!rebind) "" else "\n  if [ -d $driver ]; then echo 6-001f > $driver/bind; fi"
        return """
#!/system/bin/sh
# Resolve which partition actually has the layout file on this ROM (also waits for it
# to be available - this can run at boot before either overlay is mounted yet).
$resolveSysFile
$unbindBlock
# Now safe to fully release any stale mount from a previous boot/session
umount -l $sysFileVar 2>/dev/null

# Clean up tmp and copy the now-guaranteed-original file
rm -f $TMP_FILE
cp $sysFileVar $TMP_FILE

# Remap the target key
$sedCommand
cat $TMP_FILE.tmp > $TMP_FILE
rm -f $TMP_FILE.tmp

# Safety: this script re-runs every boot, so an empty result (failed cp/sed) must never
# be mounted - that would leave the keyboard dead on every boot. Only emptiness is checked,
# not content, so a valid layout on any ROM is never rejected.
if [ ! -s $TMP_FILE ]; then
  rm -f $TMP_FILE$bailRebind
  exit 1
fi

# Label it to match its target partition (system_file or vendor_file) so
# system_server/EventHub can read it under enforcing.
chcon $selabelVar $TMP_FILE

# Bind mount the modified keylayout over the original
mount --bind $TMP_FILE $sysFileVar
$bindBlock
        """.trimIndent()
    }
}
