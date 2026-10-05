package com.kgr.q25toolbox.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeCompatTest {
    private val on = "ChangeId(327313645; name=OVERRIDE_ENABLE_INSETS_DECOUPLED_CONFIGURATION; disabled; " +
        "packageOverrides={com.blackberry.keyboard=true}; rawOverrides={com.blackberry.keyboard=true}; overridable)"
    private val off = "ChangeId(327313645; name=OVERRIDE_ENABLE_INSETS_DECOUPLED_CONFIGURATION; disabled; overridable)"

    @Test fun readsTheOverrideFromTheCompatDump() = assertTrue(ImeCompat.parseActive(on))

    @Test fun noOverrideMeansInactive() = assertFalse(ImeCompat.parseActive(off))

    @Test fun anotherPackagesOverrideDoesNotCount() =
        assertFalse(ImeCompat.parseActive(on.replace("com.blackberry.keyboard", "com.other.app")))

    @Test fun aFalseOverrideDoesNotCount() = assertFalse(ImeCompat.parseActive(on.replace("keyboard=true", "keyboard=false")))

    @Test fun emptyOutputIsInactive() = assertFalse(ImeCompat.parseActive(""))

    @Test fun otherChangesAreIgnored() =
        assertFalse(ImeCompat.parseActive(on.replace("OVERRIDE_ENABLE_INSETS_DECOUPLED_CONFIGURATION", "SOMETHING_ELSE")))

    @Test fun actsOnlyWhenTheSwitchAndTheSystemDiffer() {
        assertEquals("am compat enable OVERRIDE_ENABLE_INSETS_DECOUPLED_CONFIGURATION com.blackberry.keyboard", ImeCompat.commandFor(true, false))
        assertEquals("am compat reset OVERRIDE_ENABLE_INSETS_DECOUPLED_CONFIGURATION com.blackberry.keyboard", ImeCompat.commandFor(false, true))
        assertNull(ImeCompat.commandFor(true, true))   // already on: do not restart the keyboard again
        assertNull(ImeCompat.commandFor(false, false))
    }
}
