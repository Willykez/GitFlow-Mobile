package willykez.gitflowmobile.ui.screens.clone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import willykez.gitflowmobile.ui.theme.StatusAdded
import willykez.gitflowmobile.ui.theme.StatusClean
import willykez.gitflowmobile.ui.theme.StatusDeleted

/**
 * Clone, shown as a bottom sheet over the repo list so adding a repo never
 * navigates away from the home screen. Call this from RepoListScreen when
 * showCloneSheet is true — there is no more standalone Clone route in
 * AppNav.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneScreen(
    onDismiss: () -> Unit,
    onCloned: () -> Unit,
    onAddCredential: () -> Unit,
    viewModel: CloneViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var credentialMenuExpanded by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val clipboard = LocalClipboardManager.current
    var clipboardSuggestion by remember { mutableStateOf<String?>(null) }
    var suggestionDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val clipText = clipboard.getText()?.text?.trim()
        if (!clipText.isNullOrBlank() && looksLikeGitUrl(clipText) && state.url.isBlank()) {
            clipboardSuggestion = clipText
        }
    }

    LaunchedEffect(state.done) {
        if (state.done) {
            onCloned()
            // The ViewModel outlives this sheet (it's scoped to the screen behind it,
            // not to `if (showCloneSheet)`), so `done` must be cleared here — otherwise
            // the next time this sheet opens, this effect fires immediately on first
            // composition with the stale `done = true` from the previous successful
            // clone and closes the sheet before it can animate in.
            viewModel.resetForm()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Clone a repo",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            if (clipboardSuggestion != null && !suggestionDismissed) {
                Card(
                    onClick = {
                        viewModel.onUrlChanged(clipboardSuggestion!!)
                        suggestionDismissed = true
                    },
                    colors = CardDefaults.cardColors(containerColor = StatusAdded.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Use URL from clipboard?", style = MaterialTheme.typography.labelLarge)
                            Text(
                                clipboardSuggestion!!, maxLines = 1,
                                style = MaterialTheme.typography.bodySmall, color = StatusClean,
                            )
                        }
                        IconButton(onClick = { suggestionDismissed = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::onUrlChanged,
                label = { Text("Repository URL") },
                placeholder = { Text("https://github.com/you/repo.git") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.repoName,
                onValueChange = viewModel::onRepoNameChanged,
                label = { Text("Folder name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.branch,
                onValueChange = viewModel::onBranchChanged,
                label = { Text("Branch (optional)") },
                placeholder = { Text("main") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Column {
                Text(
                    "Credential",
                    style = MaterialTheme.typography.labelLarge,
                    color = StatusClean
                )
                Spacer(Modifier.height(6.dp))

                if (state.credentials.isEmpty()) {
                    Card(
                        onClick = onAddCredential,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("No saved credentials — tap to add one")
                        }
                    }
                } else {
                    ExposedDropdownMenuBox(
                        expanded = credentialMenuExpanded,
                        onExpandedChange = { credentialMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = state.credentials.firstOrNull { it.id == state.selectedCredentialId }?.name
                                ?: "None (public repo)",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Use credential") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = credentialMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        DropdownMenu(
                            expanded = credentialMenuExpanded,
                            onDismissRequest = { credentialMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None (public repo)") },
                                onClick = {
                                    viewModel.onCredentialSelected(null)
                                    credentialMenuExpanded = false
                                }
                            )
                            state.credentials.forEach { cred ->
                                DropdownMenuItem(
                                    text = { Text(cred.name) },
                                    onClick = {
                                        viewModel.onCredentialSelected(cred.id)
                                        credentialMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            state.errorMessage?.let { message ->
                Text(message, color = StatusDeleted, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = viewModel::startClone,
                enabled = !state.isCloning,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isCloning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Cloning…")
                } else {
                    Text("Clone")
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Deliberately permissive — false positives just show a dismissible suggestion banner,
 * false negatives mean no banner at all, so erring toward "looks plausible" costs a lot
 * less than an over-strict regex that misses real URLs from less common git hosts. */
private fun looksLikeGitUrl(text: String): Boolean {
    if (text.contains(' ') || text.contains('\n')) return false
    val lower = text.lowercase()
    return lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("git@") ||
        lower.endsWith(".git")
}
