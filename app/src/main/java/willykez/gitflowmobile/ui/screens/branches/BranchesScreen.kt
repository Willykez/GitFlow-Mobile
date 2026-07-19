package willykez.gitflowmobile.ui.screens.branches

import android.app.Application
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import willykez.gitflowmobile.App
import willykez.gitflowmobile.git.BranchInfo
import willykez.gitflowmobile.git.GitEngine
import willykez.gitflowmobile.git.GitResult
import willykez.gitflowmobile.ui.components.GlassCard
import willykez.gitflowmobile.ui.screens.changes.ConfirmDialog
import willykez.gitflowmobile.ui.screens.changes.SingleInputDialog
import willykez.gitflowmobile.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.eclipse.jgit.api.Git

data class BranchesUiState(
    val repoName: String = "", val branches: List<BranchInfo> = emptyList(),
    val isLoading: Boolean = true, val isWorking: Boolean = false, val message: String? = null,
)

class BranchesViewModel(app: Application) : AndroidViewModel(app) {
    private val appRef = app as App
    private val repoRepo = appRef.repoRepository
    private val credRepo = appRef.credentialRepository
    private val _state = MutableStateFlow(BranchesUiState())
    val state: StateFlow<BranchesUiState> = _state.asStateFlow()
    private var git: Git? = null
    private var repoId: Long = -1

    fun load(id: Long) {
        repoId = id
        viewModelScope.launch {
            val repo = repoRepo.getById(id) ?: return@launch
            _state.value = _state.value.copy(repoName = repo.name, isLoading = true)
            when (val gr = GitEngine.openRepo(repo.fullSavePath)) {
                is GitResult.Error -> err(gr.message)
                is GitResult.Success -> { git = gr.data; refresh() }
            }
        }
    }

    private suspend fun refresh() {
        val g = git ?: return
        when (val r = GitEngine.listBranches(g)) {
            is GitResult.Error -> err(r.message)
            is GitResult.Success -> _state.value = _state.value.copy(branches = r.data, isLoading = false)
        }
    }

    fun checkout(name: String) = op { g ->
        val r = GitEngine.checkoutBranch(g, name)
        if (r is GitResult.Success) ok("Checked out $name") else err((r as GitResult.Error).message)
    }

    fun checkoutRemote(name: String) = op { g ->
        val r = GitEngine.checkoutRemoteBranch(g, name)
        if (r is GitResult.Success) ok("Checked out $name locally") else err((r as GitResult.Error).message)
    }

    fun createBranch(name: String) = op { g ->
        val r = GitEngine.createBranch(g, name)
        if (r is GitResult.Success) ok("Created $name") else err((r as GitResult.Error).message)
    }

    fun deleteBranch(name: String, force: Boolean = false) = op { g ->
        val r = GitEngine.deleteBranch(g, name, force)
        if (r is GitResult.Success) ok("Deleted $name") else err((r as GitResult.Error).message)
    }

    fun renameBranch(old: String, new: String) = op { g ->
        val r = GitEngine.renameBranch(g, old, new)
        if (r is GitResult.Success) ok("Renamed to $new") else err((r as GitResult.Error).message)
    }

    fun mergeBranch(name: String) = op { g ->
        when (val r = GitEngine.mergeBranch(g, name)) {
            is GitResult.Success -> ok(r.data)
            is GitResult.Error -> err(r.message)
        }
    }

    fun pushDeleteRemote(branch: String) = op { g ->
        val repo = repoRepo.getById(repoId) ?: return@op
        val cred = if (repo.credentialId != 0L) credRepo.getById(repo.credentialId) else null
        when (val r = GitEngine.pushDeleteBranch(g, branch = branch.substringAfter("/"), credential = cred)) {
            is GitResult.Success -> ok(r.data)
            is GitResult.Error -> err(r.message)
        }
    }

    fun dismiss() { _state.value = _state.value.copy(message = null) }

    private fun op(block: suspend (Git) -> Unit) {
        val g = git ?: run { err("Repo not open"); return }
        viewModelScope.launch {
            _state.value = _state.value.copy(isWorking = true)
            block(g)
            refresh()
            _state.value = _state.value.copy(isWorking = false)
        }
    }
    private fun err(m: String) { _state.value = _state.value.copy(message = m, isLoading = false, isWorking = false) }
    private fun ok(m: String) { _state.value = _state.value.copy(message = m) }
    override fun onCleared() { super.onCleared(); git?.close() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchesScreen(repoId: Long, onBack: () -> Unit, vm: BranchesViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }
    var branchToDelete by remember { mutableStateOf<BranchInfo?>(null) }
    var branchToRename by remember { mutableStateOf<BranchInfo?>(null) }

    LaunchedEffect(repoId) { vm.load(repoId) }
    LaunchedEffect(state.message) { state.message?.let { snack.showSnackbar(it); vm.dismiss() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Branches", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, "New branch") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface, navigationIconContentColor = MaterialTheme.colorScheme.onSurface, actionIconContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.branches.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.AccountTree, null, Modifier.size(48.dp), tint = StatusClean)
                    Spacer(Modifier.height(12.dp))
                    Text("No branches found", style = MaterialTheme.typography.titleMedium)
                    Text("Tap + to create one", style = MaterialTheme.typography.bodySmall, color = StatusClean)
                }
            }
        } else {
            val local = state.branches.filter { !it.isRemote }
            val remote = state.branches.filter { it.isRemote }
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { SectionLabel("LOCAL (${local.size})") }
                items(local, key = { it.fullRef }) { b ->
                    BranchRow(b, onCheckout = { vm.checkout(b.name) }, onDelete = { branchToDelete = b },
                        onRename = { branchToRename = b }, onMerge = { vm.mergeBranch(b.name) })
                }
                item { Spacer(Modifier.height(8.dp)); SectionLabel("REMOTE (${remote.size})") }
                items(remote, key = { it.fullRef }) { b ->
                    BranchRow(b, onCheckout = { vm.checkoutRemote(b.name) }, onDelete = { vm.pushDeleteRemote(b.name) },
                        onRename = null, onMerge = null)
                }
            }
        }
    }

    if (showCreate) SingleInputDialog("New Branch", "Branch name", "",
        onConfirm = { vm.createBranch(it); showCreate = false }, onDismiss = { showCreate = false })
    branchToDelete?.let { b ->
        ConfirmDialog("Delete ${b.name}?", "This action cannot be undone.", "Delete", danger = true,
            onConfirm = { if (b.isRemote) vm.pushDeleteRemote(b.name) else vm.deleteBranch(b.name); branchToDelete = null },
            onDismiss = { branchToDelete = null })
    }
    branchToRename?.let { b ->
        SingleInputDialog("Rename Branch", "New name", b.name,
            onConfirm = { vm.renameBranch(b.name, it); branchToRename = null }, onDismiss = { branchToRename = null })
    }
}

@Composable private fun SectionLabel(t: String) {
    Text(t, style = MaterialTheme.typography.labelSmall, color = StatusClean, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchRow(b: BranchInfo, onCheckout: () -> Unit, onDelete: () -> Unit, onRename: (() -> Unit)?, onMerge: (() -> Unit)?) {
    var expanded by remember { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth(), accent = if (b.isCurrent) CommandBlue else null) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (b.isCurrent) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                null, Modifier.size(16.dp), tint = if (b.isCurrent) Amber else StatusClean)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(b.name, fontWeight = if (b.isCurrent) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium)
                if (b.ahead > 0 || b.behind > 0) {
                    Text("↑${b.ahead} ↓${b.behind}", style = MaterialTheme.typography.labelSmall, color = Amber)
                }
            }
            Box {
                IconButton(onClick = { expanded = true }, Modifier.size(32.dp)) { Icon(Icons.Filled.MoreVert, null, Modifier.size(16.dp), tint = StatusClean) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text(if (b.isRemote) "Checkout (local)" else "Checkout") }, onClick = { expanded = false; onCheckout() })
                    onMerge?.let { DropdownMenuItem(text = { Text("Merge into current") }, onClick = { expanded = false; it() }) }
                    onRename?.let { DropdownMenuItem(text = { Text("Rename") }, onClick = { expanded = false; it() }) }
                    DropdownMenuItem(text = { Text("Delete", color = StatusDeleted) }, onClick = { expanded = false; onDelete() })
                }
            }
        }
    }
}
