package com.kgr.q25toolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kgr.q25toolbox.R
import com.kgr.q25toolbox.modules.GestureSettings
import com.kgr.q25toolbox.modules.NativeBottomGesture
import com.kgr.q25toolbox.modules.RecentsTweaksController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kgr.q25toolbox.modules.GestureSettings.Mode
import com.kgr.q25toolbox.modules.GestureSettings.Zone
import com.kgr.q25toolbox.service.GestureStripsController
import com.kgr.q25toolbox.service.isQ25AccessibilityServiceEnabled

/** Edge gestures: per-zone Off / Custom, with strip size and sensitivity. Applied live by the service. */
@Composable
fun GesturesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(true) }
    var showStrips by remember { mutableStateOf(GestureSettings.showStrips(context)) }
    LaunchedEffect(Unit) { serviceEnabled = isQ25AccessibilityServiceEnabled(context) }

    ScreenScaffold(title = Screen.Gestures.title, onBack = onBack) {
        AccessibilityServiceBanner(serviceEnabled)
        ZoneCard(Zone.LATERAL, R.string.gestures_zone_lateral, R.string.gestures_lateral_desc)
        ZoneCard(Zone.BOTTOM, R.string.gestures_zone_bottom, R.string.gestures_bottom_desc)
        NativeGestureStatus()
        VibrationCard()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = showStrips, onCheckedChange = {
                showStrips = it
                GestureSettings.setShowStrips(context, it)
            })
            Text(stringResource(R.string.gestures_show_strips))
        }
        DescriptionDivider()
        Text(stringResource(R.string.gestures_description), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ZoneCard(zone: Zone, titleRes: Int, descRes: Int) {
    val context = LocalContext.current
    var cfg by remember { mutableStateOf(GestureSettings.get(context, zone)) }
    fun update(c: GestureSettings.Config) { cfg = c; GestureSettings.set(context, zone, c) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(descRes), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = cfg.mode == Mode.OFF, onClick = { update(cfg.copy(mode = Mode.OFF)) },
                    label = { Text(stringResource(R.string.gestures_mode_off)) })
                FilterChip(selected = cfg.mode == Mode.CUSTOM, onClick = { update(cfg.copy(mode = Mode.CUSTOM)) },
                    label = { Text(stringResource(R.string.gestures_mode_custom)) })
            }
            if (cfg.mode == Mode.CUSTOM) {
                LabeledSlider(R.string.gestures_thickness, cfg.thicknessDp, GestureSettings.THICKNESS_RANGE, "dp") {
                    update(cfg.copy(thicknessDp = it))
                }
                LabeledSlider(R.string.gestures_length, cfg.lengthPct, GestureSettings.LENGTH_RANGE, "%") {
                    update(cfg.copy(lengthPct = it))
                }
                LabeledSlider(R.string.gestures_distance, cfg.distanceDp, GestureSettings.DISTANCE_RANGE, "dp") {
                    update(cfg.copy(distanceDp = it))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = cfg.haptic, onCheckedChange = { update(cfg.copy(haptic = it)) })
                    Text(stringResource(R.string.gestures_haptic))
                }
            }
        }
    }
}

/** Slider that saves on release (each save rebuilds the strips, so not on every drag step). */
@Composable
private fun LabeledSlider(labelRes: Int, value: Int, range: ClosedFloatingPointRange<Float>, unit: String, onSave: (Int) -> Unit) {
    var live by remember(value) { mutableStateOf(value.toFloat()) }
    Column {
        // dp -> millimetres (160 dp per inch), so the value can be judged against a fingertip.
        val mm = if (unit == "dp") " (%.1f mm)".format(live / 160f * 25.4f) else ""
        Text("${stringResource(labelRes)}: ${live.toInt()} $unit$mm", style = MaterialTheme.typography.bodyMedium)
        Slider(value = live, valueRange = range, onValueChange = { live = it },
            onValueChangeFinished = { onSave(live.toInt()) })
    }
}

/** Whether the launcher hook that silences the native bottom swipe-up has run (needed for the bottom strip). */
@Composable
private fun NativeGestureStatus() {
    val context = LocalContext.current
    var bottomOn by remember { mutableStateOf(GestureSettings.get(context, Zone.BOTTOM).mode == Mode.CUSTOM) }
    var health by remember { mutableStateOf<RecentsTweaksController.HookHealth?>(null) }
    var check by remember { mutableIntStateOf(0) }
    LaunchedEffect(check) {
        bottomOn = GestureSettings.get(context, Zone.BOTTOM).mode == Mode.CUSTOM
        health = withContext(Dispatchers.IO) { NativeBottomGesture.hookHealth() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(
                if (health == RecentsTweaksController.HookHealth.OK) R.string.gestures_hook_ok
                else R.string.gestures_hook_unknown
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { check++ }) { Text(stringResource(R.string.recents_hook_recheck)) }
    }
}

/** Shared vibration feedback: tick at the threshold and pulse on the action, with a test button. */
@Composable
private fun VibrationCard() {
    val context = LocalContext.current
    var v by remember { mutableStateOf(GestureSettings.vibration(context)) }
    val amplitude = remember { GestureStripsController.hasAmplitudeControl(context) }
    fun update(n: GestureSettings.Vibration) { v = n; GestureSettings.setVibration(context, n) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.gestures_vibration), style = MaterialTheme.typography.titleMedium)
            LabeledSlider(R.string.gestures_vib_tick, v.tickMs, GestureSettings.VIB_TICK_RANGE, "ms") { update(v.copy(tickMs = it)) }
            LabeledSlider(R.string.gestures_vib_action, v.actionMs, GestureSettings.VIB_ACTION_RANGE, "ms") { update(v.copy(actionMs = it)) }
            if (amplitude) {
                LabeledSlider(R.string.gestures_vib_strength, v.strengthPct, GestureSettings.VIB_STRENGTH_RANGE, "%") { update(v.copy(strengthPct = it)) }
            } else {
                Text(stringResource(R.string.gestures_vib_no_amplitude), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { GestureStripsController.testVibration(context) }) {
                Text(stringResource(R.string.gestures_vib_test))
            }
        }
    }
}
