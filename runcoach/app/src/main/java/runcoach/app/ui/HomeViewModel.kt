package runcoach.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import runcoach.app.data.RunCoachService
import runcoach.core.IntervalPlan
import runcoach.core.Rpe
import runcoach.core.RunnerProfile

data class UiState(
    val healthConnected: Boolean = false,
    val spotifyConnected: Boolean = false,
    val profile: RunnerProfile = RunnerProfile(40),
    val plan: IntervalPlan = IntervalPlan(180, 60, 7),
    val bpmTolerance: Int = 4,
    val report: String? = null,
    val playlistUrl: String? = null,
    val message: String? = null,
    val busy: Boolean = false,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    val service = RunCoachService(app)
    private val store = service.store

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        viewModelScope.launch {
            val health = service.health.isAvailable() && runCatching { service.health.hasPermissions() }.getOrDefault(false)
            _state.update {
                it.copy(
                    healthConnected = health,
                    spotifyConnected = service.spotify.isConnected,
                    profile = store.profile,
                    plan = store.plan,
                    bpmTolerance = store.bpmTolerance,
                    report = store.lastReport,
                    playlistUrl = store.lastPlaylistUrl,
                )
            }
        }
    }

    fun finishSpotifyLogin(redirect: Uri) = work("Connected to Spotify.") { service.spotify.finishLogin(redirect) }

    fun saveSettings(profile: RunnerProfile, plan: IntervalPlan, tolerance: Int) {
        store.profile = profile
        store.plan = plan
        store.bpmTolerance = tolerance
        refreshStatus()
    }

    /** Analyse the newest run; pass [rpe] to re-rate the run you already analysed. */
    fun analyze(rpe: Int?) = work(null) {
        val result = service.processLatestRun(rpe?.let { Rpe(it) })
        _state.update {
            it.copy(message = when {
                result == null -> "No new run since last time. Rate how it felt to re-check it."
                else -> result.playlistNote.ifBlank { null }
            })
        }
    }

    fun makePlaylist() = work(null) {
        val (_, note) = service.makePlaylist()
        _state.update { it.copy(message = note) }
    }

    private fun work(done: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val error = runCatching { block() }.exceptionOrNull()
            _state.update { it.copy(busy = false, message = error?.message ?: done ?: it.message) }
            refreshStatus()
        }
    }
}
