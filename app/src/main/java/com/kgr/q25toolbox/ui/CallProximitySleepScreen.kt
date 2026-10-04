package com.kgr.q25toolbox.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kgr.q25toolbox.R
import com.kgr.q25toolbox.modules.ProximitySensorController
import com.kgr.q25toolbox.service.Q25AccessibilityService
import com.kgr.q25toolbox.service.isQ25AccessibilityServiceEnabled

@Composable
fun CallProximitySleepScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(Q25AccessibilityService.PREFS, Context.MODE_PRIVATE) }
    var serviceEnabled by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(Q25AccessibilityService.KEY_CALL_PROXIMITY_SLEEP, false)) }
    val sensorFlow = remember(context) { ProximitySensorController.observeSensor(context) }
    val sensor by sensorFlow.collectAsState(initial = ProximitySensorController.SensorData())

    LaunchedEffect(Unit) { serviceEnabled = isQ25AccessibilityServiceEnabled(context) }

    ScreenScaffold(title = stringResource(Screen.CallProximitySleep.titleRes), onBack = onBack) {
        AccessibilityServiceBanner(serviceEnabled)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.call_prox_enable_label))
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    enabled = checked
                    prefs.edit().putBoolean(Q25AccessibilityService.KEY_CALL_PROXIMITY_SLEEP, checked).apply()
                }
            )
        }
        Text(stringResource(R.string.call_prox_desc), style = MaterialTheme.typography.bodySmall)

        DescriptionDivider()

        // Live reading, to check the sensor behaves before relying on it during a call.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.call_prox_sensor_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!sensor.isAvailable) {
                    Text(stringResource(R.string.prox_unavailable), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        if (sensor.isNear) stringResource(R.string.prox_state_near) else stringResource(R.string.prox_state_far),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.prox_distance, sensor.distanceCm),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
