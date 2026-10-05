package com.kgr.q25toolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kgr.q25toolbox.R
import com.kgr.q25toolbox.modules.ImeCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** LineageOS only (see [availableOn]): keeps the BlackBerry keyboard on its suggestions strip. */
@Composable
fun ImeCompatScreen(onBack: () -> Unit) {
    ScreenScaffold(title = Screen.ImeCompat.title, onBack = onBack) {
        ImeCompatCard()
    }
}

@Composable
private fun ImeCompatCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(ImeCompat.isEnabled(context)) }
    var active by remember { mutableStateOf<Boolean?>(null) } // null = checking
    var check by remember { mutableIntStateOf(0) }
    LaunchedEffect(check) {
        active = null
        active = withContext(Dispatchers.IO) { ImeCompat.isActive() }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = on, onCheckedChange = {
                    on = it
                    ImeCompat.setEnabled(context, it)
                    scope.launch(Dispatchers.IO) { ImeCompat.apply(context); active = ImeCompat.isActive() }
                })
                Text(stringResource(R.string.ime_compat_title), modifier = Modifier.weight(1f))
            }
            Text(stringResource(R.string.ime_compat_desc), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(when (active) {
                    null -> R.string.ime_compat_checking
                    true -> R.string.ime_compat_ok
                    false -> R.string.ime_compat_off
                }),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { check++ }) { Text(stringResource(R.string.recents_hook_recheck)) }
        }
    }
}
