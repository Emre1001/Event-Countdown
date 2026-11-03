package app.countdown.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.countdown.MainActivity
import app.countdown.R
import app.countdown.data.EventRepository
import app.countdown.ui.CountdownTheme
import app.countdown.ui.EventCard
import app.countdown.ui.rememberDarkTheme
import app.countdown.data.AppSettings

/** Auswahl, welches Event ein Widget anzeigt (beim Hinzufügen und beim Neu-Konfigurieren). */
class WidgetConfigActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppSettings.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        EventRepository.init(applicationContext)

        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            CountdownTheme(rememberDarkTheme()) {
                val events by EventRepository.state.collectAsState()
                Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            16.dp, pad.calculateTopPadding() + 16.dp, 16.dp, pad.calculateBottomPadding() + 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            Text(
                                stringResource(R.string.widget_pick_title),
                                fontSize = 30.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                            )
                        }
                        if (events.isEmpty()) {
                            item {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                    Text(
                                        stringResource(R.string.widget_pick_empty),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    TextButton(onClick = {
                                        startActivity(Intent(this@WidgetConfigActivity, MainActivity::class.java))
                                    }) { Text(stringResource(R.string.widget_open_app)) }
                                }
                            }
                        }
                        items(events, key = { it.id }) { e ->
                            EventCard(e, numberSize = 34.sp, onClick = {
                                EventRepository.bindWidget(widgetId, e.id)
                                WidgetUpdater.updateWidget(this@WidgetConfigActivity, widgetId)
                                WidgetUpdater.schedule(this@WidgetConfigActivity)
                                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                                finish()
                            })
                        }
                    }
                }
            }
        }
    }
}
