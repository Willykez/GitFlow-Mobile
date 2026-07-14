package willykez.gitflowmobile.ui.screens.repolist

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import willykez.gitflowmobile.data.PublicStorage
import willykez.gitflowmobile.data.db.entity.RepoEntity
import willykez.gitflowmobile.ui.components.GlassCard
import willykez.gitflowmobile.ui.screens.clone.CloneScreen
import willykez.gitflowmobile.ui.theme.*
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoListScreen(
    onOpenRepo: (Long) -> Unit,
    onOpenCredentials: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: RepoListViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsState()
    val snack = remember { SnackbarHostState() }
    var repoPendingDelete by remember { mutableStateOf<RepoEntity?>(null) }
    var repoPendingPullForce by remember { mutableStateOf<RepoEntity?>(null) }
    var repoPendingPushForce by remember { mutableStateOf<RepoEntity?>(null) }
    var showCloneSheet by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var hasStorageAccess by remember { mutableStateOf(PublicStorage.hasStorageAccess(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasStorageAccess = PublicStorage.hasStorageAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snack.showSnackbar(it); vm.dismissSnackbar() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = vm::setSearchQuery,
                            placeholder = { Text("Search repos…") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text("GitFlow Mobile", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    if (showSearch) {
                        IconButton(onClick = { showSearch = false; vm.setSearchQuery("") }) {
                            Icon(Icons.Filled.ArrowBack, "Close search")
                        }
                    }
                },
                actions = {
                    if (!showSearch) {
                        IconButton(onClick = { showSearch = true }) { Icon(Icons.Filled.Search, "Search") }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Filled.Sort, "Sort") }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                DropdownMenuItem(text = { Text("Most recent") },
                                    onClick = { showSortMenu = false; vm.setSortMode(RepoSortMode.RECENT) })
                                DropdownMenuItem(text = { Text("Name (A–Z)") },
                                    onClick = { showSortMenu = false; vm.setSortMode(RepoSortMode.NAME) })
                                DropdownMenuItem(text = { Text("Most changes first") },
                                    onClick = { showSortMenu = false; vm.setSortMode(RepoSortMode.HAS_CHANGES) })
                            }
                        }
                        IconButton(onClick = onOpenDiscover) { Icon(Icons.Filled.Explore, "Discover on GitHub") }
                        IconButton(onClick = onOpenCredentials) { Icon(Icons.Filled.Key, "Credentials") }
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                        IconButton(onClick = { showCloneSheet = true }) { Icon(Icons.Filled.Add, "Clone") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface, navigationIconContentColor = MaterialTheme.colorScheme.onSurface, actionIconContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
        snackbarHost = { SnackbarHost(snack) { d -> Snackbar(d) } },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (!hasStorageAccess) {
                StorageAccessBanner(onGrant = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        context.startActivity(PublicStorage.allFilesAccessIntent(context))
                    }
                })
            }

            val visibleRepos = state.visibleRepos

            if (state.repos.isEmpty()) {
                EmptyState(Modifier.weight(1f), onAddRepo = { showCloneSheet = true }, onDiscover = onOpenDiscover)
            } else if (visibleRepos.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No repos match \"${state.searchQuery}\"", color = StatusClean)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    items(visibleRepos, key = { it.id }) { repo ->
                        RepoCard(
                            repo = repo, isBusy = state.busyRepoId == repo.id,
                            changeCount = state.changeCounts[repo.id],
                            onTap = { onOpenRepo(repo.id) },
                            onLongPress = { repoPendingDelete = repo },
                            onPull = { vm.pull(repo) }, onPush = { vm.push(repo) },
                            onFetch = { vm.fetch(repo) },
                            onRequestPullForce = { repoPendingPullForce = repo },
                            onRequestPushForce = { repoPendingPushForce = repo },
                        )
                    }
                }
            }
        }
    }

    repoPendingDelete?.let { repo ->
        AlertDialog(onDismissRequest = { repoPendingDelete = null },
            title = { Text("Remove ${repo.name}?") },
            text = { Text("Remove it from GitFlow Mobile. You can also delete the local files.") },
            confirmButton = { TextButton(onClick = { vm.deleteRepo(repo, true); repoPendingDelete = null }) { Text("Remove + Delete Files", color = StatusDeleted) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.deleteRepo(repo, false); repoPendingDelete = null }) { Text("Just Remove") }
                    TextButton(onClick = { repoPendingDelete = null }) { Text("Cancel") }
                }
            })
    }

    repoPendingPullForce?.let { repo ->
        willykez.gitflowmobile.ui.screens.changes.ConfirmDialog(
            "Pull (Force) ${repo.name}?",
            "This hard-resets the branch to match the remote. Any local commits not on the remote will be lost. Cannot be undone.",
            confirmLabel = "Force Pull", danger = true,
            onConfirm = { vm.pullForce(repo); repoPendingPullForce = null },
            onDismiss = { repoPendingPullForce = null },
        )
    }
    repoPendingPushForce?.let { repo ->
        willykez.gitflowmobile.ui.screens.changes.ConfirmDialog(
            "Push (Force) ${repo.name}?",
            "This overwrites the remote branch with local history. If someone else pushed in the meantime, their commits will be lost.",
            confirmLabel = "Force Push", danger = true,
            onConfirm = { vm.pushForce(repo); repoPendingPushForce = null },
            onDismiss = { repoPendingPushForce = null },
        )
    }

    // Clone lives here as a bottom sheet, not a nav destination — adding a
    // repo never navigates away from the home screen.
    if (showCloneSheet) {
        CloneScreen(
            onDismiss = { showCloneSheet = false },
            onCloned = { showCloneSheet = false },
            onAddCredential = onOpenCredentials,
        )
    }
}

@Composable
private fun StorageAccessBanner(onGrant: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Amber.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.FolderOpen, null, tint = Amber, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Grant storage access", style = MaterialTheme.typography.labelLarge)
                Text(
                    "So repos save to a public folder you can browse from any file manager, not a hidden app folder.",
                    style = MaterialTheme.typography.bodySmall, color = StatusClean,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onGrant) { Text("Grant") }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onAddRepo: () -> Unit, onDiscover: () -> Unit) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.FolderOff, null, Modifier.size(56.dp), tint = StatusClean)
        Spacer(Modifier.height(16.dp))
        Text("No repositories", style = MaterialTheme.typography.titleMedium)
        Text("Clone one to get started", style = MaterialTheme.typography.bodyMedium, color = StatusClean)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onDiscover) {
                Icon(Icons.Filled.Explore, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Browse GitHub")
            }
            Button(onClick = onAddRepo) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Clone URL") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RepoCard(
    repo: RepoEntity, isBusy: Boolean, changeCount: Int?,
    onTap: () -> Unit, onLongPress: () -> Unit,
    onPull: () -> Unit, onPush: () -> Unit,
    onFetch: () -> Unit, onRequestPullForce: () -> Unit, onRequestPushForce: () -> Unit,
) {
    var showMore by remember { mutableStateOf(false) }
    val hasError = repo.lastError.isNotBlank()
    val accent = when {
        hasError -> StatusDeleted
        changeCount != null && changeCount > 0 -> Amber
        else -> CommandBlue
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onTap, onLongClick = onLongPress),
        accent = accent,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BadgedBox(badge = {
                    if (changeCount != null && changeCount > 0) {
                        Badge(containerColor = Amber) { Text("$changeCount") }
                    }
                }) {
                    Icon(Icons.Filled.FolderOpen, null, Modifier.size(20.dp), tint = CommandBlue)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(repo.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val branch = repo.branch.ifBlank { "—" }
                        Text(branch, style = MaterialTheme.typography.labelSmall, color = Amber)
                        val sync = if (repo.lastSyncTime > 0)
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(repo.lastSyncTime))
                        else "never synced"
                        Text("· $sync", style = MaterialTheme.typography.labelSmall, color = StatusClean)
                    }
                }
                Box {
                    IconButton(onClick = { showMore = true }, Modifier.size(32.dp)) {
                        Icon(Icons.Filled.MoreVert, null, Modifier.size(18.dp), tint = StatusClean)
                    }
                    DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                        DropdownMenuItem(text = { Text("Fetch") }, onClick = { showMore = false; onFetch() },
                            leadingIcon = { Icon(Icons.Filled.Download, null) })
                        DropdownMenuItem(text = { Text("Pull (Force)") }, onClick = { showMore = false; onRequestPullForce() },
                            leadingIcon = { Icon(Icons.Filled.Warning, null, tint = StatusDeleted) })
                        DropdownMenuItem(text = { Text("Push (Force)") }, onClick = { showMore = false; onRequestPushForce() },
                            leadingIcon = { Icon(Icons.Filled.Warning, null, tint = Amber) })
                    }
                }
            }

            if (hasError) {
                Spacer(Modifier.height(6.dp))
                Text(repo.lastError, style = MaterialTheme.typography.labelSmall, color = StatusDeleted, maxLines = 2)
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onPull, enabled = !isBusy, modifier = Modifier.weight(1f)) {
                    if (isBusy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.Filled.ArrowDownward, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Pull") }
                }
                Button(onClick = onPush, enabled = !isBusy, modifier = Modifier.weight(1f)) {
                    if (isBusy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else { Icon(Icons.Filled.ArrowUpward, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Push") }
                }
            }
        }
    }
}
