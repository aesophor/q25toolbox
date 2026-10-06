package com.kgr.q25toolbox.modules

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.DynamicsProcessing
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Fine media volume: the volume keys move the music stream in sub-steps of the system's 15.
 *
 * The system index can only be changed in whole steps (ro.config.media_vol_steps needs root and a
 * reboot), so each step is split by a global DynamicsProcessing input gain on audio session 0:
 * fine level L with [subdivisions] sub-steps per index plays at index ceil(L / sub), attenuated
 * by the fraction of the dB gap down to the index below. The gap comes from the stream's volume
 * curve for the current output; when the curve is flat (Bluetooth absolute volume, where the
 * headset applies the volume) a fixed estimate is used.
 *
 * The level resyncs to whole steps whenever the index was changed elsewhere (the system slider,
 * another app) or the output device changes.
 */
class FineVolumeController(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "Q25Toolbox"
        private const val STREAM = AudioManager.STREAM_MUSIC
        // dB gap per index when the volume curve gives none (absolute volume) or a step is -inf.
        private const val FALLBACK_GAP_DB = 3f
        private const val MAX_GAP_DB = 12f
        // How long the system volume dialog stays up after the last change (SystemUI default).
        private const val HUD_MS = 3000L
        // The system dialog slides in from the right edge after the volume change, and its reported
        // bounds move in coarse jumps (the same value can repeat mid-slide). It is sampled at this
        // interval for LOCATE_MAX_MS, and the leftmost position seen is its position at rest.
        private const val LOCATE_INTERVAL_MS = 50L
        private const val LOCATE_MAX_MS = 1000L
        // Holding a key: the filtered volume keys arrive without auto-repeats, so repeat here.
        private const val REPEAT_DELAY_MS = 400L
        private const val REPEAT_INTERVAL_MS = 60L
        // After the mute key, wait for the system to apply the mute before reading it.
        private const val MUTE_HUD_DELAY_MS = 100L
        const val DEFAULT_SUBDIVISIONS = 4
    }

    // Lazy: the controller is built with the service, before its context is attached.
    private val am by lazy { service.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val handler = Handler(Looper.getMainLooper())

    private var active = false
    private var subdivisions = DEFAULT_SUBDIVISIONS
    private var dp: DynamicsProcessing? = null
    private var level = -1          // fine level, -1 = resync from the system index on next press
    private var appliedIndex = -1   // the index this controller last set
    private var gainDb = 0f
    private var repeatDir = 0

    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (repeatDir == 0) return
            step(repeatDir)
            handler.postDelayed(this, REPEAT_INTERVAL_MS)
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = resync()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = resync()
    }

    /** Start or stop handling the volume keys. Main thread. */
    fun setActive(on: Boolean, subdivisions: Int) {
        this.subdivisions = subdivisions.coerceIn(2, 10)
        if (on == active) {
            if (on) resync()
            return
        }
        active = on
        if (on) {
            am.registerAudioDeviceCallback(deviceCallback, handler)
            level = -1
        } else {
            am.unregisterAudioDeviceCallback(deviceCallback)
            stopRepeat()
            handler.removeCallbacks(muteHudRunnable)
            releaseEffect()
            hideHud()
        }
    }

    /** Returns true when the key was consumed. */
    fun onKeyEvent(e: KeyEvent): Boolean {
        if (!active) return false
        if (e.keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            // The system toggles the mute itself (on the press); refresh the HUD once it has.
            if (e.action == KeyEvent.ACTION_UP && am.mode == AudioManager.MODE_NORMAL) {
                handler.removeCallbacks(muteHudRunnable)
                handler.postDelayed(muteHudRunnable, MUTE_HUD_DELAY_MS)
            }
            return false
        }
        val dir = when (e.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> 1
            KeyEvent.KEYCODE_VOLUME_DOWN -> -1
            else -> return false
        }
        // Calls, ringing and alarms keep the system behaviour (call / ring / alarm volume).
        if (am.mode != AudioManager.MODE_NORMAL) {
            stopRepeat()
            return false
        }
        when {
            e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 -> {
                stopRepeat()
                step(dir)
                repeatDir = dir
                handler.postDelayed(repeatRunnable, REPEAT_DELAY_MS)
            }
            e.action == KeyEvent.ACTION_UP && dir == repeatDir -> stopRepeat()
            // System auto-repeats, if a build sends them, are covered by our own repeat.
        }
        return true
    }

    private fun stopRepeat() {
        repeatDir = 0
        handler.removeCallbacks(repeatRunnable)
    }

    private val muteHudRunnable = Runnable {
        am.adjustStreamVolume(STREAM, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
        showHud(currentPercent())
    }

    /** The HUD value: 0 while muted, otherwise the fine level (whole steps if it is not known). */
    private fun currentPercent(): Int {
        if (am.isStreamMute(STREAM)) return 0
        val sub = subdivisions
        val max = am.getStreamMaxVolume(STREAM) * sub
        val index = am.getStreamVolume(STREAM)
        val l = if (level >= 0 && index == appliedIndex) level else index * sub
        return (l * 100f / max).roundToInt()
    }

    private fun step(dir: Int) {
        val sub = subdivisions
        val max = am.getStreamMaxVolume(STREAM)
        // getStreamVolume() reads 0 while muted, which would look like a change made elsewhere.
        // Like the system: down keeps the mute, up unmutes back to the previous level and raises it.
        if (am.isStreamMute(STREAM)) {
            if (dir < 0) {
                am.adjustStreamVolume(STREAM, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                showHud(0)
                return
            }
            am.adjustStreamVolume(STREAM, AudioManager.ADJUST_UNMUTE, 0)
        }
        val index = am.getStreamVolume(STREAM)
        if (level < 0 || index != appliedIndex) level = index * sub
        level = (level + dir).coerceIn(0, max * sub)

        val target = (level + sub - 1) / sub   // ceil
        val frac = level - (target - 1) * sub  // 1..sub sub-steps above the index below
        gainDb = if (target == 0 || frac == sub) 0f else -gapDb(target) * (sub - frac) / sub

        // FLAG_SHOW_UI even when the index stays: the system slider shows on every press, and the
        // percentage HUD above it shows the sub-steps the slider cannot.
        am.setStreamVolume(STREAM, target, AudioManager.FLAG_SHOW_UI)
        appliedIndex = target
        applyGain(gainDb)
        showHud((level * 100f / (max * sub)).roundToInt())
    }

    /** dB between [index] and [index] - 1 on the current output. */
    private fun gapDb(index: Int): Float {
        val type = currentDeviceType()
        return try {
            val hi = am.getStreamVolumeDb(STREAM, index, type)
            val lo = am.getStreamVolumeDb(STREAM, index - 1, type)
            val gap = hi - lo
            if (gap.isNaN() || gap < 0.5f) FALLBACK_GAP_DB else gap.coerceAtMost(MAX_GAP_DB)
        } catch (t: Throwable) {
            FALLBACK_GAP_DB
        }
    }

    private fun currentDeviceType(): Int {
        val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val prefer = intArrayOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
        )
        for (t in prefer) if (outs.any { it.type == t }) return t
        return AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
    }

    private fun applyGain(db: Float) {
        if (db == 0f) {
            dp?.let { try { it.setInputGainAllChannelsTo(0f) } catch (_: Throwable) { } }
            return
        }
        val fx = dp ?: createEffect() ?: return
        try {
            fx.setInputGainAllChannelsTo(db)
        } catch (t: Throwable) {
            Log.w(TAG, "fine volume: set gain failed", t)
        }
    }

    private fun createEffect(): DynamicsProcessing? = try {
        val cfg = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
            false, 0, false, 0, false, 0, false,
        ).build()
        // Priority above default so a player's own session effects cannot take session 0 from us.
        DynamicsProcessing(Int.MAX_VALUE, 0, cfg).also {
            it.enabled = true
            dp = it
        }
    } catch (t: Throwable) {
        Log.w(TAG, "fine volume: global DynamicsProcessing unavailable", t)
        null
    }

    private fun releaseEffect() {
        dp?.let { try { it.release() } catch (_: Throwable) { } }
        dp = null
        gainDb = 0f
    }

    /** Snap back to whole steps: the index changed elsewhere or the output device changed. */
    private fun resync() {
        level = -1
        appliedIndex = -1
        if (gainDb != 0f) applyGain(0f)
        gainDb = 0f
    }

    // ---------------------------------------------------------------- HUD

    private var hud: TextView? = null
    private var hudLp: WindowManager.LayoutParams? = null
    private var sliderBounds: Rect? = null   // system volume dialog at rest, screen coordinates
    private var locateUntil = 0L
    private val hideHudRunnable = Runnable { hideHud() }
    private val locateRunnable = object : Runnable {
        override fun run() {
            sampleSlider()
            if (SystemClock.uptimeMillis() < locateUntil) {
                handler.postDelayed(this, LOCATE_INTERVAL_MS)
            }
        }
    }

    private val wm by lazy { service.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private fun px(dp: Float) = (dp * service.resources.displayMetrics.density).roundToInt()

    /** Percentage label right above the system volume slider. */
    private fun showHud(pct: Int) {
        val v = hud ?: createHud() ?: return
        v.text = "$pct%"
        placeHud()
        // Keep sampling while the dialog slides in; the position at rest is known from earlier presses.
        locateUntil = SystemClock.uptimeMillis() + LOCATE_MAX_MS
        handler.removeCallbacks(locateRunnable)
        locateRunnable.run()
        handler.removeCallbacks(hideHudRunnable)
        handler.postDelayed(hideHudRunnable, HUD_MS)
    }

    private fun createHud(): TextView? {
        val tv = TextView(service).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xE62B2B30.toInt())
                cornerRadius = px(14f).toFloat()
            }
            setPadding(px(10f), px(4f), px(10f), px(4f))
            visibility = android.view.View.INVISIBLE  // until the slider has been found
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        return try {
            wm.addView(tv, lp)
            hud = tv
            hudLp = lp
            tv
        } catch (t: Throwable) {
            Log.w(TAG, "fine volume: HUD failed", t)
            null
        }
    }

    /** Track the dialog's resting position: the leftmost seen, unless its size or height changed. */
    private fun sampleSlider() {
        val now = findSlider() ?: return
        val rest = sliderBounds
        if (rest == null || now.top != rest.top || now.height() != rest.height() || now.left < rest.left) {
            sliderBounds = now
            placeHud()
        }
    }

    /** Centre the HUD just above the system volume dialog where it last came to rest. */
    private fun placeHud() {
        val v = hud ?: return
        val lp = hudLp ?: return
        val b = sliderBounds ?: return
        // Size from the font (top/bottom: TextView includes font padding), not v.measure(): measuring the live view re-lays its text out at
        // the natural width, and with no real layout pass after it the text ends up off-centre.
        val fm = v.paint.fontMetricsInt
        val h = fm.bottom - fm.top + v.paddingTop + v.paddingBottom
        val w = maxOf(b.width(), v.paint.measureText("100%").roundToInt() + v.paddingLeft + v.paddingRight)
        val x = b.centerX() - w / 2
        val y = maxOf(0, b.top - h - px(6f))
        if (lp.x != x || lp.y != y || lp.width != w || lp.height != h || v.visibility != android.view.View.VISIBLE) {
            lp.x = x
            lp.y = y
            lp.width = w
            lp.height = h
            v.visibility = android.view.View.VISIBLE
            try { wm.updateViewLayout(v, lp) } catch (_: Throwable) { }
        }
    }

    /**
     * The system volume dialog's window. It has no stable id across locales, so it is found by
     * shape: a SystemUI (TYPE_SYSTEM) window, taller than wide, narrow, and in the screen's right half.
     */
    private fun findSlider(): Rect? {
        val screenW = service.resources.displayMetrics.widthPixels
        val windows = try { service.windows } catch (_: Throwable) { return null }
        val r = Rect()
        for (w in windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_SYSTEM) continue
            w.getBoundsInScreen(r)
            if (r.height() > r.width() && r.width() < screenW / 3 && r.centerX() > screenW / 2) return Rect(r)
        }
        return null
    }

    private fun hideHud() {
        handler.removeCallbacks(hideHudRunnable)
        handler.removeCallbacks(locateRunnable)
        val v = hud ?: return
        hud = null
        hudLp = null
        try {
            wm.removeView(v)
        } catch (_: Throwable) { }
    }
}
