package app.countdown.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.countdown.R
import app.countdown.data.AppSettings
import app.countdown.data.ThemePref
import app.countdown.update.CheckStatus
import app.countdown.update.UpdateController
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val theme by AppSettings.theme.collectAsState()
    val language by AppSettings.language.collectAsState()
    val auto by AppSettings.autoUpdate.collectAsState()
    val status by UpdateController.status.collectAsState()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).readableWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
                Text(
                    stringResource(R.string.settings),
                    modifier = Modifier.weight(1f).padding(end = 48.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionTitle(stringResource(R.string.settings_appearance))
                Group(padding = 6.dp) {
                    SegmentedControl(
                        listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                        ThemePref.entries.indexOf(theme),
                    ) { i, origin -> AppSettings.setTheme(ThemePref.entries[i], origin) }
                }

                SectionTitle(stringResource(R.string.settings_language), Modifier.padding(top = 14.dp))
                Group { LanguageRow(language) }

                SectionTitle(stringResource(R.string.section_updates), Modifier.padding(top = 14.dp))
                Group {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.update_version, UpdateController.currentVersion()),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                when (status) {
                                    CheckStatus.CHECKING -> stringResource(R.string.update_checking)
                                    CheckStatus.UP_TO_DATE -> stringResource(R.string.update_up_to_date)
                                    CheckStatus.FAILED -> stringResource(R.string.update_check_failed)
                                    CheckStatus.IDLE -> ""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (status == CheckStatus.FAILED) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { UpdateController.checkManual() }, enabled = status != CheckStatus.CHECKING) {
                            Text(stringResource(R.string.update_check))
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.update_auto), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.update_auto_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = auto, onCheckedChange = { AppSettings.setAutoUpdate(it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(current: String) {
    var open by remember { mutableStateOf(false) }
    val currentName = AppSettings.languages.firstOrNull { it.first == current }?.second
        ?: stringResource(R.string.language_system)
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(currentName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            offset = androidx.compose.ui.unit.DpOffset(16.dp, 0.dp),
        ) {
            val entries = listOf("system" to stringResource(R.string.language_system)) + AppSettings.languages
            entries.forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    trailingIcon = {
                        if (code == current) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    onClick = {
                        open = false
                        AppSettings.setLanguage(code)
                    },
                )
            }
        }
    }
}
