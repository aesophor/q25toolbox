package com.kgr.q25toolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kgr.q25toolbox.R
import com.kgr.q25toolbox.core.RootShell
import com.kgr.q25toolbox.modules.InputLanguageController
import com.kgr.q25toolbox.service.Q25AccessibilityService
import com.kgr.q25toolbox.service.isQ25AccessibilityServiceEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GRANT_COMMAND =
    "adb shell pm grant com.kgr.q25toolbox android.permission.WRITE_SECURE_SETTINGS"

@Composable
fun LanguageSwitchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember {
        context.getSharedPreferences(Q25AccessibilityService.PREFS, Context.MODE_PRIVATE)
    }

    var serviceEnabled by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(InputLanguageController.hasPermission(context)) }
    var languageCount by remember { mutableStateOf(0) }
    var rootAvailable by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(InputLanguageController.isEnabled(prefs)) }

    LaunchedEffect(Unit) {
        serviceEnabled = isQ25AccessibilityServiceEnabled(context)
        languageCount = InputLanguageController.subtypeCount(context)
        withContext(Dispatchers.IO) {
            val root = RootShell.isRootAvailable()
            withContext(Dispatchers.Main) { rootAvailable = root }
        }
    }

    ScreenScaffold(title = Screen.LanguageSwitch.title, onBack = onBack) {
        AccessibilityServiceBanner(serviceEnabled)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.generic_enabled))
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    enabled = checked
                    InputLanguageController.setEnabled(prefs, checked)
                }
            )
        }

        if (!granted) {
            Text(
                stringResource(R.string.lang_switch_permission_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Text(GRANT_COMMAND, style = MaterialTheme.typography.bodySmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("adb", GRANT_COMMAND))
                }) {
                    Text(stringResource(R.string.lang_switch_copy_command))
                }

                if (rootAvailable) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch(Dispatchers.IO) {
                                InputLanguageController.grantWithRoot()
                                val now = InputLanguageController.hasPermission(context)
                                withContext(Dispatchers.Main) {
                                    granted = now
                                    busy = false
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.lang_switch_grant_root))
                    }
                }
            }
        } else if (languageCount < 2) {
            Text(
                stringResource(R.string.lang_switch_one_language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        DescriptionDivider()
        Text(
            stringResource(R.string.lang_switch_desc),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
