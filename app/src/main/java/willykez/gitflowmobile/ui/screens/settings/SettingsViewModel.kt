package willykez.gitflowmobile.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import willykez.gitflowmobile.sync.SyncPrefs
import willykez.gitflowmobile.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val backgroundSyncEnabled: Boolean = false,
    val intervalHours: Long = SyncPrefs.DEFAULT_INTERVAL_HOURS,
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(
        SettingsUiState(
            backgroundSyncEnabled = SyncPrefs.isEnabled(app),
            intervalHours = SyncPrefs.intervalHours(app),
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun setBackgroundSyncEnabled(enabled: Boolean) {
        val app = getApplication<android.app.Application>()
        SyncPrefs.setEnabled(app, enabled)
        SyncScheduler.applyFromPrefs(app)
        _state.value = _state.value.copy(backgroundSyncEnabled = enabled)
    }

    fun setIntervalHours(hours: Long) {
        val app = getApplication<android.app.Application>()
        SyncPrefs.setIntervalHours(app, hours)
        if (_state.value.backgroundSyncEnabled) SyncScheduler.applyFromPrefs(app)
        _state.value = _state.value.copy(intervalHours = hours.coerceAtLeast(SyncPrefs.MIN_INTERVAL_HOURS))
    }
}
