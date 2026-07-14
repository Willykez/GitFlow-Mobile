package willykez.gitflowmobile.ui.screens.changes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import willykez.gitflowmobile.git.GitFileEntry
import willykez.gitflowmobile.git.GitFileStatus
import willykez.gitflowmobile.ui.components.GlassCard
import willykez.gitflowmobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun ChangesScreen(
    repoId: Long,
    onBack: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenBranches: () -> Unit,
    onOpenStash: () -> Unit,
    onOpenRemote: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenGitignore: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenConflicts: () -> Unit,
    onOpenDiff: (path: String, staged: Boolean) -> Unit,
    vm: ChangesViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsState()
    val snack = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    var showMenu by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showAmendDialog by remember { mutableStateOf(false) }
    var showSquashDialog by remember { mutableStateOf(false) }
    var showCherryPickDialog by remember { mutableStateOf(false) }
    var showPullForceDialog by remember { mutableStateOf(false) }
    var showPushForceDialog by remember { mutableStateOf(false) }
    var showDiscardAllDialog by remember { mutableStateOf(false) }
    var fileToDiscard by remember { mutableStateOf<GitFileEntry?>(null) }

    LaunchedEffect(repoId) { vm.load(repoId) }
    LaunchedEffect(state.message) {
        state.message?.let { snack.showSnackbar(it); vm.dismissMessage() }
    }

    // Pull-to-refresh via the Material2 "pullrefresh" primitives (androidx.compose.material,
    // @ExperimentalMaterialApi) rather than material3's own pull-to-refresh — the material3
    // version that ships in this project's pinned compose-bom doesn't expose it under the
    // plain androidx.compose.material3 package the way a newer BOM would, so this is the
    // long-stable, unambiguous API instead of a guess about material3's internal package layout.
    val pullRefreshState = rememberPullRefreshState(refreshing = state.isLoading, onRefresh = vm::refresh)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.repo?.name ?: "Changes", fontWeight = FontWeight.SemiBold)
                        val branch = state.currentBranch
                        if (branch.isNotBlank()) {
                            Text(
                                "$branch:origin/$branch",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        clipboard.setText(AnnotatedString(branch))
                                        vm.showTransientMessage("Branch name copied")
                                    },
                                ),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                actions = {
                    // Push is the #1 reason someone opens this menu — surface it directly
                    // instead of burying it in an overflow list.
                    IconButton(onClick = vm::push, enabled = !state.isWorking) {
                        Icon(Icons.Filled.Upload, "Push", tint = Amber)
                    }
                    IconButton(onClick = vm::refresh, enabled = !state.isWorking) {
                        Icon(Icons.Filled.Refresh, "Refresh")
                    }
                    // Everything else lives in a grouped bottom sheet, not a flat
                    // 24-item dropdown — see RepoToolsSheet below.
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, "More")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        },
        snackbarHost = { SnackbarHost(snack) { d -> Snackbar(d) } },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .pullRefresh(pullRefreshState),
        ) {
            Column(Modifier.fillMaxSize()) {

                if (state.hasConflicts) {
                    Surface(color = StatusDeleted.copy(alpha = 0.15f)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Warning, null, tint = StatusDeleted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Merge conflicts need resolving",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Button(onClick = onOpenConflicts) { Text("Resolve") }
                        }
                    }
                }

                // Ahead/behind status bar
                if (state.ahead > 0 || state.behind > 0 || state.statusLine.isNotBlank()) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (state.statusLine.isNotBlank())
                                Text(state.statusLine, style = MaterialTheme.typography.bodySmall, color = StatusClean)
                            if (state.ahead > 0 || state.behind > 0) {
                                Icon(Icons.Filled.Upload, null, Modifier.size(14.dp), tint = StatusAdded)
                                Text("${state.ahead}", style = MaterialTheme.typography.labelSmall, color = StatusAdded)
                                Icon(Icons.Filled.Download, null, Modifier.size(14.dp), tint = Amber)
                                Text("${state.behind}", style = MaterialTheme.typography.labelSmall, color = Amber)
                            }
                        }
                    }
                }

                if (state.isLoading) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Staged
                        item {
                            SectionRow("Staged (${state.staged.size})",
                                action = if (state.staged.isNotEmpty()) "Unstage all" else null,
                                onAction = { vm.unstageAll() })
                        }
                        if (state.staged.isEmpty()) {
                            item { PlaceholderRow("Nothing staged") }
                        } else {
                            items(state.staged, key = { "s:" + it.path }) { e ->
                                FileRow(e, staged = true,
                                    onToggle = { vm.unstageFile(e) },
                                    onDiscard = { fileToDiscard = e },
                                    onDiff = { onOpenDiff(e.path, true) })
                            }
                        }
                        item { Spacer(Modifier.height(4.dp)) }
                        // Unstaged
                        item {
                            SectionRow("Changes (${state.unstaged.size})",
                                action = if (state.unstaged.isNotEmpty()) "Stage all" else null,
                                onAction = { vm.stageAll() })
                        }
                        if (state.unstaged.isEmpty()) {
                            item { PlaceholderRow("Working tree clean") }
                        } else {
                            items(state.unstaged, key = { "u:" + it.path }) { e ->
                                FileRow(e, staged = false,
                                    onToggle = { vm.stageFile(e) },
                                    onDiscard = { fileToDiscard = e },
                                    onDiff = { onOpenDiff(e.path, false) })
                            }
                        }
                    }
                }

                // Bottom navigation icon bar — Tags / Log / Branches / Explorer (like PuppyGit)
                Surface(tonalElevation = 4.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        IconButton(onClick = onOpenTags) { Icon(Icons.Filled.Label, "Tags", tint = StatusClean) }
                        IconButton(onClick = onOpenLog)  { Icon(Icons.Filled.History, "Log", tint = StatusClean) }
                        IconButton(onClick = onOpenBranches) { Icon(Icons.Filled.AccountTree, "Branches", tint = StatusClean) }
                        IconButton(onClick = onOpenRemote) { Icon(Icons.Filled.Cloud, "Remotes", tint = StatusClean) }
                        IconButton(onClick = onOpenFiles) { Icon(Icons.Filled.Folder, "Files", tint = StatusClean) }
                    }
                }

                // Commit bar
                CommitBar(
                    message = state.commitMessage,
                    onMessageChanged = vm::onCommitMessageChanged,
                    isWorking = state.isWorking,
                    onCommit = vm::commit,
                    onPush = vm::push,
                )
            }

            PullRefreshIndicator(
                refreshing = state.isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    // ── dialogs ───────────────────────────────────────────────────────────────
    if (showResetDialog) {
        ConfirmDialog("Reset Hard?",
            "This will discard ALL uncommitted changes and reset to HEAD. Cannot be undone.",
            confirmLabel = "Reset Hard", danger = true,
            onConfirm = { vm.resetHard(); showResetDialog = false },
            onDismiss = { showResetDialog = false })
    }
    if (showPullForceDialog) {
        ConfirmDialog("Pull (Force)?",
            "This hard-resets your branch to match the remote. Any local commits not on the remote will be lost. Cannot be undone.",
            confirmLabel = "Force Pull", danger = true,
            onConfirm = { vm.pullForce(); showPullForceDialog = false },
            onDismiss = { showPullForceDialog = false })
    }
    if (showPushForceDialog) {
        ConfirmDialog("Push (Force)?",
            "This overwrites the remote branch with your local history. If someone else pushed in the meantime, their commits will be lost.",
            confirmLabel = "Force Push", danger = true,
            onConfirm = { vm.pushForce(); showPushForceDialog = false },
            onDismiss = { showPushForceDialog = false })
    }
    if (showDiscardAllDialog) {
        ConfirmDialog("Discard All Changes?",
            "This discards every uncommitted change in the working directory. Cannot be undone.",
            confirmLabel = "Discard All", danger = true,
            onConfirm = { vm.discardAll(); showDiscardAllDialog = false },
            onDismiss = { showDiscardAllDialog = false })
    }
    fileToDiscard?.let { e ->
        ConfirmDialog("Discard changes to ${e.path}?",
            "This discards uncommitted changes to this file. Cannot be undone.",
            confirmLabel = "Discard", danger = true,
            onConfirm = { vm.discardFile(e); fileToDiscard = null },
            onDismiss = { fileToDiscard = null })
    }
    if (showAmendDialog) {
        SingleInputDialog("Amend Last Commit", "New commit message", state.commitMessage,
            onConfirm = { msg -> vm.amendCommit(msg); showAmendDialog = false },
            onDismiss = { showAmendDialog = false })
    }
    var squashCount by remember { mutableStateOf("2") }
    if (showSquashDialog) {
        SingleInputDialog("Squash Commits", "How many last commits to squash?", squashCount,
            onConfirm = { n -> vm.squash(n.toIntOrNull() ?: 2, state.commitMessage); showSquashDialog = false },
            onDismiss = { showSquashDialog = false })
    }
    var cherryPickSha by remember { mutableStateOf("") }
    if (showCherryPickDialog) {
        SingleInputDialog("Cherry-Pick", "Commit SHA to cherry-pick", cherryPickSha,
            onConfirm = { sha -> vm.cherryPick(sha); showCherryPickDialog = false },
            onDismiss = { showCherryPickDialog = false })
    }

    if (showMenu) {
        RepoToolsSheet(
            onDismiss = { showMenu = false },
            onStageAll = vm::stageAll,
            onCommitAll = vm::stageAllAndCommit,
            onDiscardAll = { showDiscardAllDialog = true },
            onFetch = vm::fetch,
            onPullMerge = vm::pullMerge,
            onPullRebase = vm::pullRebase,
            onPullForce = { showPullForceDialog = true },
            onPushForce = { showPushForceDialog = true },
            onSyncMerge = vm::syncMerge,
            onSyncRebase = vm::syncRebase,
            onOpenLog = onOpenLog,
            onCherryPick = { showCherryPickDialog = true },
            onAmend = { showAmendDialog = true },
            onSquash = { showSquashDialog = true },
            onResetSoft = vm::resetSoft,
            onResetMixed = vm::resetMixed,
            onResetHard = { showResetDialog = true },
            onOpenStash = onOpenStash,
            onOpenBranches = onOpenBranches,
            onOpenTags = onOpenTags,
            onOpenRemote = onOpenRemote,
            onOpenGitignore = onOpenGitignore,
            onOpenFiles = onOpenFiles,
        )
    }
}

/**
 * Every less-frequent repo action, organized as labeled groups of glass
 * cards instead of one long flat dropdown. Grouping (not just alphabetizing)
 * is what makes 20+ actions scannable: a person looking for "push" reasons
 * for anxiety about the remote in one visual cluster, "reset" danger in
 * another, and navigation to other screens in a third — never all mixed
 * together in a single scrolling list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoToolsSheet(
    onDismiss: () -> Unit,
    onStageAll: () -> Unit,
    onCommitAll: () -> Unit,
    onDiscardAll: () -> Unit,
    onFetch: () -> Unit,
    onPullMerge: () -> Unit,
    onPullRebase: () -> Unit,
    onPullForce: () -> Unit,
    onPushForce: () -> Unit,
    onSyncMerge: () -> Unit,
    onSyncRebase: () -> Unit,
    onOpenLog: () -> Unit,
    onCherryPick: () -> Unit,
    onAmend: () -> Unit,
    onSquash: () -> Unit,
    onResetSoft: () -> Unit,
    onResetMixed: () -> Unit,
    onResetHard: () -> Unit,
    onOpenStash: () -> Unit,
    onOpenBranches: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenRemote: () -> Unit,
    onOpenGitignore: () -> Unit,
    onOpenFiles: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var openGroup by remember { mutableStateOf<String?>(null) }

    fun close(action: () -> Unit) {
        action()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Repo tools", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))

            ToolGroup(
                title = "Sync with remote", icon = Icons.Filled.Sync, isOpen = openGroup == "sync",
                onToggle = { openGroup = if (openGroup == "sync") null else "sync" },
            ) {
                ToolRow("Fetch", Icons.Filled.Download) { close(onFetch) }
                ToolRow("Pull (merge)", Icons.Filled.MergeType) { close(onPullMerge) }
                ToolRow("Pull (rebase)", Icons.Filled.LinearScale) { close(onPullRebase) }
                ToolRow("Sync (merge)", Icons.Filled.SyncAlt) { close(onSyncMerge) }
                ToolRow("Sync (rebase)", Icons.Filled.Sync) { close(onSyncRebase) }
                ToolRow("Pull (force) — overwrites local commits", Icons.Filled.Warning, danger = true) { close(onPullForce) }
                ToolRow("Push (force) — overwrites the remote", Icons.Filled.Warning, danger = true) { close(onPushForce) }
            }

            ToolGroup(
                title = "Staging", icon = Icons.Filled.Add, isOpen = openGroup == "staging",
                onToggle = { openGroup = if (openGroup == "staging") null else "staging" },
            ) {
                ToolRow("Stage all", Icons.Filled.Add) { close(onStageAll) }
                ToolRow("Stage all & commit", Icons.Filled.Check) { close(onCommitAll) }
                ToolRow("Discard all changes", Icons.Filled.DeleteSweep, danger = true) { close(onDiscardAll) }
            }

            ToolGroup(
                title = "History", icon = Icons.Filled.History, isOpen = openGroup == "history",
                onToggle = { openGroup = if (openGroup == "history") null else "history" },
            ) {
                ToolRow("Commit log", Icons.Filled.History) { close(onOpenLog) }
                ToolRow("Cherry-pick", Icons.Filled.ContentCopy) { close(onCherryPick) }
                ToolRow("Amend last commit", Icons.Filled.Edit) { close(onAmend) }
                ToolRow("Squash commits…", Icons.Filled.Compress) { close(onSquash) }
            }

            ToolGroup(
                title = "Reset", icon = Icons.Filled.RestartAlt, isOpen = openGroup == "reset",
                onToggle = { openGroup = if (openGroup == "reset") null else "reset" },
            ) {
                ToolRow("Reset (soft)", Icons.Filled.Undo) { close(onResetSoft) }
                ToolRow("Reset (mixed)", Icons.Filled.RestartAlt) { close(onResetMixed) }
                ToolRow("Reset (hard)", Icons.Filled.DeleteForever, danger = true) { close(onResetHard) }
            }

            ToolGroup(
                title = "Manage", icon = Icons.Filled.Folder, isOpen = openGroup == "manage",
                onToggle = { openGroup = if (openGroup == "manage") null else "manage" },
            ) {
                ToolRow("Stash", Icons.Filled.Inventory) { close(onOpenStash) }
                ToolRow("Branches", Icons.Filled.AccountTree) { close(onOpenBranches) }
                ToolRow("Tags", Icons.Filled.Label) { close(onOpenTags) }
                ToolRow("Remotes", Icons.Filled.Cloud) { close(onOpenRemote) }
                ToolRow("Edit .gitignore", Icons.Filled.VisibilityOff) { close(onOpenGitignore) }
                ToolRow("Files", Icons.Filled.Folder) { close(onOpenFiles) }
            }
        }
    }
}

/** A collapsible group header + its rows, rendered as one glass card. Collapsed by default
 *  so the sheet opens short — a person expands only the group they came here for. */
@Composable
private fun ToolGroup(
    title: String,
    icon: ImageVector,
    isOpen: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = CommandBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Icon(
                    if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null,
                    tint = StatusClean, modifier = Modifier.size(20.dp),
                )
            }
            if (isOpen) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Column(Modifier.padding(vertical = 4.dp), content = content)
            }
        }
    }
}

@Composable
private fun ToolRow(label: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (danger) StatusDeleted else StatusClean, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (danger) StatusDeleted else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SectionRow(title: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (action != null) TextButton(onClick = onAction) { Text(action, fontSize = 12.sp) }
    }
}

@Composable
private fun PlaceholderRow(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = StatusClean,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp))
}

/** Icon + tint for a file's status — used by the row's leading icon so the same visual
 * language (green=added, amber=modified, red=deleted/conflict) is consistent everywhere
 * a file status shows up, replacing the old plain colored-letter treatment. */
private fun statusIcon(status: GitFileStatus): Pair<ImageVector, Color> = when (status) {
    GitFileStatus.ADDED -> Icons.Filled.AddCircle to StatusAdded
    GitFileStatus.MODIFIED -> Icons.Filled.Edit to StatusModified
    GitFileStatus.DELETED -> Icons.Filled.RemoveCircle to StatusDeleted
    GitFileStatus.RENAMED -> Icons.Filled.DriveFileRenameOutline to StatusModified
    GitFileStatus.TYPE_CHANGED -> Icons.Filled.SwapHoriz to StatusModified
    GitFileStatus.CONFLICTED -> Icons.Filled.ErrorOutline to StatusDeleted
}

private data class SwipeBackgroundSpec(val color: Color, val icon: ImageVector, val label: String, val alignEnd: Boolean)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    e: GitFileEntry, staged: Boolean,
    onToggle: () -> Unit, onDiscard: () -> Unit, onDiff: () -> Unit,
) {
    val (icon, color) = statusIcon(e.status)
    var showOptions by remember { mutableStateOf(false) }

    // Swipe right = primary action (stage/unstage). Swipe left = discard, but only for
    // unstaged files — staged files have nothing destructive to do in that direction, so
    // that gesture is disabled for them rather than silently doing nothing.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onToggle()
                SwipeToDismissBoxValue.EndToStart -> if (!staged) onDiscard()
                SwipeToDismissBoxValue.Settled -> {}
            }
            false // always snap back — the row leaves the list via data change, not the swipe animation
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = !staged,
        enableDismissFromStartToEnd = true,
        backgroundContent = {
            val spec = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd ->
                    SwipeBackgroundSpec(StatusAdded, Icons.Filled.Check, if (staged) "Unstage" else "Stage", alignEnd = false)
                SwipeToDismissBoxValue.EndToStart ->
                    SwipeBackgroundSpec(StatusDeleted, Icons.Filled.DeleteSweep, "Discard", alignEnd = true)
                else -> SwipeBackgroundSpec(StatusClean, Icons.Filled.Check, "", alignEnd = false)
            }
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(spec.color)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (spec.alignEnd) Arrangement.End else Arrangement.Start,
            ) {
                Icon(spec.icon, null, tint = Cream)
                Spacer(Modifier.width(6.dp))
                Text(spec.label, color = Cream, style = MaterialTheme.typography.labelMedium)
            }
        },
    ) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(0.dp),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .combinedClickable(onClick = onToggle, onLongClick = { showOptions = true })
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, Modifier.size(18.dp), tint = color)
                Spacer(Modifier.width(8.dp))
                Text(e.path, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                IconButton(onClick = onDiff, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Code, "Diff", Modifier.size(16.dp), tint = StatusClean)
                }
                Box {
                    IconButton(onClick = { showOptions = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.MoreVert, "Options", Modifier.size(16.dp), tint = StatusClean)
                    }
                    DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                        DropdownMenuItem(text = { Text(if (staged) "Unstage" else "Stage") },
                            onClick = { showOptions = false; onToggle() })
                        DropdownMenuItem(text = { Text("View Diff") },
                            onClick = { showOptions = false; onDiff() })
                        if (!staged) DropdownMenuItem(
                            text = { Text("Discard Changes", color = StatusDeleted) },
                            onClick = { showOptions = false; onDiscard() })
                    }
                }
            }
        }
    }
}

@Composable
private fun CommitBar(
    message: String, onMessageChanged: (String) -> Unit,
    isWorking: Boolean, onCommit: () -> Unit, onPush: () -> Unit,
) {
    Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            OutlinedTextField(
                value = message, onValueChange = onMessageChanged,
                label = { Text("Commit message") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2, maxLines = 4,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPush, modifier = Modifier.weight(1f), enabled = !isWorking) {
                    Icon(Icons.Filled.Upload, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Push")
                }
                Button(onClick = onCommit, modifier = Modifier.weight(1f), enabled = !isWorking) {
                    if (isWorking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Commit") }
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirmLabel: String, danger: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = if (danger) StatusDeleted else MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun SingleInputDialog(title: String, label: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(label) }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
