package com.kgr.q25toolbox.service

import kotlin.math.abs

/**
 * Pure (Android-free) recognizer for one edge strip, so the gesture rules can be unit-tested.
 *
 * Progress is measured toward the screen interior: +x from the left edge, -x from the right edge, -y from the
 * bottom edge. A swipe counts once progress reaches [distancePx] AND it is mostly along the progress axis (the
 * cross-axis travel stays below [MAX_CROSS_RATIO] times the progress), so scrolling along an edge does not fire.
 * Falling back under half the distance cancels it again (the finger came back).
 *
 * Feed it down/move/up; the caller owns the hold timer (see [onHoldElapsed]).
 */
class EdgeSwipe(
    private val edge: Edge,
    private val distancePx: Float,
    /** Whether a held swipe is a distinct gesture (bottom edge: Recents). */
    private val holdEnabled: Boolean,
) {
    enum class Edge { LEFT, RIGHT, BOTTOM }

    /** What the caller should do after a touch event. */
    enum class Result { NONE, CROSSED, SWIPE, CANCELLED }

    private var x0 = 0f
    private var y0 = 0f
    private var crossed = false
    private var holdFired = false

    fun onDown(x: Float, y: Float) {
        x0 = x; y0 = y
        crossed = false; holdFired = false
    }

    /** [CROSSED] when the swipe just became valid (start the hold timer); [CANCELLED] if it was undone. */
    fun onMove(x: Float, y: Float): Result {
        if (holdFired) return Result.NONE
        val p = progress(x, y)
        val cross = abs(crossAxis(x, y))
        if (!crossed) {
            if (p >= distancePx && cross <= MAX_CROSS_RATIO * p) { crossed = true; return Result.CROSSED }
        } else if (p < distancePx / 2f) {
            crossed = false
            return Result.CANCELLED
        }
        return Result.NONE
    }

    /** True once if the hold timer fires while the swipe is still valid and a hold gesture exists. */
    fun onHoldElapsed(): Boolean {
        if (!holdEnabled || !crossed || holdFired) return false
        holdFired = true
        return true
    }

    /** [SWIPE] if releasing now completes a plain swipe (not already consumed as a hold). */
    fun onUp(): Result = if (crossed && !holdFired) Result.SWIPE else Result.NONE

    private fun progress(x: Float, y: Float) = when (edge) {
        Edge.LEFT -> x - x0
        Edge.RIGHT -> x0 - x
        Edge.BOTTOM -> y0 - y
    }

    private fun crossAxis(x: Float, y: Float) = when (edge) {
        Edge.LEFT, Edge.RIGHT -> y - y0
        Edge.BOTTOM -> x - x0
    }

    companion object {
        const val MAX_CROSS_RATIO = 1.5f
    }
}
