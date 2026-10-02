package com.kgr.q25toolbox.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RomProfileTest {
    private fun rom(props: Map<String, String>, mfr: String = "", sdk: Int = 35) =
        RomProfile.classify(props, mfr, sdk)

    @Test fun lineageFromVersionProp() {
        val (r, major, _) = rom(mapOf("ro.lineage.version" to "22.2-2026-UNOFFICIAL", "ro.lineage.build.version" to "22.2"))
        assertEquals(Rom.LINEAGE, r); assertEquals(22, major)
    }

    @Test fun lineage23FallsBackToSdkWhenBuildVersionMissing() {
        val (r, major, _) = rom(mapOf("ro.build.flavor" to "lineage_q25-userdebug"), sdk = 36)
        assertEquals(Rom.LINEAGE, r); assertEquals(23, major)
    }

    @Test fun benosWinsOverGenericFota() {
        assertEquals(Rom.BENOS, rom(mapOf("ro.fota.version" to "Q25-BenOS_25.05.2026", "ro.fota.oem" to "hdf_MTK_14.0")).first)
    }

    @Test fun otherFotaIsZinwaHeuristic() {
        assertEquals(Rom.ZINWAOS, rom(mapOf("ro.fota.version" to "Q25_V1.0")).first)
    }

    @Test fun nothingKnownIsUnknown() {
        assertEquals(Rom.UNKNOWN, rom(emptyMap()).first)
    }

    // Props captured from the real Q25 on LineageOS 23 (2026-10-02): no ro.lineage.version /
    // ro.lineage.build.version at all, and a leftover ro.fota.version that must not win.
    @Test fun realQ25Lineage23() {
        val (r, major, _) = rom(
            mapOf("ro.build.flavor" to "lineage_Q25-userdebug", "ro.fota.version" to "Q25_26.03.2026"),
            mfr = "Zinwa", sdk = 36
        )
        assertEquals(Rom.LINEAGE, r); assertEquals(23, major)
    }
}
