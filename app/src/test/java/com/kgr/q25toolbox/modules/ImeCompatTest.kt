package com.kgr.q25toolbox.modules

import org.junit.Assert.assertEquals
import org.junit.Test

class ImeCompatTest {
    @Test fun proofFileLivesInTheKeyboardsOwnFilesDir() =
        assertEquals("/data/user/0/com.blackberry.keyboard/files/q25toolbox_ime_compat.state", ImeCompat.statePath())
}
