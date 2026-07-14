package willykez.gitflowmobile.ui.screens.remote

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
import willykez.gitflowmobile.git.GitEngine
import willykez.gitflowmobile.git.GitResult
import willykez.gitflowmobile.git.RemoteInfo
import willykez.gitflowmobile.ui.screens.changes.ConfirmDialog
import willykez.gitflowmobile.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.eclipse.jgit.api.Git

data class RemoteUiState(
    val remotes: List<RemoteInfo> = emptyList(),
    val isLoading: Boolean = true, val isWorking: Boolean = false, val message: String? = null,
)

class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val repoRepo = (app as App).repoRepository
    private val _state = MutableStateFlow(RemoteUiState())
    val state: StateFlow<RemoteUiState> = _state.asStateFlow()
    private var git: Git? = null

    fun load(repoId: Long) {
        viewModelScope.launch {
            val repo = repoRepo.getById(repoId) ?: return@launch
            when (val gr = GitEngine.openRepo(repo.fullSavePath)) {
                is GitResult.Error -> err(gr.message)
                is GitResult.Success -> { git = gr.data; refresh() }
            }
        }
    }

    private suspend fun refresh() {
        val g = git ?: return
        when (val r = GitEngine.listRemotes(g)) {
            is GitResult.Success -> _state.value = _state.value.copy(remotes = r.data, isLoading = false)
            is GitResult.Error -> err(r.message)
        }
    }

    fun addRemote(name: String, url: String) = op { g ->
        when (val r = GitEngine.addRemote(g, name, url)) {
            is GitResult.Success -> ok("Added remote $name")
            is GitResult.Error -> err(r.message)
        }
    }

    fun removeRemote(name: String) = op { g ->
        when (val r = GitEngine.removeRemote(g, name)) {
            is GitResult.Success -> ok("Removed remote $name")
            is GitResult.Error -> err(r.message)
        }
    }

    fun setUrl(name: String, url: String) = op { g ->
        when (val r = GitEngine.setRemoteUrl(g, name, url)) {
            is GitResult.Success -> ok("Updated $name URL")
            is GitResult.Error -> err(r.message)
        }
    }

    fun dismiss() { _state.value = _state.value.copy(message = null) }
    private fun err(m: String) { _state.value = _state.value.copy(message = m, isLoading = false, isWorking = false) }
    private fun ok(m: String) { _state.value = _state.value.copy(message = m) }
    private fun op(block: suspend (Git) -> Unit) {
        val g = git ?: run { err("Repo not open"); return }
        viewModelScope.launch { _state.value = _state.value.copy(isWorking = true); block(g); refresh(); _state.value = _state.value.copy(isWorking = false) }
    }
    override fun onCleared() { super.onCleared(); git?.close() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(repoId: Long, onBack: () -> Unit, vm: RemoteViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var remoteToDelete by remember { mutableStateOf<RemoteInfo?>(null) }
    var remoteToEdit by remember { mutableStateOf<RemoteInfo?>(null) }
    var newName by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }

    LaunchedEffect(repoId) { vm.load(repoId) }
    LaunchedEffect(state.message) { state.message?.let { snack.showSnackbar(it); vm.dismiss() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remotes", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "Add remote") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface, navigationIconContentColor = MaterialTheme.colorScheme.onSurface, actionIconContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.remotes.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                            Text("No remotes configured", color = StatusClean)
                        }
                    }
                }
                items(state.remotes, key = { it.name }) { r ->
                    RemoteRow(r, onEdit = { remoteToEdit = r }, onDelete = { remoteToDelete = r })
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(onDismissRequest = { showAdd = false },
            title = { Text("Add Remote") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name (e.g. origin)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = newUrl, onValueChange = { newUrl = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { vm.addRemote(newName, newUrl); showAdd = false; newName = ""; newUrl = "" }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } })
    }

    remoteToEdit?.let { r ->
        var editUrl by remember(r) { mutableStateOf(r.fetchUrl) }
        AlertDialog(onDismissRequest = { remoteToEdit = null },
            title = { Text("Edit ${r.name}") },
            text = { OutlinedTextField(value = editUrl, onValueChange = { editUrl = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.setUrl(r.name, editUrl); remoteToEdit = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { remoteToEdit = null }) { Text("Cancel") } })
    }

    remoteToDelete?.let { r ->
        ConfirmDialog("Remove ${r.name}?", "Remote '${r.name}' will be removed from this repo.", "Remove", danger = true,
            onConfirm = { vm.removeRemote(r.name); remoteToDelete = null },
            onDismiss = { remoteToDelete = null })
    }
}

@Composable
private fun RemoteRow(r: RemoteInfo, onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Cloud, null, Modifier.size(20.dp), tint = Amber)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(r.fetchUrl, style = MaterialTheme.typography.bodySmall, color = StatusClean, maxLines = 1)
                if (r.pushUrl != r.fetchUrl && r.pushUrl.isNotBlank())
                    Text("push: ${r.pushUrl}", style = MaterialTheme.typography.labelSmall, color = StatusClean, maxLines = 1)
            }
            Box {
                IconButton(onClick = { expanded = true }, Modifier.size(32.dp)) { Icon(Icons.Filled.MoreVert, null, Modifier.size(16.dp), tint = StatusClean) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Edit URL") }, onClick = { expanded = false; onEdit() })
                    DropdownMenuItem(text = { Text("Remove", color = StatusDeleted) }, onClick = { expanded = false; onDelete() })
                }
            }
        }
    }
}
