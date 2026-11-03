package app.countdown.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.countdown.R
import app.countdown.update.UpdateController
import app.countdown.update.UpdateDialogState

@Composable
fun UpdateHost() {
    val state by UpdateController.dialog.collectAsState()
    state?.let { UpdateDialog(it) }
}

@Composable
private fun UpdateDialog(state: UpdateDialogState) {
    val info = state.info
    Dialog(
        onDismissRequest = {
            // Zurück/Außen-Tippen = "Später"; während des Ladens bricht es sauber ab.
            when (state) {
                is UpdateDialogState.Available, is UpdateDialogState.NeedPermission -> UpdateController.later()
                is UpdateDialogState.Downloading -> UpdateController.cancel()
                else -> UpdateController.dismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp)),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp).animateContentSize(tween(250)),
            ) {
                Text(
                    stringResource(R.string.update_available),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.update_new_version, info.version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(16.dp))

                when (state) {
                    is UpdateDialogState.Available, is UpdateDialogState.NeedPermission -> {
                        Text(
                            info.notes.trim().ifBlank { stringResource(R.string.update_no_notes) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                        )
                        if (state is UpdateDialogState.NeedPermission) {
                            Text(
                                stringResource(R.string.update_permission),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { UpdateController.install(info) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text(stringResource(R.string.update_install), fontWeight = FontWeight.SemiBold) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { UpdateController.later() }) { Text(stringResource(R.string.update_later)) }
                            TextButton(onClick = { UpdateController.skip(info) }) { Text(stringResource(R.string.update_skip), maxLines = 2) }
                        }
                    }
                    is UpdateDialogState.Downloading -> {
                        val p by animateFloatAsState(state.percent / 100f, tween(250), label = "progress")
                        Text(
                            stringResource(R.string.update_downloading) + "  ${state.percent}%",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        TextButton(onClick = { UpdateController.cancel() }, modifier = Modifier.padding(top = 12.dp)) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                    is UpdateDialogState.Handoff -> {
                        Text(stringResource(R.string.update_handoff), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                    is UpdateDialogState.Error -> {
                        Text(
                            stringResource(R.string.update_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { UpdateController.install(info) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text(stringResource(R.string.update_retry)) }
                        TextButton(onClick = { UpdateController.dismiss() }) { Text(stringResource(R.string.close)) }
                    }
                }
            }
        }
    }
}
