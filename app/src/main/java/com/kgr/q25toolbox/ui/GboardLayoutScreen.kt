package com.kgr.q25toolbox.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kgr.q25toolbox.R

/**
 * Gboard Layout Patch: ships a key character map overlay that stops ALT from
 * latching, fixes the $ key and puts Gboard's symbol/emoji pickers on SYM.
 *
 * There is nothing to toggle here. The layout is declared in the manifest and
 * applied by the framework once the user picks it under Physical keyboard, so
 * this screen only explains it and opens that settings page.
 */
@Composable
fun GboardLayoutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    ScreenScaffold(title = Screen.GboardLayout.title, onBack = onBack) {
        Section(
            stringResource(R.string.gboard_layout_select_title),
            stringResource(R.string.gboard_layout_select_body)
        )

        Button(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_HARD_KEYBOARD_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }) {
            Text(stringResource(R.string.gboard_layout_open_settings))
        }

        DescriptionDivider()

        Section(
            stringResource(R.string.gboard_layout_fixes_title),
            stringResource(R.string.gboard_layout_fixes_body)
        )

        Section(
            stringResource(R.string.gboard_layout_shortcuts_title),
            stringResource(R.string.gboard_layout_shortcuts_body)
        )

        Section(
            stringResource(R.string.gboard_layout_tradeoff_title),
            stringResource(R.string.gboard_layout_tradeoff_body)
        )
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodySmall)
    }
}
