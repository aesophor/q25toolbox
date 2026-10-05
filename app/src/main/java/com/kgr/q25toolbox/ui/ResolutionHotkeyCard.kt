package com.kgr.q25toolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kgr.q25toolbox.R
import com.kgr.q25toolbox.modules.AppScalingController
import com.kgr.q25toolbox.modules.AppScalingController.Res
import com.kgr.q25toolbox.modules.ResolutionHotkey

/**
 * The global resolution hotkey: switch, key combination (modifiers plus a letter or digit) and the ordered list of
 * resolutions it steps through before returning to the default. [onCustom] opens the W x H dialog of the screen.
 */
@Composable
fun ResolutionHotkeyCard(onCustom: () -> Unit, listVersion: Int) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ResolutionHotkey.isEnabled(context)) }
    var combo by remember { mutableStateOf(ResolutionHotkey.combo(context)) }
    var list by remember(listVersion) { mutableStateOf(ResolutionHotkey.list(context)) }
    var menu by remember { mutableStateOf(false) }
    var press by remember { mutableStateOf(ResolutionHotkey.press(context)) }
    var vibrate by remember { mutableStateOf(ResolutionHotkey.vibrate(context)) }
    var tapPass by remember { mutableStateOf(ResolutionHotkey.tapPass(context)) }

    fun saveCombo(c: ResolutionHotkey.Combo) { combo = c; ResolutionHotkey.setCombo(context, c) }
    fun saveList(l: List<Res>) { list = l; ResolutionHotkey.setList(context, l) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = enabled, onCheckedChange = { enabled = it; ResolutionHotkey.setEnabled(context, it) })
                Text(stringResource(R.string.res_hotkey_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            Text(stringResource(R.string.res_hotkey_desc), style = MaterialTheme.typography.bodySmall)
            if (enabled) {
                Text(stringResource(R.string.res_hotkey_combo, ResolutionHotkey.label(combo)), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    for ((bit, name) in ResolutionHotkey.MODIFIERS) {
                        val on = combo.mods and bit != 0
                        FilterChip(selected = on, label = { Text(name) }, onClick = {
                            val m = if (on) combo.mods and bit.inv() else combo.mods or bit
                            if (m != 0) saveCombo(combo.copy(mods = m)) // a combo needs at least one modifier key
                        })
                    }
                    OutlinedTextField(
                        value = ResolutionHotkey.charFor(combo.keyCode)?.toString() ?: "",
                        onValueChange = { t ->
                            t.lastOrNull()?.let { ResolutionHotkey.keyCodeFor(it) }?.let { saveCombo(combo.copy(keyCode = it)) }
                        },
                        singleLine = true, modifier = Modifier.width(72.dp), label = { Text(stringResource(R.string.res_hotkey_key)) },
                    )
                }
                Text(stringResource(R.string.res_hotkey_press_title), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((p, label) in listOf(ResolutionHotkey.Press.SHORT to R.string.res_hotkey_press_short,
                        ResolutionHotkey.Press.LONG to R.string.res_hotkey_press_long)) {
                        FilterChip(selected = press == p, label = { Text(stringResource(label)) },
                            onClick = { press = p; ResolutionHotkey.setPress(context, p) })
                    }
                }
                if (press == ResolutionHotkey.Press.LONG) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(checked = tapPass, onCheckedChange = { tapPass = it; ResolutionHotkey.setTapPass(context, it) })
                        Text(stringResource(R.string.res_hotkey_tap_pass), modifier = Modifier.weight(1f))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it; ResolutionHotkey.setVibrate(context, it) })
                    Text(stringResource(R.string.res_hotkey_vibrate))
                }
                Text(stringResource(R.string.res_hotkey_list), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    list.forEachIndexed { i, r ->
                        InputChip(selected = false, onClick = { saveList(list - r) },
                            label = { Text("${i + 1}. ${r.w}×${r.h}  ✕") })
                    }
                    Box {
                        OutlinedButton(onClick = { menu = true }) { Text(stringResource(R.string.res_hotkey_add)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            AppScalingController.PRESETS.filter { !it.isNative && it !in list }.forEach { p ->
                                DropdownMenuItem(text = { Text(AppScalingController.label(p)) },
                                    onClick = { menu = false; saveList(list + p) })
                            }
                            DropdownMenuItem(text = { Text("Custom…") }, onClick = { menu = false; onCustom() })
                        }
                    }
                }
                if (list.isEmpty()) Text(stringResource(R.string.res_hotkey_empty), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.res_hotkey_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
