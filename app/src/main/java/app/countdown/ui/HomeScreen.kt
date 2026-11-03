package app.countdown.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.countdown.R
import app.countdown.model.CountdownEvent

@Composable
fun HomeScreen(events: List<CountdownEvent>, onAdd: () -> Unit, onOpen: (CountdownEvent) -> Unit, onSettings: () -> Unit) {
    val seen = remember { mutableSetOf<String>() }
    // Anstehende Events zuerst (nächstes oben), vergangene danach (jüngstes zuerst).
    val now = System.currentTimeMillis()
    val sorted = remember(events) {
        val (past, upcoming) = events.partition { it.targetMillis < now }
        upcoming.sortedBy { it.targetMillis } + past.sortedByDescending { it.targetMillis }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(2.dp, 2.dp, 2.dp, 2.dp),
            ) {
                Icon(Icons.Filled.Add, stringResource(R.string.add_event))
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().readableWidth(),
            contentPadding = PaddingValues(
                start = pad.calculateStartPadding(LocalLayoutDirection.current) + 16.dp,
                end = pad.calculateEndPadding(LocalLayoutDirection.current) + 16.dp,
                top = pad.calculateTopPadding() + 12.dp,
                bottom = pad.calculateBottomPadding() + 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.home_title),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
                }
            }
            if (events.isEmpty()) {
                item {
                    Box(Modifier.emptyStatePadding(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleLarge)
                            Text(
                                stringResource(R.string.home_empty_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
            itemsIndexed(sorted, key = { _, e -> e.id }) { index, e ->
                // Nur beim ersten Erscheinen gestaffelt einblenden, nicht bei jedem Zurückscrollen.
                val first = remember(e.id) { seen.add(e.id) }
                EventCard(
                    e,
                    Modifier.animateItem().then(if (first) Modifier.staggerIn(index) else Modifier),
                    onClick = { onOpen(e) },
                )
            }
        }
    }
}

private fun Modifier.emptyStatePadding(): Modifier = this.fillMaxWidth().padding(vertical = 96.dp)
