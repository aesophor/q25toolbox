package com.kgr.q25toolbox.service

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.kgr.q25toolbox.modules.GestureSettings
import com.kgr.q25toolbox.modules.GestureSettings.Zone

/**
 * Custom edge gestures: thin TYPE_ACCESSIBILITY_OVERLAY strips on the side edges and the bottom edge, each
 * driving an [EdgeSwipe] recognizer. Phase 1 mapping: inward swipe on a side = Back; swipe up from the bottom =
 * Home; swipe up and hold = Recents (our overlay when one is selected).
 *
 * The strips take the touches that land on them (a tap there is not forwarded to the app underneath), which is
 * why they are thin and why each zone can be switched off on its own. They are not shown on the lockscreen or
 * with the screen off. Everything runs on the main thread; [reconcile] is idempotent and rebuilds from prefs.
 */
object GestureStripsController {

    enum class Action { BACK, HOME, RECENTS }

    private val main = Handler(Looper.getMainLooper())
    private val strips = ArrayList<View>()
    private var wm: WindowManager? = null

    /** Whether the strips may be up right now: screen on and keyguard gone. */
    private fun allowed(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return pm.isInteractive && !km.isKeyguardLocked
    }

    /** Rebuilds the strips from the saved settings (adds, resizes or removes them). */
    fun reconcile(service: Q25AccessibilityService) = main.post {
        removeAll()
        if (!allowed(service)) return@post
        val lateral = GestureSettings.get(service, Zone.LATERAL)
        val bottom = GestureSettings.get(service, Zone.BOTTOM)
        if (lateral.mode != GestureSettings.Mode.CUSTOM && bottom.mode != GestureSettings.Mode.CUSTOM) return@post

        val manager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = manager
        val bounds = manager.currentWindowMetrics.bounds
        val dm = service.resources.displayMetrics
        val dp = dm.density
        val tint = GestureSettings.showStrips(service)

        if (lateral.mode == GestureSettings.Mode.CUSTOM) {
            val t = (lateral.thicknessDp * dp).toInt()
            val len = (bounds.height() * lateral.lengthPct / 100f).toInt()
            add(service, manager, EdgeSwipe.Edge.LEFT, lateral, t, len, Gravity.START or Gravity.CENTER_VERTICAL, tint)
            add(service, manager, EdgeSwipe.Edge.RIGHT, lateral, t, len, Gravity.END or Gravity.CENTER_VERTICAL, tint)
        }
        if (bottom.mode == GestureSettings.Mode.CUSTOM) {
            val t = (bottom.thicknessDp * dp).toInt()
            val len = (bounds.width() * bottom.lengthPct / 100f).toInt()
            add(service, manager, EdgeSwipe.Edge.BOTTOM, bottom, len, t, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, tint)
        }
    }

    fun hide() = main.post { removeAll() }

    private fun removeAll() {
        val manager = wm
        for (v in strips) try { manager?.removeView(v) } catch (_: Exception) { }
        strips.clear()
    }

    private fun add(
        service: Q25AccessibilityService, manager: WindowManager, edge: EdgeSwipe.Edge,
        cfg: GestureSettings.Config, w: Int, h: Int, gravity: Int, tint: Boolean,
    ) {
        val view = StripView(service, edge, cfg, tint)
        val lp = WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
            title = "Q25 gesture strip $edge"
        }
        try {
            manager.addView(view, lp)
            strips.add(view)
        } catch (t: Throwable) {
            android.util.Log.e("Q25Toolbox", "gesture strip $edge failed", t)
        }
    }

    private fun vibrator(ctx: Context): android.os.Vibrator =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator)

    fun hasAmplitudeControl(ctx: Context) = vibrator(ctx).hasAmplitudeControl()

    /** One pulse of [ms] at [strengthPct] (amplitude is ignored by motors without amplitude control). */
    fun vibrate(ctx: Context, ms: Int, strengthPct: Int) {
        if (ms <= 0) return
        try {
            val v = vibrator(ctx)
            val amp = if (v.hasAmplitudeControl()) (strengthPct * 255 / 100).coerceIn(1, 255)
                      else android.os.VibrationEffect.DEFAULT_AMPLITUDE
            v.vibrate(android.os.VibrationEffect.createOneShot(ms.toLong(), amp))
        } catch (_: Throwable) { }
    }

    /** Plays the configured tick, a pause, then the action pulse (the settings screen's "Test" button). */
    fun testVibration(ctx: Context) {
        val cfg = GestureSettings.vibration(ctx)
        vibrate(ctx, cfg.tickMs, cfg.strengthPct)
        main.postDelayed({ vibrate(ctx, cfg.actionMs, cfg.strengthPct) }, 1500)
    }

    /** Runs [a] through the service (it knows about our Recents overlay). */
    private fun perform(service: Q25AccessibilityService, a: Action) = service.performEdgeAction(a)

    @SuppressLint("ViewConstructor")
    private class StripView(
        private val service: Q25AccessibilityService,
        edge: EdgeSwipe.Edge,
        private val cfg: GestureSettings.Config,
        tint: Boolean,
    ) : View(service) {
        private val swipe = EdgeSwipe(edge, cfg.distanceDp * service.resources.displayMetrics.density,
            holdEnabled = edge == EdgeSwipe.Edge.BOTTOM)
        private val isBottom = edge == EdgeSwipe.Edge.BOTTOM
        private val holdTimer = Runnable {
            if (swipe.onHoldElapsed()) { buzz(tick = false); perform(service, Action.RECENTS) }
        }

        init {
            // Invisible by default; the tint option makes the strip visible so the user can place it.
            setBackgroundColor(if (tint) Color.argb(90, 0, 150, 255) else Color.TRANSPARENT)
        }

        /** [ms] short = threshold tick, longer = action. Direct vibrator: the system haptic is inaudible on this motor. */
        private fun buzz(tick: Boolean) {
            if (!cfg.haptic) return
            val v = GestureSettings.vibration(service)
            vibrate(service, if (tick) v.tickMs else v.actionMs, v.strengthPct)
        }

        // Absolute screen coordinates: the view itself is tiny, and the finger leaves it during the swipe.
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> swipe.onDown(e.rawX, e.rawY)
                MotionEvent.ACTION_MOVE -> when (swipe.onMove(e.rawX, e.rawY)) {
                    EdgeSwipe.Result.CROSSED -> {
                        buzz(tick = true) // marks the distance: from here on, releasing completes the gesture
                        if (isBottom) postDelayed(holdTimer, GestureSettings.HOLD_MS)
                    }
                    EdgeSwipe.Result.CANCELLED -> removeCallbacks(holdTimer)
                    else -> {}
                }
                MotionEvent.ACTION_UP -> {
                    removeCallbacks(holdTimer)
                    if (swipe.onUp() == EdgeSwipe.Result.SWIPE) {
                        buzz(tick = false)
                        perform(service, if (isBottom) Action.HOME else Action.BACK)
                    }
                }
                MotionEvent.ACTION_CANCEL -> removeCallbacks(holdTimer)
            }
            return true
        }
    }
}
