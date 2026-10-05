package com.kgr.q25toolbox.modules

import android.content.Context
import android.view.KeyEvent
import com.kgr.q25toolbox.modules.AppScalingController.Res
import com.kgr.q25toolbox.service.Q25AccessibilityService

/**
 * Global resolution hotkey: a key combination that steps the screen through a list of resolutions and back to the
 * default (native), independently of the per-app targets. With one resolution it is a toggle (Ctrl+R: 720x772, again:
 * default); with several, each press goes to the next one and the press after the last returns to the default.
 *
 * The decision logic is pure ([next], [Combo], [parseList]) so it can be unit tested; the accessibility service owns
 * the key handling and applying the size (see Q25AccessibilityService.cycleResolution).
 */
object ResolutionHotkey {

    const val KEY_ENABLED = "res_hotkey_enabled"
    const val KEY_COMBO = "res_hotkey_combo"
    const val KEY_LIST = "res_hotkey_list"
    const val KEY_PRESS = "res_hotkey_press"
    const val KEY_VIBRATE = "res_hotkey_vibrate"
    const val KEY_TAP_PASS = "res_hotkey_tap_pass"

    /** Whether the combo acts when pressed (SHORT) or only once held for [LONG_MS] (LONG). */
    enum class Press { SHORT, LONG;
        companion object { fun parse(s: String?) = entries.firstOrNull { it.name == s?.uppercase() } ?: SHORT }
    }

    /** How long the combo must be held in LONG mode. */
    const val LONG_MS = 500L

    /**
     * Modifier bits of a [Combo]: each physical modifier key is its own bit (own values, not Android's meta state, so
     * they can be stored), because the left and right ones are different keys on this keyboard and are used differently.
     */
    const val CTRL_L = 1
    const val CTRL_R = 2
    const val ALT_L = 4
    const val ALT_R = 8
    const val SHIFT_L = 16
    const val SHIFT_R = 32
    const val META_L = 64
    const val META_R = 128
    private const val ALL_MODS = 255

    /** The modifier keys in the order the screen lists them, with their names. */
    val MODIFIERS = listOf(
        CTRL_L to "Left Ctrl", CTRL_R to "Right Ctrl", ALT_L to "Left Alt", ALT_R to "Right Alt",
        SHIFT_L to "Left Shift", SHIFT_R to "Right Shift", META_L to "Left Meta", META_R to "Right Meta",
    )

    /** A set of modifier keys plus one key. Needs at least one modifier, so a plain key is never taken over. */
    data class Combo(val mods: Int, val keyCode: Int) {
        fun encode() = "$mods:$keyCode"

        /** True if exactly this combo's modifier keys are down in [metaState] (Caps/Num lock and the like are ignored). */
        fun matches(metaState: Int, key: Int) = key == keyCode && modsOf(metaState) == mods

        companion object {
            fun decode(s: String?): Combo? {
                val p = s?.split(':') ?: return null
                if (p.size != 2) return null
                val mods = p[0].toIntOrNull() ?: return null
                val key = p[1].toIntOrNull() ?: return null
                return if (mods in 1..ALL_MODS && charFor(key) != null) Combo(mods, key) else null
            }
        }
    }

    /** Right Shift + R. */
    val DEFAULT_COMBO = Combo(SHIFT_R, KeyEvent.KEYCODE_R)

    /**
     * The modifier keys down in an Android meta state. A modifier reported without a side (some injected events) counts
     * as the left one.
     */
    fun modsOf(metaState: Int): Int {
        fun has(flag: Int) = metaState and flag != 0
        var m = 0
        if (has(KeyEvent.META_CTRL_LEFT_ON)) m = m or CTRL_L
        if (has(KeyEvent.META_CTRL_RIGHT_ON)) m = m or CTRL_R
        if (has(KeyEvent.META_CTRL_ON) && !has(KeyEvent.META_CTRL_LEFT_ON) && !has(KeyEvent.META_CTRL_RIGHT_ON)) m = m or CTRL_L
        if (has(KeyEvent.META_ALT_LEFT_ON)) m = m or ALT_L
        if (has(KeyEvent.META_ALT_RIGHT_ON)) m = m or ALT_R
        if (has(KeyEvent.META_ALT_ON) && !has(KeyEvent.META_ALT_LEFT_ON) && !has(KeyEvent.META_ALT_RIGHT_ON)) m = m or ALT_L
        if (has(KeyEvent.META_SHIFT_LEFT_ON)) m = m or SHIFT_L
        if (has(KeyEvent.META_SHIFT_RIGHT_ON)) m = m or SHIFT_R
        if (has(KeyEvent.META_SHIFT_ON) && !has(KeyEvent.META_SHIFT_LEFT_ON) && !has(KeyEvent.META_SHIFT_RIGHT_ON)) m = m or SHIFT_L
        if (has(KeyEvent.META_META_LEFT_ON)) m = m or META_L
        if (has(KeyEvent.META_META_RIGHT_ON)) m = m or META_R
        if (has(KeyEvent.META_META_ON) && !has(KeyEvent.META_META_LEFT_ON) && !has(KeyEvent.META_META_RIGHT_ON)) m = m or META_L
        return m
    }

    /** Letters A-Z and digits 0-9 are the keys a combo can use. Pure. */
    fun charFor(keyCode: Int): Char? = when (keyCode) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> 'A' + (keyCode - KeyEvent.KEYCODE_A)
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> '0' + (keyCode - KeyEvent.KEYCODE_0)
        else -> null
    }

    fun keyCodeFor(c: Char): Int? = when (val u = c.uppercaseChar()) {
        in 'A'..'Z' -> KeyEvent.KEYCODE_A + (u - 'A')
        in '0'..'9' -> KeyEvent.KEYCODE_0 + (u - '0')
        else -> null
    }

    /** "Right Shift + R" for the screen. */
    fun label(c: Combo): String = (MODIFIERS.filter { c.mods and it.first != 0 }.map { it.second } +
        (charFor(c.keyCode)?.toString() ?: "?")).joinToString(" + ")

    /** "720x772,720x960" -> resolutions; invalid, native and repeated entries are dropped, order is kept. */
    fun parseList(s: String?): List<Res> =
        (s ?: "").split(',').mapNotNull { Res.decode(it) }.filter { !it.isNative }.distinct()

    fun encodeList(list: List<Res>) = list.joinToString(",") { it.encode() }

    /**
     * The resolution after [current] (null = the default) in [list]; null means "back to the default". With an empty
     * list there is nothing to go to. A [current] that is not in the list (the list was edited meanwhile) restarts it.
     */
    fun next(list: List<Res>, current: Res?): Res? {
        if (list.isEmpty()) return null
        if (current == null) return list.first()
        val i = list.indexOf(current)
        return if (i < 0) list.first() else list.getOrNull(i + 1)
    }

    // --- prefs ---

    private fun prefs(context: Context) = context.getSharedPreferences(Q25AccessibilityService.PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, on: Boolean) { prefs(context).edit().putBoolean(KEY_ENABLED, on).apply() }

    fun combo(context: Context): Combo = Combo.decode(prefs(context).getString(KEY_COMBO, null)) ?: DEFAULT_COMBO
    fun setCombo(context: Context, c: Combo) { prefs(context).edit().putString(KEY_COMBO, c.encode()).apply() }

    fun press(context: Context) = Press.parse(prefs(context).getString(KEY_PRESS, null))
    fun setPress(context: Context, p: Press) { prefs(context).edit().putString(KEY_PRESS, p.name).apply() }

    /** In LONG mode, a quick tap types the key after all (re-injected as a key combination). On by default. */
    fun tapPass(context: Context) = prefs(context).getBoolean(KEY_TAP_PASS, true)
    fun setTapPass(context: Context, on: Boolean) { prefs(context).edit().putBoolean(KEY_TAP_PASS, on).apply() }

    /** Android key codes of the modifier bits, in the order they are pressed. */
    private val MODIFIER_KEYCODES = listOf(
        CTRL_L to KeyEvent.KEYCODE_CTRL_LEFT, CTRL_R to KeyEvent.KEYCODE_CTRL_RIGHT,
        ALT_L to KeyEvent.KEYCODE_ALT_LEFT, ALT_R to KeyEvent.KEYCODE_ALT_RIGHT,
        SHIFT_L to KeyEvent.KEYCODE_SHIFT_LEFT, SHIFT_R to KeyEvent.KEYCODE_SHIFT_RIGHT,
        META_L to KeyEvent.KEYCODE_META_LEFT, META_R to KeyEvent.KEYCODE_META_RIGHT,
    )

    /** The shell command that types [c] again: `input keycombination <modifiers...> <key>` (about 75 ms on the Q25). Pure. */
    fun reinjectCommand(c: Combo): String =
        "input keycombination " + (MODIFIER_KEYCODES.filter { c.mods and it.first != 0 }.map { it.second } + c.keyCode).joinToString(" ")

    /** Short vibration when the hotkey acts (the pulse set in Edge Gestures, Vibration). On by default. */
    fun vibrate(context: Context) = prefs(context).getBoolean(KEY_VIBRATE, true)
    fun setVibrate(context: Context, on: Boolean) { prefs(context).edit().putBoolean(KEY_VIBRATE, on).apply() }

    /** The cycle; until the user sets one it holds the example 720x772. */
    fun list(context: Context): List<Res> =
        prefs(context).getString(KEY_LIST, null)?.let { parseList(it) } ?: listOf(Res(720, 772))

    fun setList(context: Context, list: List<Res>) { prefs(context).edit().putString(KEY_LIST, encodeList(list)).apply() }
}
