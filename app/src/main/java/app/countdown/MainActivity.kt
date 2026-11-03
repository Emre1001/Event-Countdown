package app.countdown

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.countdown.data.AppSettings
import app.countdown.data.EventRepository
import app.countdown.model.CountdownEvent
import app.countdown.ui.CountdownTheme
import app.countdown.ui.EditorScreen
import app.countdown.ui.HomeScreen
import app.countdown.ui.SettingsScreen
import app.countdown.ui.ThemeRevealHost
import app.countdown.ui.UpdateHost
import app.countdown.update.UpdateController
import app.countdown.widget.WidgetUpdater
import kotlinx.coroutines.delay

private sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Edit(val event: CountdownEvent?) : Screen
}

class MainActivity : ComponentActivity() {
    private var requestedEvent by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettings.init(applicationContext)
        EventRepository.init(applicationContext)
        requestedEvent = intent?.getStringExtra(EXTRA_EVENT_ID)
        setContent { Root(requestedEvent) { requestedEvent = null } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedEvent = intent.getStringExtra(EXTRA_EVENT_ID)
    }

    override fun onResume() {
        super.onResume()
        UpdateController.onResume()
    }

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
    }
}

@Composable
private fun Root(requestedEvent: String?, onRequestHandled: () -> Unit) {
    val language by AppSettings.language.collectAsState()
    // Navigation liegt außerhalb von Sprach- und Theme-Wechsel, damit beide den Zustand nicht verlieren.
    val nav = remember { mutableStateOf<Screen>(Screen.Home) }

    AnimatedContent(
        targetState = language,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
        label = "language",
    ) { lang ->
        val base = LocalContext.current
        // Live-Konfiguration als Schlüssel: Drehen, Schriftgröße und System-Dark-Mode bleiben aktuell,
        // weil die Activity diese Änderungen selbst verarbeitet (configChanges) statt neu zu starten.
        val liveConfig = LocalConfiguration.current
        val localized = remember(lang, base, liveConfig) { AppSettings.wrap(base, lang) }
        CompositionLocalProvider(
            LocalContext provides localized,
            LocalConfiguration provides localized.resources.configuration,
            // Der lokalisierte Context kennt die Activity nicht mehr; Zurück-Geste explizit durchreichen.
            LocalOnBackPressedDispatcherOwner provides (base as ComponentActivity),
            LocalActivityResultRegistryOwner provides (base as ComponentActivity),
        ) {
            ThemeRevealHost { dark, overlay ->
                CountdownTheme(dark) { App(nav, overlay, requestedEvent, onRequestHandled) }
            }
        }
    }
}

@Composable
private fun App(
    nav: MutableState<Screen>,
    overlay: Boolean,
    requestedEvent: String?,
    onRequestHandled: () -> Unit,
) {
    val context = LocalContext.current
    val events by EventRepository.state.collectAsState()
    var screen by nav

    if (!overlay) {
        LaunchedEffect(requestedEvent) {
            if (requestedEvent != null) {
                EventRepository.get(requestedEvent)?.let { screen = Screen.Edit(it) }
                onRequestHandled()
            }
        }
        // Update-Prüfung ~5 s nach dem Start, danach alle 6 Stunden.
        LaunchedEffect(Unit) {
            delay(5_000)
            while (true) {
                UpdateController.checkAuto()
                delay(6L * 60 * 60 * 1000)
            }
        }
        UpdateHost()
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState is Screen.Home) {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                } else {
                    (fadeIn(tween(320)) + slideInVertically(tween(420)) { it / 14 }) togetherWith fadeOut(tween(200))
                }
            },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Home -> HomeScreen(
                    events = events,
                    onAdd = { screen = Screen.Edit(null) },
                    onOpen = { screen = Screen.Edit(it) },
                    onSettings = { screen = Screen.Settings },
                )
                Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
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
