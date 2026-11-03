package app.countdown

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.countdown.data.EventRepository
import app.countdown.model.CountdownEvent
import app.countdown.ui.CountdownTheme
import app.countdown.ui.EditorScreen
import app.countdown.ui.HomeScreen
import app.countdown.widget.WidgetUpdater

private sealed interface Screen {
    data object Home : Screen
    data class Edit(val event: CountdownEvent?) : Screen
}

class MainActivity : ComponentActivity() {
    private var requestedEvent by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        EventRepository.init(applicationContext)
        requestedEvent = intent?.getStringExtra(EXTRA_EVENT_ID)
        setContent {
            CountdownTheme {
                App(requestedEvent) { requestedEvent = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedEvent = intent.getStringExtra(EXTRA_EVENT_ID)
    }

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
    }
}

@Composable
private fun App(requestedEvent: String?, onRequestHandled: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val events by EventRepository.state.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    LaunchedEffect(requestedEvent) {
        if (requestedEvent != null) {
            EventRepository.get(requestedEvent)?.let { screen = Screen.Edit(it) }
            onRequestHandled()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState is Screen.Edit) {
                    (fadeIn(tween(320)) + slideInVertically(tween(420)) { it / 14 }) togetherWith fadeOut(tween(200))
                } else {
                    fadeIn(tween(300)) togetherWith (fadeOut(tween(200)))
                }
            },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Home -> HomeScreen(
                    events = events,
                    onAdd = { screen = Screen.Edit(null) },
                    onOpen = { screen = Screen.Edit(it) },
                )
                is Screen.Edit -> EditorScreen(
                    initial = s.event,
                    onSave = {
                        EventRepository.upsert(it)
                        WidgetUpdater.updateAll(context)
                        screen = Screen.Home
                    },
                    onDelete = {
                        EventRepository.delete(it)
                        WidgetUpdater.updateAll(context)
                        screen = Screen.Home
                    },
                    onClose = { screen = Screen.Home },
                )
            }
        }
    }
}
