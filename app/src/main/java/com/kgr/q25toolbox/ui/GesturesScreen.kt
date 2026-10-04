package com.kgr.q25toolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.kgr.q25toolbox.service.GestureArrowView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.style.TextOverflow
import com.kgr.q25toolbox.service.EdgeSwipe
import java.util.Locale
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
    var picking by remember { mutableStateOf(false) }
    if (picking) { GestureExcludedAppsScreen(onBack = { picking = false }); return }
    val context = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(true) }
    var showStrips by remember { mutableStateOf(GestureSettings.showStrips(context)) }
    LaunchedEffect(Unit) { serviceEnabled = isQ25AccessibilityServiceEnabled(context) }

    ScreenScaffold(title = Screen.Gestures.title, onBack = onBack) {
        AccessibilityServiceBanner(serviceEnabled)
        // Copying one side onto the other rewrites that card's saved values: the tick makes it reload them.
        var tick by remember { mutableIntStateOf(0) }
        key(tick, 0) {
            ZoneCard(Zone.LEFT, R.string.gestures_zone_left, R.string.gestures_lateral_desc, R.string.gestures_copy_to_right) {
                GestureSettings.copyZone(context, Zone.LEFT, Zone.RIGHT); tick++
            }
        }
        key(tick, 1) {
            ZoneCard(Zone.RIGHT, R.string.gestures_zone_right, R.string.gestures_lateral_desc, R.string.gestures_copy_to_left) {
                GestureSettings.copyZone(context, Zone.RIGHT, Zone.LEFT); tick++
            }
        }
        ZoneCard(Zone.BOTTOM, R.string.gestures_zone_bottom, R.string.gestures_bottom_desc)
        NativeBottomCard()
        ArrowStyleCard()
        VibrationCard()
        ExcludedAppsRow { picking = true }
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
private fun ZoneCard(zone: Zone, titleRes: Int, descRes: Int, copyRes: Int = 0, onCopy: (() -> Unit)? = null) {
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = cfg.arrow, onCheckedChange = { update(cfg.copy(arrow = it)) })
                    Text(stringResource(R.string.gestures_arrow))
                }
                Text(stringResource(R.string.gestures_actions), style = MaterialTheme.typography.titleSmall)
                for (dir in GestureSettings.dirsOf(zone)) GestureRow(zone, dir)
                if (onCopy != null) OutlinedButton(onClick = onCopy) { Text(stringResource(copyRes)) }
            }
        }
    }
}

/** Slider that saves on release (each save rebuilds the strips, so not on every drag step). */
@Composable
private fun LabeledSlider(labelRes: Int, value: Int, range: ClosedFloatingPointRange<Float>, unit: String, showMm: Boolean = unit == "dp", onSave: (Int) -> Unit) {
    var live by remember(value) { mutableStateOf(value.toFloat()) }
    Column {
        // dp -> millimetres (160 dp per inch), so the value can be judged against a fingertip.
        val mm = if (showMm) " (%.1f mm)".format(live / 160f * 25.4f) else ""
        Text("${stringResource(labelRes)}: ${live.toInt()} $unit$mm", style = MaterialTheme.typography.bodyMedium)
        Slider(value = live, valueRange = range, onValueChange = { live = it },
            onValueChangeFinished = { onSave(live.toInt()) })
    }
}

/**
 * Option to switch off the launcher's native bottom swipe-up (independent of the bottom strip), with proof that the
 * launcher hook that does it is really running. Until the first root check returns the state is "checking".
 */
@Composable
private fun NativeBottomCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var off by remember { mutableStateOf(GestureSettings.nativeBottomOff(context)) }
    var health by remember { mutableStateOf<RecentsTweaksController.HookHealth?>(null) } // null = checking
    var check by remember { mutableIntStateOf(0) }
    LaunchedEffect(check) {
        health = null
        health = withContext(Dispatchers.IO) { NativeBottomGesture.hookHealth() }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = off, onCheckedChange = {
                    off = it
                    GestureSettings.setNativeBottomOff(context, it)
                    // Applied right away (root), not only when the accessibility service next notices the change.
                    scope.launch(Dispatchers.IO) { NativeBottomGesture.sync(context) }
                })
                Text(stringResource(R.string.gestures_native_off), modifier = Modifier.weight(1f))
            }
            Text(stringResource(R.string.gestures_native_off_desc), style = MaterialTheme.typography.bodySmall)
            if (off) {
                Text(
                    stringResource(when (health) {
                        null -> R.string.gestures_hook_checking
                        RecentsTweaksController.HookHealth.OK -> R.string.gestures_hook_ok
                        else -> R.string.gestures_hook_unknown
                    }),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { check++ }) { Text(stringResource(R.string.recents_hook_recheck)) }
            }
        }
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

private fun dirLabel(zone: Zone, dir: EdgeSwipe.Dir): Int = when {
    zone == Zone.BOTTOM -> R.string.gestures_dir_bottom_up
    dir == EdgeSwipe.Dir.STRAIGHT -> R.string.gestures_dir_straight
    dir == EdgeSwipe.Dir.DIAG_A -> R.string.gestures_dir_diag_up
    else -> R.string.gestures_dir_diag_down
}

private fun actionLabel(a: GestureSettings.Action): Int = when (a) {
    GestureSettings.Action.NONE -> R.string.gesture_action_none
    GestureSettings.Action.BACK -> R.string.gesture_action_back
    GestureSettings.Action.HOME -> R.string.gesture_action_home
    GestureSettings.Action.RECENTS -> R.string.gesture_action_recents
    GestureSettings.Action.NOTIFICATIONS -> R.string.gesture_action_notifications
    GestureSettings.Action.QUICK_SETTINGS -> R.string.gesture_action_quick_settings
    GestureSettings.Action.LOCK_SCREEN -> R.string.gesture_action_lock_screen
    GestureSettings.Action.SCREENSHOT -> R.string.gesture_action_screenshot
    GestureSettings.Action.POWER_MENU -> R.string.gesture_action_power_menu
    GestureSettings.Action.SPLIT_SCREEN -> R.string.gesture_action_split_screen
    GestureSettings.Action.FLASHLIGHT -> R.string.gesture_action_flashlight
    GestureSettings.Action.PREVIOUS_APP -> R.string.gesture_action_previous_app
}

/** One gesture: the action when released (swipe) and the action when held. */
@Composable
private fun GestureRow(zone: Zone, dir: EdgeSwipe.Dir) {
    val context = LocalContext.current
    var simple by remember { mutableStateOf(GestureSettings.binding(context, zone, dir, false)) }
    var held by remember { mutableStateOf(GestureSettings.binding(context, zone, dir, true)) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(dirLabel(zone, dir)), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionPicker(R.string.gestures_act_swipe, simple, Modifier.weight(1f)) {
                simple = it; GestureSettings.setBinding(context, zone, dir, false, it)
            }
            ActionPicker(R.string.gestures_act_hold, held, Modifier.weight(1f)) {
                held = it; GestureSettings.setBinding(context, zone, dir, true, it)
            }
        }
    }
}

@Composable
private fun ActionPicker(labelRes: Int, value: GestureSettings.Action, modifier: Modifier, onPick: (GestureSettings.Action) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(actionLabel(value)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (a in GestureSettings.Action.entries) {
                DropdownMenuItem(text = { Text(stringResource(actionLabel(a))) }, onClick = { open = false; onPick(a) })
            }
        }
    }
}

/** Entry that opens the picker of apps where the strips are switched off. */
@Composable
private fun ExcludedAppsRow(onOpen: () -> Unit) {
    val context = LocalContext.current
    val n = GestureSettings.excludedApps(context).size
    OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.gestures_excluded_apps, n))
    }
}

/** App picker for the exclusion list (same pattern as the per-app keyboard block). */
@Composable
fun GestureExcludedAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(GestureSettings.excludedApps(context)) }
    var showSystemApps by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(showSystemApps) {
        apps = null
        apps = withContext(Dispatchers.IO) { loadInstalledApps(context, showSystemApps, selected) }
    }
    fun persist(n: Set<String>) { selected = n; GestureSettings.setExcludedApps(context, n) }
    val filtered = remember(apps, query) {
        val q = query.trim().lowercase(Locale.ROOT)
        (apps ?: emptyList()).filter { q.isEmpty() || it.label.lowercase(Locale.ROOT).contains(q) || it.pkg.contains(q) }
    }
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, label = { Text(stringResource(R.string.common_search_apps)) })
        if (apps == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item(key = "_bar") {
                    AppListTopBar(title = Screen.Gestures.title, onBack = onBack, showSystemApps = showSystemApps,
                        onToggleShowSystemApps = { showSystemApps = it })
                }
                item(key = "_desc") {
                    Text(stringResource(R.string.gestures_excluded_desc), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(filtered, key = { it.pkg }) { app ->
                    val checked = app.pkg in selected
                    Row(
                        Modifier.fillMaxWidth()
                            .toggleable(value = checked, onValueChange = { on -> persist(if (on) selected + app.pkg else selected - app.pkg) })
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Column {
                            Text(app.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.pkg, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private val SWATCHES = listOf(
    0xFFFFFFFF, 0xFF202020, 0xFF2979FF, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835,
    0xFF43A047, 0xFF00ACC1, 0xFF8E24AA, 0xFFD81B60, 0xFF9E9E9E,
).map { it.toInt() }

/** Colour choice: a row of swatches plus a #RRGGBB field (applied once it parses). */
@Composable
private fun ColorRow(labelRes: Int, value: Int, onChange: (Int) -> Unit) {
    var hex by remember(value) { mutableStateOf("%06X".format(value and 0xFFFFFF)) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            for (c in SWATCHES) {
                val sel = (c and 0xFFFFFF) == (value and 0xFFFFFF)
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                        .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { onChange(c or 0xFF000000.toInt()) }
                )
            }
        }
        OutlinedTextField(
            value = hex, singleLine = true, modifier = Modifier.width(150.dp),
            prefix = { Text("#") },
            onValueChange = { t ->
                val clean = t.removePrefix("#").uppercase(Locale.ROOT).filter { it in "0123456789ABCDEF" }.take(6)
                hex = clean
                if (clean.length == 6) onChange(clean.toInt(16) or 0xFF000000.toInt())
            },
        )
    }
}

/** Look of the arrow overlay, with a live preview of its two states. */
@Composable
private fun ArrowStyleCard() {
    val context = LocalContext.current
    var st by remember { mutableStateOf(GestureSettings.arrowStyle(context)) }
    fun update(n: GestureSettings.ArrowStyle) { st = n; GestureSettings.setArrowStyle(context, n) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.gestures_arrow_style), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                for (armed in listOf(false, true)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AndroidView(
                            modifier = Modifier.size(96.dp),
                            factory = { GestureArrowView(it) },
                            update = { it.showPreview(st, armed) },
                        )
                        Text(stringResource(if (armed) R.string.gestures_arrow_preview_active else R.string.gestures_arrow_preview_inactive),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            ColorRow(R.string.gestures_arrow_color, st.arrowColor) { update(st.copy(arrowColor = it)) }
            ColorRow(R.string.gestures_arrow_inactive, st.inactiveColor) { update(st.copy(inactiveColor = it)) }
            ColorRow(R.string.gestures_arrow_active, st.activeColor) { update(st.copy(activeColor = it)) }
            LabeledSlider(R.string.gestures_arrow_size, st.sizeDp, GestureSettings.ARROW_SIZE_RANGE, "dp", showMm = false) { update(st.copy(sizeDp = it)) }
            LabeledSlider(R.string.gestures_arrow_travel, st.travelDp, GestureSettings.ARROW_TRAVEL_RANGE, "dp", showMm = false) { update(st.copy(travelDp = it)) }
            LabeledSlider(R.string.gestures_arrow_opacity, st.opacityPct, GestureSettings.ARROW_OPACITY_RANGE, "%") { update(st.copy(opacityPct = it)) }
            LabeledSlider(R.string.gestures_arrow_thickness, st.thicknessDp, GestureSettings.ARROW_THICKNESS_RANGE, "dp", showMm = false) { update(st.copy(thicknessDp = it)) }
            LabeledSlider(R.string.gestures_arrow_speed, st.speedPct, GestureSettings.ARROW_SPEED_RANGE, "%") { update(st.copy(speedPct = it)) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = st.showBadge, onCheckedChange = { update(st.copy(showBadge = it)) })
                Text(stringResource(R.string.gestures_arrow_badge))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = st.followTilt, onCheckedChange = { update(st.copy(followTilt = it)) })
                Text(stringResource(R.string.gestures_arrow_tilt))
            }
            OutlinedButton(onClick = { update(GestureSettings.ArrowStyle()) }) { Text(stringResource(R.string.gestures_arrow_reset)) }
        }
    }
}
