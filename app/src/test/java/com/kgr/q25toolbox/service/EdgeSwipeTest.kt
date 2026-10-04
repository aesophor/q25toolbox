package com.kgr.q25toolbox.service

import com.kgr.q25toolbox.service.EdgeSwipe.Edge
import com.kgr.q25toolbox.service.EdgeSwipe.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeSwipeTest {

    @Test fun leftEdgeInwardSwipeCompletes() {
        val s = EdgeSwipe(Edge.LEFT, 50f, holdEnabled = false)
        s.onDown(5f, 300f)
        assertEquals(Result.NONE, s.onMove(30f, 305f))
        assertEquals(Result.CROSSED, s.onMove(60f, 310f))
        assertEquals(Result.SWIPE, s.onUp())
    }

    @Test fun rightEdgeNeedsLeftwardMotion() {
        val s = EdgeSwipe(Edge.RIGHT, 50f, false)
        s.onDown(715f, 300f)
        assertEquals(Result.NONE, s.onMove(750f, 300f)) // outward does nothing
        assertEquals(Result.CROSSED, s.onMove(650f, 300f))
        assertEquals(Result.SWIPE, s.onUp())
    }

    @Test fun bottomEdgeNeedsUpwardMotion() {
        val s = EdgeSwipe(Edge.BOTTOM, 50f, true)
        s.onDown(360f, 715f)
        assertEquals(Result.NONE, s.onMove(360f, 760f))
        assertEquals(Result.CROSSED, s.onMove(365f, 650f))
        assertEquals(Result.SWIPE, s.onUp())
    }

    @Test fun shortSwipeIsIgnored() {
        val s = EdgeSwipe(Edge.LEFT, 50f, false)
        s.onDown(5f, 300f)
        s.onMove(40f, 300f)
        assertEquals(Result.NONE, s.onUp())
    }

    @Test fun scrollingAlongTheEdgeDoesNotFire() {
        val s = EdgeSwipe(Edge.LEFT, 50f, false)
        s.onDown(5f, 300f)
        // 60 px inward but 200 px along the edge: cross-axis ratio far above the limit.
        assertEquals(Result.NONE, s.onMove(65f, 500f))
        assertEquals(Result.NONE, s.onUp())
    }

    @Test fun comingBackCancelsTheSwipe() {
        val s = EdgeSwipe(Edge.LEFT, 50f, false)
        s.onDown(5f, 300f)
        assertEquals(Result.CROSSED, s.onMove(70f, 300f))
        assertEquals(Result.CANCELLED, s.onMove(10f, 300f))
        assertEquals(Result.NONE, s.onUp())
    }

    @Test fun holdFiresOnceAndSuppressesTheSwipeOnRelease() {
        val s = EdgeSwipe(Edge.BOTTOM, 50f, true)
        s.onDown(360f, 715f)
        s.onMove(360f, 640f)
        assertTrue(s.onHoldElapsed())
        assertFalse(s.onHoldElapsed())
        assertEquals(Result.NONE, s.onUp())
    }

    @Test fun holdBeforeCrossingDoesNothing() {
        val s = EdgeSwipe(Edge.BOTTOM, 50f, true)
        s.onDown(360f, 715f)
        s.onMove(360f, 700f)
        assertFalse(s.onHoldElapsed())
    }

    @Test fun holdDisabledOnLateralEdges() {
        val s = EdgeSwipe(Edge.LEFT, 50f, false)
        s.onDown(5f, 300f)
        s.onMove(70f, 300f)
        assertFalse(s.onHoldElapsed())
        assertEquals(Result.SWIPE, s.onUp())
    }

    @Test fun newGestureResetsState() {
        val s = EdgeSwipe(Edge.LEFT, 50f, false)
        s.onDown(5f, 300f); s.onMove(70f, 300f)
        s.onDown(5f, 300f)
        assertEquals(Result.NONE, s.onUp())
    }
}
