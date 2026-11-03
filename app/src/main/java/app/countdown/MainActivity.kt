package app.countdown

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.countdown.data.AppSettings
import app.countdown.data.EventRepository
import app.countdown.data.EventShare
import app.countdown.data.Reminders
import app.countdown.render.ImageStore
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
    private var importUri by mutableStateOf<Uri?>(null)

    private fun readIntent(intent: Intent?) {
        intent ?: return
        requestedEvent = intent.getStringExtra(EXTRA_EVENT_ID)
        importUri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme == "content" || it.scheme == "file" }
            Intent.ACTION_SEND -> @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            else -> null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettings.init(applicationContext)
        EventRepository.init(applicationContext)
        readIntent(intent)
        setContent { Root(requestedEvent, importUri, { requestedEvent = null }, { importUri = null }) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
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
private fun Root(requestedEvent: String?, importUri: Uri?, onRequestHandled: () -> Unit, onImportHandled: () -> Unit) {
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
                CountdownTheme(dark) { App(nav, overlay, requestedEvent, importUri, onRequestHandled, onImportHandled) }
            }
        }
    }
}

@Composable
private fun App(
    nav: MutableState<Screen>,
    overlay: Boolean,
    requestedEvent: String?,
    importUri: Uri?,
    onRequestHandled: () -> Unit,
    onImportHandled: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun changed() {
        WidgetUpdater.updateAll(context)
        Reminders.schedule(context)
    }
    fun import(uri: Uri) {
        scope.launch {
            val e = EventShare.import(context, uri)
            if (e == null) {
                Toast.makeText(context, context.getString(R.string.import_failed), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, context.getString(R.string.import_done), Toast.LENGTH_SHORT).show()
                changed()
                nav.value = Screen.Edit(e)
            }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { import(it) } }
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
        LaunchedEffect(importUri) {
            if (importUri != null) {
                import(importUri)
                onImportHandled()
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
                    onImport = { openFile.launch(arrayOf("*/*")) },
                )
                Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
                is Screen.Edit -> EditorScreen(
                    initial = s.event,
                    onSave = {
                        EventRepository.upsert(it)
                        changed()
                        screen = Screen.Home
                    },
                    onDelete = {
                        Reminders.cancel(context, it)
                        EventRepository.delete(it)
                        changed()
                        screen = Screen.Home
                    },
                    onDuplicate = { e ->
                        val copy = e.copy(
                            id = java.util.UUID.randomUUID().toString(),
                            title = e.title + " " + context.getString(R.string.copy_suffix),
                            style = e.style.mapImages { ImageStore.duplicate(context, it) },
                        )
                        EventRepository.add(copy)
                        changed()
                        screen = Screen.Edit(copy)
                    },
                    onClose = { screen = Screen.Home },
                )
            }
        }
    }
}
