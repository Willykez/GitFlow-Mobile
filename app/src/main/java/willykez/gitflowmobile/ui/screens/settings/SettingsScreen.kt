package willykez.gitflowmobile.ui.screens.settings

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
                    "it never merges or changes your working tree on its own). Needs network access.",
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
                    onCheckedChange = vm::setBackgroundSyncEnabled,
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
