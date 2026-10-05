package com.kgr.q25toolbox.modules

import android.view.KeyEvent
import com.kgr.q25toolbox.modules.AppScalingController.Res
import com.kgr.q25toolbox.modules.ResolutionHotkey.Combo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolutionHotkeyTest {
    private val a = Res(720, 772)
    private val b = Res(720, 960)

    @Test fun oneResolutionIsAToggle() {
        assertEquals(a, ResolutionHotkey.next(listOf(a), null))
        assertNull(ResolutionHotkey.next(listOf(a), a))
    }

    @Test fun severalResolutionsStepThroughAndReturnToTheDefault() {
        val list = listOf(a, b)
        assertEquals(a, ResolutionHotkey.next(list, null))
        assertEquals(b, ResolutionHotkey.next(list, a))
        assertNull(ResolutionHotkey.next(list, b))
    }

    @Test fun anEmptyListHasNowhereToGo() = assertNull(ResolutionHotkey.next(emptyList(), null))

    @Test fun aResolutionNoLongerInTheListRestartsTheCycle() {
        assertEquals(a, ResolutionHotkey.next(listOf(a, b), Res(720, 1280)))
    }

    @Test fun listParsingDropsInvalidNativeAndRepeatedEntries() {
        assertEquals(listOf(a, b), ResolutionHotkey.parseList("720x772, 720x960,720x772,720x720,abc,0x5,"))
        assertEquals(emptyList<Res>(), ResolutionHotkey.parseList(null))
        assertEquals("720x772,720x960", ResolutionHotkey.encodeList(listOf(a, b)))
    }

    private val rightShift = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_RIGHT_ON
    private val leftShift = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON

    @Test fun rightShiftIsNotLeftShift() {
        val c = Combo(ResolutionHotkey.SHIFT_R, KeyEvent.KEYCODE_R)
        assertTrue(c.matches(rightShift, KeyEvent.KEYCODE_R))
        assertFalse(c.matches(leftShift, KeyEvent.KEYCODE_R))
        assertFalse(Combo(ResolutionHotkey.SHIFT_L, KeyEvent.KEYCODE_R).matches(rightShift, KeyEvent.KEYCODE_R))
    }

    @Test fun comboMatchesExactlyItsModifiers() {
        val c = ResolutionHotkey.DEFAULT_COMBO
        assertTrue(c.matches(rightShift or KeyEvent.META_CAPS_LOCK_ON, KeyEvent.KEYCODE_R)) // locks are ignored
        assertFalse(c.matches(0, KeyEvent.KEYCODE_R))                                       // plain R
        assertFalse(c.matches(rightShift or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON, KeyEvent.KEYCODE_R)) // extra modifier
        assertFalse(c.matches(rightShift, KeyEvent.KEYCODE_E))
    }

    @Test fun twoModifiersMustBothBeDown() {
        val c = Combo(ResolutionHotkey.CTRL_R or ResolutionHotkey.SHIFT_R, KeyEvent.KEYCODE_7)
        val both = rightShift or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_RIGHT_ON
        assertTrue(c.matches(both, KeyEvent.KEYCODE_7))
        assertFalse(c.matches(rightShift, KeyEvent.KEYCODE_7))
    }

    @Test fun aModifierWithoutASideCountsAsTheLeftOne() {
        assertEquals(ResolutionHotkey.SHIFT_L, ResolutionHotkey.modsOf(KeyEvent.META_SHIFT_ON))
        assertEquals(ResolutionHotkey.CTRL_L, ResolutionHotkey.modsOf(KeyEvent.META_CTRL_ON))
        assertEquals(0, ResolutionHotkey.modsOf(KeyEvent.META_CAPS_LOCK_ON or KeyEvent.META_NUM_LOCK_ON))
    }

    @Test fun comboEncodingRoundTripsAndRejectsBadValues() {
        val c = Combo(ResolutionHotkey.CTRL_L or ResolutionHotkey.ALT_R, KeyEvent.KEYCODE_7)
        assertEquals(c, Combo.decode(c.encode()))
        listOf(null, "", "1", "0:46", "256:46", "1:4", "1:x", "a:46", "1:46:2").forEach { assertNull(it, Combo.decode(it)) }
    }

    @Test fun keysAreLettersAndDigitsOnly() {
        assertEquals(KeyEvent.KEYCODE_R, ResolutionHotkey.keyCodeFor('r'))
        assertEquals(KeyEvent.KEYCODE_0, ResolutionHotkey.keyCodeFor('0'))
        assertNull(ResolutionHotkey.keyCodeFor('-'))
        assertEquals('R', ResolutionHotkey.charFor(KeyEvent.KEYCODE_R))
        assertEquals('9', ResolutionHotkey.charFor(KeyEvent.KEYCODE_9))
        assertNull(ResolutionHotkey.charFor(KeyEvent.KEYCODE_ENTER))
    }

    @Test fun pressTypeDefaultsToShortAndParsesCaseInsensitively() {
        assertEquals(ResolutionHotkey.Press.SHORT, ResolutionHotkey.Press.parse(null))
        assertEquals(ResolutionHotkey.Press.SHORT, ResolutionHotkey.Press.parse("nonsense"))
        assertEquals(ResolutionHotkey.Press.LONG, ResolutionHotkey.Press.parse("long"))
        assertEquals(ResolutionHotkey.Press.LONG, ResolutionHotkey.Press.parse(ResolutionHotkey.Press.LONG.name))
    }

    @Test fun reinjectCommandPressesTheModifiersThenTheKey() {
        assertEquals("input keycombination 60 46", ResolutionHotkey.reinjectCommand(ResolutionHotkey.DEFAULT_COMBO))
        assertEquals("input keycombination 113 58 12",
            ResolutionHotkey.reinjectCommand(Combo(ResolutionHotkey.CTRL_L or ResolutionHotkey.ALT_R, KeyEvent.KEYCODE_5)))
    }

    @Test fun theDefaultComboIsRightShiftR() {
        assertEquals("Right Shift + R", ResolutionHotkey.label(ResolutionHotkey.DEFAULT_COMBO))
        assertEquals("Left Ctrl + Right Alt + 7",
            ResolutionHotkey.label(Combo(ResolutionHotkey.CTRL_L or ResolutionHotkey.ALT_R, KeyEvent.KEYCODE_7)))
    }
}
