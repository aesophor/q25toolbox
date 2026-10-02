package com.kgr.q25toolbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kgr.q25toolbox.R
import com.kgr.q25toolbox.core.Rom
import com.kgr.q25toolbox.core.RomProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun romLabel(rom: Rom): String = when (rom) {
    Rom.BENOS -> stringResource(R.string.rom_benos)
    Rom.ZINWAOS -> stringResource(R.string.rom_zinwaos)
    Rom.LINEAGE -> stringResource(R.string.rom_lineage)
    Rom.UNKNOWN -> "?"
}

/** Active ROM profile (null for the instant it takes to read build props off the main thread). */
@Composable
fun rememberRom(): State<Rom?> {
    val context = LocalContext.current
    return produceState<Rom?>(null) { value = withContext(Dispatchers.IO) { RomProfile.get(context).rom } }
}

/** Lists the selectable ROMs; [onPick] gets null for "back to automatic". */
@Composable
fun RomPickerDialog(
    title: String,
    body: String?,
    autoLabel: String?,
    onPick: (Rom?) -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (body != null) Text(body)
                listOf(Rom.LINEAGE, Rom.BENOS, Rom.ZINWAOS).forEach { rom ->
                    TextButton(onClick = { onPick(rom) }) { Text(romLabel(rom)) }
                }
                if (autoLabel != null) TextButton(onClick = { onPick(null) }) { Text(autoLabel) }
            }
        },
        confirmButton = {},
        dismissButton = dismissLabel?.let { { TextButton(onClick = onDismiss) { Text(it) } } },
    )
}

/**
 * Shown on launch only when detection failed (no Lineage/BenOS/Zinwa marker) and the user has
 * not picked a ROM yet. Known ROMs never see it; "Later" leaves the profile UNKNOWN.
 */
@Composable
fun RomChoiceDialog() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        show = withContext(Dispatchers.IO) { RomProfile.needsChoice(context) }
    }
    if (!show) return
    RomPickerDialog(
        title = stringResource(R.string.rom_choice_title),
        body = stringResource(R.string.rom_choice_body),
        autoLabel = null,
        onPick = { RomProfile.setOverride(context, it); show = false },
        onDismiss = { show = false },
        dismissLabel = stringResource(R.string.rom_choice_later),
    )
}
