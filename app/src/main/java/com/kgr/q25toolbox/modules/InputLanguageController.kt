package com.kgr.q25toolbox.modules

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import com.kgr.q25toolbox.core.RootShell
import com.kgr.q25toolbox.service.Q25AccessibilityService

/**
 * Shift+Space cycles the current keyboard's enabled languages.
 *
 * Gboard exposes each language as an InputMethodSubtype of one IME, so this
 * walks that list rather than switching IMEs. The only way to set it from
 * outside the IME is the deprecated [InputMethodManager.setCurrentInputMethodSubtype],
 * which is gated behind WRITE_SECURE_SETTINGS - a signature permission that
 * can still be granted to an installed app, once, over adb:
 *
 *     adb shell pm grant com.kgr.q25toolbox android.permission.WRITE_SECURE_SETTINGS
 *
 * The grant survives reboots and needs no root, no Shizuku and nothing left
 * running; it is lost only on uninstall. [grantWithRoot] does the same thing
 * through su for rooted devices.
 */
object InputLanguageController {
    private const val TAG = "InputLanguage"
    private const val PKG = "com.kgr.q25toolbox"

    fun isEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(Q25AccessibilityService.KEY_LANG_SWITCH, false)

    fun setEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(Q25AccessibilityService.KEY_LANG_SWITCH, enabled).apply()
    }

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun grantWithRoot(): Boolean =
        RootShell.run("pm grant $PKG ${Manifest.permission.WRITE_SECURE_SETTINGS}").success

    /** How many languages the current keyboard has enabled; < 2 means nothing to cycle. */
    fun subtypeCount(context: Context): Int = subtypes(context).size

    /**
     * Switches to the next enabled language of the current keyboard, wrapping around.
     * Returns false (and changes nothing) if the permission is missing or there is
     * only one language, so the caller can leave the key alone.
     */
    fun cycleSubtype(context: Context): Boolean {
        if (!hasPermission(context)) return false

        val subtypes = subtypes(context)
        if (subtypes.size < 2) return false

        val current = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE,
            -1
        )
        val index = subtypes.indexOfFirst { it.hashCode() == current }
        val next = subtypes[(index + 1) % subtypes.size]

        return try {
            @Suppress("DEPRECATION")
            context.getSystemService(InputMethodManager::class.java)
                .currentInputMethodSubtype = next
            true
        } catch (e: Exception) {
            Log.w(TAG, "could not switch input language", e)
            false
        }
    }

    private fun subtypes(context: Context) = try {
        val imm = context.getSystemService(InputMethodManager::class.java)
        val chosenId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
        val info = imm.inputMethodList.firstOrNull { it.id == chosenId }
        if (info == null) emptyList() else imm.getEnabledInputMethodSubtypeList(info, true)
    } catch (e: Exception) {
        Log.w(TAG, "could not list input languages", e)
        emptyList()
    }
}
