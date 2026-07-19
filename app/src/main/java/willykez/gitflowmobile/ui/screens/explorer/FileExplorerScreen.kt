package willykez.gitflowmobile.ui.screens.explorer

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import willykez.gitflowmobile.App
import willykez.gitflowmobile.data.db.entity.RepoEntity
import willykez.gitflowmobile.git.FileNode
import willykez.gitflowmobile.git.GitEngine
import willykez.gitflowmobile.git.GitResult
import willykez.gitflowmobile.ui.components.GlassCard
import willykez.gitflowmobile.ui.screens.changes.ConfirmDialog
import willykez.gitflowmobile.ui.screens.changes.SingleInputDialog
import willykez.gitflowmobile.ui.theme.Amber
import willykez.gitflowmobile.ui.theme.CommandBlue
import willykez.gitflowmobile.ui.theme.StatusClean
import willykez.gitflowmobile.ui.theme.StatusDeleted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ExplorerUiState(
    val repo: RepoEntity? = null,
    val relativePath: String = "",
    val nodes: List<FileNode> = emptyList(),
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val message: String? = null,
    val selectedPaths: Set<String> = emptySet(),
)

class FileExplorerViewModel(app: Application) : AndroidViewModel(app) {
    private val appRef = app as App
    private val repoRepo = appRef.repoRepository
    private val credRepo = appRef.credentialRepository

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    fun load(repoId: Long, relativePath: String) {
        viewModelScope.launch {
            val repo = repoRepo.getById(repoId) ?: return@launch
            _uiState.value = _uiState.value.copy(repo = repo, relativePath = relativePath, isLoading = true)
            refresh()
        }
    }

    private fun currentDir(): File? {
        val repo = _uiState.value.repo ?: return null
        val relativePath = _uiState.value.relativePath
        return if (relativePath.isBlank()) File(repo.fullSavePath) else File(repo.fullSavePath, relativePath)
    }

    private suspend fun refresh() {
        val repo = _uiState.value.repo ?: return
        val relativePath = _uiState.value.relativePath
        val nodes = withContext(Dispatchers.IO) {
            val dir = if (relativePath.isBlank()) File(repo.fullSavePath) else File(repo.fullSavePath, relativePath)
            val children = dir.listFiles()?.toList() ?: emptyList()
            children
                .filter { it.name != ".git" || relativePath.isNotBlank() } // hide the .git folder at repo root
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .map {
                    FileNode(
                        name = it.name,
                        relativePath = if (relativePath.isBlank()) it.name else "$relativePath/${it.name}",
                        isDirectory = it.isDirectory,
                        sizeBytes = if (it.isFile) it.length() else 0L,
                    )
                }
        }
        _uiState.value = _uiState.value.copy(nodes = nodes, isLoading = false)
    }

    fun rename(node: FileNode, newName: String) {
        val repo = _uiState.value.repo ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val old = File(repo.fullSavePath, node.relativePath)
                val new = File(old.parentFile, newName)
                old.renameTo(new)
            }
            refresh()
        }
    }

    /** Deletes AND stages the removal in one step via `git rm` — a plain filesystem delete
     * followed by a later "Stage" tap on the Changes screen used to silently do nothing,
     * since JGit's AddCommand only ever adds content, never removes an index entry. This
     * is what was actually breaking "delete a file, push, expect it gone on GitHub." */
    fun delete(node: FileNode) {
        val repo = _uiState.value.repo ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            when (val opened = GitEngine.openRepo(repo.fullSavePath)) {
                is GitResult.Error -> _uiState.value = _uiState.value.copy(isBusy = false, message = opened.message)
                is GitResult.Success -> {
                    val git = opened.data
                    when (val result = GitEngine.removeFile(git, node.relativePath)) {
                        is GitResult.Success -> _uiState.value = _uiState.value.copy(
                            isBusy = false,
                            message = "Deleted ${node.name} — staged, ready to commit",
                        )
                        is GitResult.Error -> _uiState.value = _uiState.value.copy(isBusy = false, message = result.message)
                    }
                    git.close()
                    refresh()
                }
            }
        }
    }

    fun toggleSelection(node: FileNode) {
        val current = _uiState.value.selectedPaths
        _uiState.value = _uiState.value.copy(
            selectedPaths = if (node.relativePath in current) current - node.relativePath else current + node.relativePath,
        )
    }

    fun clearSelection() { _uiState.value = _uiState.value.copy(selectedPaths = emptySet()) }

    /** Stages every selected file/folder in one repo open — not [selectedPaths].size
     *  separate opens, which would be needlessly slow for a large selection. A
     *  directory's relative path stages everything under it, same as `git add <dir>`. */
    fun stageSelected() {
        val repo = _uiState.value.repo ?: return
        val paths = _uiState.value.selectedPaths
        if (paths.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            when (val opened = GitEngine.openRepo(repo.fullSavePath)) {
                is GitResult.Error -> _uiState.value = _uiState.value.copy(isBusy = false, message = opened.message)
                is GitResult.Success -> {
                    val git = opened.data
                    var failed = 0
                    for (path in paths) {
                        if (GitEngine.stageFile(git, path) is GitResult.Error) failed++
                    }
                    git.close()
                    _uiState.value = _uiState.value.copy(
                        isBusy = false, selectedPaths = emptySet(),
                        message = if (failed == 0) "Staged ${paths.size} item${if (paths.size == 1) "" else "s"}"
                        else "Staged ${paths.size - failed} — $failed failed",
                    )
                }
            }
        }
    }

    /** Same "delete + stage the removal" behavior as [delete], batched. */
    fun deleteSelected() {
        val repo = _uiState.value.repo ?: return
        val paths = _uiState.value.selectedPaths
        if (paths.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            when (val opened = GitEngine.openRepo(repo.fullSavePath)) {
                is GitResult.Error -> _uiState.value = _uiState.value.copy(isBusy = false, message = opened.message)
                is GitResult.Success -> {
                    val git = opened.data
                    var failed = 0
                    for (path in paths) {
                        if (GitEngine.removeFile(git, path) is GitResult.Error) failed++
                    }
                    git.close()
                    _uiState.value = _uiState.value.copy(
                        isBusy = false, selectedPaths = emptySet(),
                        message = if (failed == 0) "Deleted ${paths.size} item${if (paths.size == 1) "" else "s"} — staged"
                        else "Deleted ${paths.size - failed} — $failed failed",
                    )
                    refresh()
                }
            }
        }
    }

    fun createFile(name: String) {
        val dir = currentDir() ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val f = File(dir, name)
                    if (f.exists()) return@withContext "A file named \"$name\" already exists"
                    f.createNewFile()
                    null
                } catch (e: Exception) {
                    e.message ?: "Couldn't create file"
                }
            }
            _uiState.value = _uiState.value.copy(message = result ?: "Created $name")
            refresh()
        }
    }

    fun createFolder(name: String) {
        val dir = currentDir() ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val f = File(dir, name)
                if (f.exists()) "A folder named \"$name\" already exists"
                else if (f.mkdirs()) null
                else "Couldn't create folder"
            }
            _uiState.value = _uiState.value.copy(
                message = result ?: "Created $name — note: git won't track an empty folder until it has a file in it",
            )
            refresh()
        }
    }

    /** Copies one or more picked files (from the system file manager / photos app / etc,
     * via SAF) into the current folder, preserving their original filenames where possible. */
    fun importFiles(context: Context, uris: List<Uri>) {
        val dir = currentDir() ?: return
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            val imported = withContext(Dispatchers.IO) {
                var count = 0
                for (uri in uris) {
                    try {
                        val name = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "imported_file"
                        val dest = File(dir, name)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            dest.outputStream().use { output -> input.copyTo(output) }
                        }
                        count++
                    } catch (e: Exception) {
                        // Best-effort: skip a file that failed to copy rather than aborting
                        // the whole batch, since one bad URI shouldn't block the rest.
                    }
                }
                count
            }
            _uiState.value = _uiState.value.copy(isBusy = false, message = "Imported $imported file(s)")
            refresh()
        }
    }

    /** Recursively copies an entire picked folder (via SAF's "open document tree" picker)
     * into a new subfolder here, named after the picked folder. */
    fun importFolder(context: Context, treeUri: Uri) {
        val dir = currentDir() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            val result = withContext(Dispatchers.IO) {
                try {
                    val pickedRoot = DocumentFile.fromTreeUri(context, treeUri)
                        ?: return@withContext "Couldn't open that folder"
                    val destRoot = File(dir, pickedRoot.name ?: "imported_folder")
                    if (destRoot.exists()) return@withContext "A folder named \"${destRoot.name}\" already exists"
                    var fileCount = 0
                    fileCount = copyDocumentTree(context, pickedRoot, destRoot)
                    "Imported ${destRoot.name} ($fileCount file(s))"
                } catch (e: Exception) {
                    e.message ?: "Import failed"
                }
            }
            _uiState.value = _uiState.value.copy(isBusy = false, message = result)
            refresh()
        }
    }

    private fun copyDocumentTree(context: Context, source: DocumentFile, destDir: File): Int {
        destDir.mkdirs()
        var count = 0
        for (child in source.listFiles()) {
            if (child.isDirectory) {
                count += copyDocumentTree(context, child, File(destDir, child.name ?: "folder"))
            } else {
                val name = child.name ?: continue
                val destFile = File(destDir, name)
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                count++
            }
        }
        return count
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }

    fun push() {
        val repo = _uiState.value.repo ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            val credential = if (repo.credentialId != 0L) credRepo.getById(repo.credentialId) else null
            when (val openResult = GitEngine.openRepo(repo.fullSavePath)) {
                is GitResult.Error -> _uiState.value = _uiState.value.copy(isBusy = false, message = openResult.message)
                is GitResult.Success -> {
                    val git = openResult.data
                    when (val result = GitEngine.push(git, credential = credential)) {
                        is GitResult.Error -> {
                            repoRepo.markError(repo.id, result.message)
                            _uiState.value = _uiState.value.copy(isBusy = false, message = result.message)
                        }
                        is GitResult.Success -> {
                            repoRepo.markSyncSuccess(repo.id)
                            _uiState.value = _uiState.value.copy(isBusy = false, message = "Pushed")
                        }
                    }
                    git.close()
                }
            }
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    repoId: Long,
    relativePath: String,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenBlame: (String) -> Unit,
    vm: FileExplorerViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsState()
    val snack = remember { SnackbarHostState() }
    val context = LocalContext.current
    var nodePendingRename by remember { mutableStateOf<FileNode?>(null) }
    var nodePendingDelete by remember { mutableStateOf<FileNode?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    val importFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) vm.importFiles(context, uris) }

    val importFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let { vm.importFolder(context, it) } }

    LaunchedEffect(repoId, relativePath) { vm.load(repoId, relativePath) }
    LaunchedEffect(state.message) {
        state.message?.let { snack.showSnackbar(it); vm.dismissMessage() }
    }

    Scaffold(
        topBar = {
            if (state.selectedPaths.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${state.selectedPaths.size} selected", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, "Cancel selection") }
                    },
                    actions = {
                        IconButton(onClick = vm::stageSelected, enabled = !state.isBusy) {
                            Icon(Icons.Filled.Add, "Stage selected")
                        }
                        IconButton(onClick = { showBulkDeleteConfirm = true }, enabled = !state.isBusy) {
                            Icon(Icons.Filled.Delete, "Delete selected", tint = StatusDeleted)
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            state.relativePath.ifBlank { state.repo?.name ?: "Files" }.substringAfterLast('/'),
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showAddMenu = true }, enabled = !state.isBusy) {
                                Icon(Icons.Filled.Add, "Add")
                            }
                            DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                                DropdownMenuItem(text = { Text("New File") },
                                    onClick = { showAddMenu = false; showNewFileDialog = true },
                                    leadingIcon = { Icon(Icons.Filled.NoteAdd, null) })
                                DropdownMenuItem(text = { Text("New Folder") },
                                    onClick = { showAddMenu = false; showNewFolderDialog = true },
                                    leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Import Files") },
                                    onClick = { showAddMenu = false; importFilesLauncher.launch(arrayOf("*/*")) },
                                    leadingIcon = { Icon(Icons.Filled.UploadFile, null) })
                                DropdownMenuItem(text = { Text("Import Folder") },
                                    onClick = { showAddMenu = false; importFolderLauncher.launch(null) },
                                    leadingIcon = { Icon(Icons.Filled.DriveFolderUpload, null) })
                            }
                        }
                        IconButton(onClick = vm::push, enabled = !state.isBusy) {
                            if (state.isBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Filled.ArrowUpward, "Push")
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snack) { d -> Snackbar(d) } },
    ) { pad ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.nodes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Empty folder", color = StatusClean)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap + to create a file, or import from your file manager",
                        style = MaterialTheme.typography.bodySmall, color = StatusClean,
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize().padding(pad),
            ) {
                items(state.nodes, key = { it.relativePath }) { node ->
                    val isSelected = node.relativePath in state.selectedPaths
                    FileRow(
                        node = node,
                        isSelected = isSelected,
                        selectionActive = state.selectedPaths.isNotEmpty(),
                        onClick = {
                            if (state.selectedPaths.isNotEmpty()) {
                                vm.toggleSelection(node)
                            } else if (node.isDirectory) onOpenFolder(node.relativePath) else onOpenFile(node.relativePath)
                        },
                        onLongClick = { vm.toggleSelection(node) },
                        onRename = { nodePendingRename = node },
                        onDelete = { nodePendingDelete = node },
                        onBlame = { onOpenBlame(node.relativePath) },
                    )
                }
            }
        }

        if (state.isBusy) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }

    nodePendingRename?.let { node ->
        SingleInputDialog(
            title = "Rename ${node.name}",
            label = "New name",
            initial = node.name,
            onDismiss = { nodePendingRename = null },
            onConfirm = { newName -> vm.rename(node, newName); nodePendingRename = null },
        )
    }

    nodePendingDelete?.let { node ->
        ConfirmDialog(
            title = "Delete ${node.name}?",
            body = if (node.isDirectory) {
                "This deletes the folder and everything inside it, and stages the removal — commit and push to remove it from the remote too."
            } else {
                "This stages the removal — commit and push to remove it from the remote too. Can't be undone locally."
            },
            confirmLabel = "Delete",
            danger = true,
            onDismiss = { nodePendingDelete = null },
            onConfirm = { vm.delete(node); nodePendingDelete = null },
        )
    }

    if (showBulkDeleteConfirm) {
        val count = state.selectedPaths.size
        ConfirmDialog(
            title = "Delete $count item${if (count == 1) "" else "s"}?",
            body = "This stages every removal — commit and push to remove them from the remote too. Can't be undone locally.",
            confirmLabel = "Delete",
            danger = true,
            onDismiss = { showBulkDeleteConfirm = false },
            onConfirm = { vm.deleteSelected(); showBulkDeleteConfirm = false },
        )
    }

    if (showNewFileDialog) {
        SingleInputDialog(
            title = "New File",
            label = "File name",
            initial = "",
            onDismiss = { showNewFileDialog = false },
            onConfirm = { name -> vm.createFile(name); showNewFileDialog = false },
        )
    }

    if (showNewFolderDialog) {
        SingleInputDialog(
            title = "New Folder",
            label = "Folder name",
            initial = "",
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name -> vm.createFolder(name); showNewFolderDialog = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    node: FileNode,
    isSelected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onBlame: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        accent = when {
            isSelected -> CommandBlue
            node.isDirectory -> CommandBlue
            else -> MaterialTheme.colorScheme.outline
        },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionActive) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() }, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
            } else {
                Icon(
                    if (node.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = if (node.isDirectory) CommandBlue else StatusClean,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(node.name, style = MaterialTheme.typography.bodyMedium)
                if (!node.isDirectory) {
                    Text(formatSize(node.sizeBytes), style = MaterialTheme.typography.labelSmall, color = StatusClean)
                }
            }
            if (!selectionActive) {
                Box {
                    IconButton(onClick = { showMenu = true }, Modifier.size(32.dp)) {
                        Icon(Icons.Filled.MoreVert, null, Modifier.size(18.dp), tint = StatusClean)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; onRename() },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) })
                        if (!node.isDirectory) {
                            DropdownMenuItem(text = { Text("Blame") }, onClick = { showMenu = false; onBlame() },
                                leadingIcon = { Icon(Icons.Filled.History, null) })
                        }
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = StatusDeleted) })
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}
