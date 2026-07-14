package willykez.gitflowmobile.ui.screens.discover

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import willykez.gitflowmobile.data.github.GitHubRepoSummary
import willykez.gitflowmobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(onBack: () -> Unit, vm: DiscoverViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    var credentialMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) { state.message?.let { snack.showSnackbar(it); vm.dismissMessage() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Discover", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface, navigationIconContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {

            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChanged,
                placeholder = { Text("Search GitHub repos…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    IconButton(onClick = vm::search) { Icon(Icons.Filled.Send, "Search") }
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { vm.search() }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            )

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::loadMyRepos, enabled = state.credentials.isNotEmpty()) {
                    Icon(Icons.Filled.Person, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("My Repos")
                }

                Spacer(Modifier.weight(1f))

                if (state.credentials.isNotEmpty()) {
                    Box {
                        TextButton(onClick = { credentialMenuExpanded = true }) {
                            Icon(Icons.Filled.Key, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                state.credentials.firstOrNull { it.id == state.selectedCredentialId }?.name
                                    ?: "No credential",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        DropdownMenu(expanded = credentialMenuExpanded, onDismissRequest = { credentialMenuExpanded = false }) {
                            state.credentials.forEach { cred ->
                                DropdownMenuItem(
                                    text = { Text(cred.name) },
                                    onClick = { vm.onCredentialSelected(cred.id); credentialMenuExpanded = false },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (state.credentials.isEmpty()) {
                Text(
                    "Tip: add a credential first (Credentials screen) to search with fewer rate limits, list your own repos, and clone private ones.",
                    style = MaterialTheme.typography.bodySmall, color = StatusClean,
                )
                Spacer(Modifier.height(8.dp))
            }

            when {
                state.isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                !state.hasSearched -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Explore, null, Modifier.size(48.dp), tint = StatusClean)
                        Spacer(Modifier.height(12.dp))
                        Text("Search GitHub or load your own repos", style = MaterialTheme.typography.bodyMedium, color = StatusClean)
                    }
                }
                state.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No repos found", color = StatusClean)
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.results, key = { it.fullName }) { repo ->
                        DiscoverRepoCard(
                            repo = repo,
                            isCloning = state.cloningFullName == repo.fullName,
                            onClone = { vm.cloneRepo(repo) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverRepoCard(repo: GitHubRepoSummary, isCloning: Boolean, onClone: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (repo.private) Icons.Filled.Lock else Icons.Filled.Public,
                    null, Modifier.size(16.dp), tint = if (repo.private) Amber else StatusClean,
                )
                Spacer(Modifier.width(6.dp))
                Text(repo.fullName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Star, null, Modifier.size(14.dp), tint = Amber)
                Spacer(Modifier.width(2.dp))
                Text("${repo.stars}", style = MaterialTheme.typography.labelSmall, color = StatusClean)
            }
            if (!repo.description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(repo.description, style = MaterialTheme.typography.bodySmall, color = StatusClean, maxLines = 2)
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onClone, enabled = !isCloning, modifier = Modifier.fillMaxWidth()) {
                if (isCloning) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Cloning…")
                } else {
                    Icon(Icons.Filled.Download, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clone")
                }
            }
        }
    }
}
