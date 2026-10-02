package com.kgr.q25toolbox.modules

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyRemapScriptTest {
    private val src = KeyRemapController.SourceKey.GRAVE

    @Test fun lineageScriptNeverTouchesDriverBinding() {
        val s = KeyRemapController.generateBootScript(src, rebind = false)
        assertFalse(s.contains("/unbind")); assertFalse(s.contains("/bind"))
        assertTrue(s.contains("echo remove >")); assertTrue(s.contains("vendor_keylayout_file"))
        System.getenv("KEYREMAP_DUMP")?.let { File(it, "lineage.sh").writeText(s); File(it, "benos.sh").writeText(KeyRemapController.generateBootScript(src, true)) }
    }

    @Test fun benosScriptStillRebinds() {
        val s = KeyRemapController.generateBootScript(src, rebind = true)
        assertTrue(s.contains("Q25_keyboard/unbind")); assertTrue(s.contains("Q25_keyboard/bind"))
        assertFalse(s.contains("echo remove >"))
    }

    @Test fun recentsOverlayRemapAloneAndCombined() {
        val only = KeyRemapController.generateBootScript(null, rebind = false, recentsRemap = true)
        assertTrue(only.contains("key 580 PROG_RED")); assertFalse(only.contains("CTRL_RIGHT"))
        val both = KeyRemapController.generateBootScript(src, rebind = false, recentsRemap = true)
        assertTrue(both.contains("CTRL_RIGHT")); assertTrue(both.contains("key 580 PROG_RED"))
        System.getenv("KEYREMAP_DUMP")?.let { File(it, "both.sh").writeText(both) }
    }
}
