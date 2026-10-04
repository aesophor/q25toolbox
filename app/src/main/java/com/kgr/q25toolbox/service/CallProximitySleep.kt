package com.kgr.q25toolbox.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import com.kgr.q25toolbox.core.RootShell
import java.util.concurrent.ExecutorService

/**
 * Turns the screen off when the phone is held to the ear during a call, and back on when it is moved away.
 *
 * Replaces the system's own handling on ROMs where it does not work: the dialer takes the proximity wake lock
 * and the sensor flips correctly, yet the screen stays lit against the cheek (a pocket-dial and accidental
 * emergency-call risk). Idea and behaviour follow Key2 Toolbox's "Call Proximity Sleep", but event-driven
 * instead of a root loop polling `dumpsys` every few seconds:
 *
 *  - the call is detected from the audio mode ([AudioManager.OnModeChangedListener], no permission needed);
 *  - the proximity sensor is registered only while a call is on, and only "near" held for [NEAR_DEBOUNCE_MS]
 *    counts (a hand passing over the sensor does not blank the screen);
 *  - it only acts on the handset earpiece: speakerphone, Bluetooth and wired audio leave the screen alone;
 *  - sleep and wake are injected as KEYCODE_SLEEP / KEYCODE_WAKEUP through the root shell (off the main
 *    thread). WAKEUP only ever wakes, so it can never flip a screen the user has just lit back off.
 *  - whatever it put to sleep it also wakes: when the sensor reports "far", when the call ends, and when the
 *    service stops, so the screen is never left dark.
 */
@RequiresApi(Build.VERSION_CODES.S)
class CallProximitySleep(private val context: Context, private val io: ExecutorService) {

    private companion object {
        const val TAG = "Q25Toolbox"
        const val NEAR_DEBOUNCE_MS = 250L
        const val KEYCODE_SLEEP = 223
        const val KEYCODE_WAKEUP = 224
    }

    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val proximity: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var started = false
    private var inCall = false
    private var sensing = false
    private var near = false
    private var forcedAsleep = false

    private val modeListener = AudioManager.OnModeChangedListener { mode -> handler.post { onMode(mode) } }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val max = proximity?.maximumRange ?: return
            val nowNear = (event.values.firstOrNull() ?: return) < max
            if (nowNear == near) return
            near = nowNear
            Log.d(TAG, "CallProx: sensor ${if (near) "NEAR" else "far"} (inCall=$inCall)")
            handler.removeCallbacks(sleepCheck)
            if (near) handler.postDelayed(sleepCheck, NEAR_DEBOUNCE_MS) else wakeIfForced("sensor far")
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val sleepCheck = Runnable {
        if (!near || !inCall || forcedAsleep) return@Runnable
        if (!onEarpiece()) { Log.d(TAG, "CallProx: near but not on the earpiece, leaving the screen on"); return@Runnable }
        forcedAsleep = true
        Log.d(TAG, "CallProx: sleep")
        io.execute { RootShell.run("input keyevent $KEYCODE_SLEEP") }
    }

    /** Handset audio only: no speakerphone, Bluetooth SCO or wired headset. */
    @Suppress("DEPRECATION")
    private fun onEarpiece(): Boolean =
        audio != null && !audio.isSpeakerphoneOn && !audio.isBluetoothScoOn && !audio.isWiredHeadsetOn

    fun start() {
        if (started || audio == null || proximity == null) {
            if (proximity == null) Log.d(TAG, "CallProx: no proximity sensor, not starting")
            return
        }
        started = true
        audio.addOnModeChangedListener(context.mainExecutor, modeListener)
        onMode(audio.mode)
        Log.d(TAG, "CallProx: started (mode=${audio.mode})")
    }

    fun stop() {
        if (!started) return
        started = false
        audio?.removeOnModeChangedListener(modeListener)
        handler.removeCallbacks(sleepCheck)
        setSensing(false)
        inCall = false
        wakeIfForced("stopped")
    }

    private fun onMode(mode: Int) {
        val nowInCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        if (nowInCall == inCall) return
        inCall = nowInCall
        Log.d(TAG, "CallProx: audio mode $mode -> inCall=$inCall")
        setSensing(nowInCall)
        if (!nowInCall) { handler.removeCallbacks(sleepCheck); wakeIfForced("call ended") }
    }

    private fun setSensing(on: Boolean) {
        if (on == sensing) return
        sensing = on
        if (on) {
            near = false
            sensors?.registerListener(sensorListener, proximity, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            sensors?.unregisterListener(sensorListener)
            near = false
        }
    }

    private fun wakeIfForced(reason: String) {
        if (!forcedAsleep) return
        forcedAsleep = false
        Log.d(TAG, "CallProx: wake ($reason)")
        io.execute { RootShell.run("input keyevent $KEYCODE_WAKEUP") }
    }
}
