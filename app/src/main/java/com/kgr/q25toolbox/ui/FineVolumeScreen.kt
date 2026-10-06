package com.kgr.q25toolbox.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kgr.q25toolbox.R
import com.kgr.q25toolbox.modules.FineVolumeController
import com.kgr.q25toolbox.service.Q25AccessibilityService
import com.kgr.q25toolbox.service.isQ25AccessibilityServiceEnabled

@Composable
fun FineVolumeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(Q25AccessibilityService.PREFS, Context.MODE_PRIVATE)
    }

    var serviceEnabled by remember { mutableStateOf(false) }
    var enabled by remember {
        mutableStateOf(prefs.getBoolean(Q25AccessibilityService.KEY_FINE_VOLUME, false))
    }
    var steps by remember {
        mutableIntStateOf(prefs.getInt(Q25AccessibilityService.KEY_FINE_VOLUME_STEPS, FineVolumeController.DEFAULT_SUBDIVISIONS))
    }

    LaunchedEffect(Unit) {
        serviceEnabled = isQ25AccessibilityServiceEnabled(context)
    }

    ScreenScaffold(title = Screen.FineVolume.title, onBack = onBack) {
        AccessibilityServiceBanner(serviceEnabled)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.fine_volume_enabled_label))
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    enabled = checked
                    prefs.edit().putBoolean(Q25AccessibilityService.KEY_FINE_VOLUME, checked).apply()
                }
            )
        }

        Text(stringResource(R.string.fine_volume_steps_label, steps * 15))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in 2..5) {
                FilterChip(
                    selected = steps == n,
                    onClick = {
                        steps = n
                        prefs.edit().putInt(Q25AccessibilityService.KEY_FINE_VOLUME_STEPS, n).apply()
                    },
                    label = { Text("×$n") }
                )
            }
        }

        DescriptionDivider()
        Text(
            stringResource(R.string.fine_volume_desc),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
