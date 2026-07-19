package willykez.gitflowmobile.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import willykez.gitflowmobile.ui.theme.StatusClean

private val INTERVAL_OPTIONS = listOf(1L, 3L, 6L, 12L, 24L)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, proceed either way — sync itself doesn't depend on it, only the "new commits" notification does */ vm.setBackgroundSyncEnabled(true) }

    fun onToggleSync(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.setBackgroundSyncEnabled(enabled)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            Text("Background sync", style = MaterialTheme.typography.titleMedium)
            Text(
                "Periodically checks every repo for new commits on the remote (fetch only — " +
                    "it never merges or changes your working tree on its own). Needs network access. " +
                    "You'll get a notification if it finds new commits to pull.",
                style = MaterialTheme.typography.bodySmall,
                color = StatusClean,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Enable background sync")
                Switch(
                    checked = state.backgroundSyncEnabled,
                    onCheckedChange = { onToggleSync(it) },
                )
            }

            if (state.backgroundSyncEnabled) {
                Spacer(Modifier.height(16.dp))
                Text("Check every", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    INTERVAL_OPTIONS.forEach { hours ->
                        FilterChip(
                            selected = state.intervalHours == hours,
                            onClick = { vm.setIntervalHours(hours) },
                            label = { Text(if (hours < 24) "${hours}h" else "1d") },
                        )
                    }
                }
            }
        }
    }
}
