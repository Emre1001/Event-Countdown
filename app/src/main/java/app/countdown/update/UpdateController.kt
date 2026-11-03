package app.countdown.update

import android.content.Context
import app.countdown.data.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateDialogState {
    val info: UpdateManager.Info
    data class Available(override val info: UpdateManager.Info) : UpdateDialogState
    data class NeedPermission(override val info: UpdateManager.Info) : UpdateDialogState
    data class Downloading(override val info: UpdateManager.Info, val percent: Int) : UpdateDialogState
    data class Handoff(override val info: UpdateManager.Info) : UpdateDialogState
    data class Error(override val info: UpdateManager.Info) : UpdateDialogState
}

enum class CheckStatus { IDLE, CHECKING, UP_TO_DATE, FAILED }

/** Zustandsautomat für Update-Prüfung, Popup und Installation. */
object UpdateController {
    // Öffentliches Repository, aus dem die Releases kommen.
    private const val OWNER = "Emre1001"
    private const val REPO = "Event-Countdown"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    private lateinit var manager: UpdateManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var handoffPending = false

    private val _dialog = MutableStateFlow<UpdateDialogState?>(null)
    val dialog: StateFlow<UpdateDialogState?> = _dialog.asStateFlow()

    private val _status = MutableStateFlow(CheckStatus.IDLE)
    val status: StateFlow<CheckStatus> = _status.asStateFlow()

    fun init(context: Context) {
        manager = UpdateManager(context.applicationContext, OWNER, REPO)
        manager.deleteInstallerFiles()
    }

    fun currentVersion(): String = manager.currentVersion()

    /** Stille Prüfung: respektiert "Überspringen", "Später" und den Automatik-Schalter. */
    fun checkAuto() {
        if (!AppSettings.autoUpdate.value || _dialog.value != null) return
        scope.launch {
            val info = runCatching { manager.check() }.getOrNull() ?: return@launch
            if (info.version == AppSettings.skippedVersion) return@launch
            if (System.currentTimeMillis() < AppSettings.remindAfter) return@launch
            if (_dialog.value == null) _dialog.value = UpdateDialogState.Available(info)
        }
    }

    /** Manuelle Prüfung aus den Einstellungen: bietet auch übersprungene Versionen an. */
    fun checkManual() {
        if (_status.value == CheckStatus.CHECKING) return
        _status.value = CheckStatus.CHECKING
        scope.launch {
            try {
                val info = manager.check()
                if (info == null) {
                    _status.value = CheckStatus.UP_TO_DATE
                } else {
                    _status.value = CheckStatus.IDLE
                    _dialog.value = UpdateDialogState.Available(info)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = CheckStatus.FAILED
            }
        }
    }

    fun later() {
        AppSettings.remindAfter = System.currentTimeMillis() + DAY_MS
        _dialog.value = null
    }

    fun skip(info: UpdateManager.Info) {
        AppSettings.skippedVersion = info.version
        _dialog.value = null
    }

    fun dismiss() { _dialog.value = null }

    fun install(info: UpdateManager.Info) {
        if (!manager.canInstall()) {
            _dialog.value = UpdateDialogState.NeedPermission(info)
            manager.openInstallPermissionSettings()
            return
        }
        job?.cancel()
        job = scope.launch {
            try {
                _dialog.value = UpdateDialogState.Downloading(info, 0)
                val file = manager.download(info) { p -> _dialog.value = UpdateDialogState.Downloading(info, p) }
                handoffPending = true
                manager.install(file)
                _dialog.value = UpdateDialogState.Handoff(info)
            } catch (e: CancellationException) {
                manager.deleteInstallerFiles()
                throw e
            } catch (e: Exception) {
                manager.deleteInstallerFiles()
                _dialog.value = UpdateDialogState.Error(info)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        manager.deleteInstallerFiles()
        _dialog.value = null
    }

    /** Kommt die App nach der Übergabe an den Installer zurück, ist der Systemdialog weg: APK löschen. */
    fun onResume() {
        if (handoffPending) {
            handoffPending = false
            manager.deleteInstallerFiles()
            if (_dialog.value is UpdateDialogState.Handoff) _dialog.value = null
        }
    }
}
