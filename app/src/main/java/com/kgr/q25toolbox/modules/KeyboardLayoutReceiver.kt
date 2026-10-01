package com.kgr.q25toolbox.modules

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Advertises the bundled key character map to the framework.
 *
 * A keyboard-layout provider is declared purely in the manifest: the system
 * reads the `KEYBOARD_LAYOUTS` meta-data off a receiver registered for
 * `ACTION_QUERY_KEYBOARD_LAYOUTS` and never actually delivers the broadcast,
 * so this class only has to exist. The layout itself lives in
 * `res/raw/keyboard_layout_en_us.kcm` and is picked under
 * Settings -> System -> Languages & input -> Physical keyboard.
 *
 * No root, no accessibility service, no IME switch - the overlay is applied by
 * the framework's own EventHub.
 */
class KeyboardLayoutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
