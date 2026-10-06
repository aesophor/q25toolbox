package com.kgr.q25toolbox.modules

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.graphics.Path
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent

/**
 * Per-app cursor taps: replays trackpad cursor clicks as touchscreen gestures, for apps that
 * ignore mouse input (Unity games such as MapleStory M only read touches).
 *
 * While a selected app is in front, the accessibility service routes mouse events to itself
 * instead of the app (AccessibilityServiceInfo.motionEventSources, Android 14+). A primary-button
 * press / drag / release becomes one continuous touch stroke built from chained
 * StrokeDescriptions, so taps, long presses and drags all work. Right-click is Back.
 */
class CursorTapController(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "Q25Toolbox"

        // Duration of each stroke segment. The pointer stays down between segments (willContinue).
        private const val SEGMENT_MS = 10L
        // Minimum spacing between move segments, to avoid flooding the gesture injector.
        private const val MOVE_INTERVAL_MS = 16L

        /** Mouse event interception needs Android 14 (API 34). */
        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }

    private var active = false
    private var stroke: StrokeDescription? = null
    private var strokeX = 0f
    private var strokeY = 0f
    private var lastDispatchMs = 0L

    /** Start or stop taking over mouse events. Main thread. */
    fun setActive(on: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || on == active) return
        active = on
        stroke = null
        val info = service.serviceInfo ?: return
        info.motionEventSources = if (on) InputDevice.SOURCE_MOUSE else 0
        service.serviceInfo = info
    }

    fun onMotionEvent(e: MotionEvent) {
        val x = e.x
        val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS ->
                if (e.actionButton == MotionEvent.BUTTON_SECONDARY) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                }
            MotionEvent.ACTION_DOWN -> {
                if (e.buttonState and MotionEvent.BUTTON_PRIMARY == 0) return
                val p = Path().apply { moveTo(x, y) }
                stroke = StrokeDescription(p, 0, SEGMENT_MS, true)
                strokeX = x
                strokeY = y
                dispatch()
            }
            MotionEvent.ACTION_MOVE -> {
                if (stroke == null) return
                if (SystemClock.uptimeMillis() - lastDispatchMs < MOVE_INTERVAL_MS) return
                continueStroke(x, y, true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (stroke == null) return
                continueStroke(x, y, false)
                stroke = null
            }
        }
    }

    private fun continueStroke(x: Float, y: Float, willContinue: Boolean) {
        val prev = stroke ?: return
        // A continuation must start exactly where the previous segment ended.
        val p = Path().apply {
            moveTo(strokeX, strokeY)
            lineTo(x, y)
        }
        stroke = prev.continueStroke(p, 0, SEGMENT_MS, willContinue)
        strokeX = x
        strokeY = y
        dispatch()
    }

    private fun dispatch() {
        val sent = stroke ?: return
        lastDispatchMs = SystemClock.uptimeMillis()
        val gesture = GestureDescription.Builder().addStroke(sent).build()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCancelled(gestureDescription: GestureDescription?) {
                // Replacing an in-flight segment with its continuation cancels it; only a
                // cancellation of the newest segment means the stroke is gone.
                if (stroke === sent) {
                    Log.w(TAG, "cursor tap gesture cancelled")
                    stroke = null
                }
            }
        }, null)
    }
}
