package com.kgr.q25toolbox.modules

import com.kgr.q25toolbox.modules.RecentsTweaksController.HookHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsHookHealthTest {
    private val c = RecentsTweaksController

    @Test fun okOnlyWhenTheRecordedLauncherIsTheInstalledOne() {
        assertEquals(HookHealth.OK, c.parseHandshake("ok=1\nlauncher=36\n", "36"))
        assertEquals(HookHealth.BROKEN, c.parseHandshake("ok=0\nlauncher=36\n", "36"))
    }

    @Test fun aLauncherUpdateInvalidatesTheVerdict() {
        assertEquals(HookHealth.UNKNOWN, c.parseHandshake("ok=1\nlauncher=36\n", "37"))
        assertEquals(HookHealth.UNKNOWN, c.parseHandshake("ok=0\nlauncher=36\n", "37"))
    }

    @Test fun garbageOrMissingVersionIsUnknownNeverOk() {
        assertEquals(HookHealth.UNKNOWN, c.parseHandshake("", "36"))
        assertEquals(HookHealth.UNKNOWN, c.parseHandshake("ok=1\n", "36"))
        assertEquals(HookHealth.UNKNOWN, c.parseHandshake("ok=1\nlauncher=36\n", null))
    }

    @Test fun hookIsUsedOnlyWhenProvenOrAlreadyWorking() {
        assertTrue(c.shouldUseHook(HookHealth.OK, hookKeyOn = false))
        assertFalse(c.shouldUseHook(HookHealth.BROKEN, hookKeyOn = true))   // broken always falls back
        assertFalse(c.shouldUseHook(HookHealth.UNKNOWN, hookKeyOn = false)) // a new choice starts standalone
        assertTrue(c.shouldUseHook(HookHealth.UNKNOWN, hookKeyOn = true))   // updating from v3: keep what works
    }
}
