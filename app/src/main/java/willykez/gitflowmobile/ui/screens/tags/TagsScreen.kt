package willykez.gitflowmobile.ui.screens.tags

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import willykez.gitflowmobile.App
import willykez.gitflowmobile.git.GitEngine
import willykez.gitflowmobile.git.GitResult
import willykez.gitflowmobile.git.TagInfo
import willykez.gitflowmobile.ui.components.GlassCard
import willykez.gitflowmobile.ui.screens.changes.ConfirmDialog
import willykez.gitflowmobile.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.eclipse.jgit.api.Git

data class TagsUiState(
    val tags: List<TagInfo> = emptyList(),
    val isLoading: Boolean = true, val isWorking: Boolean = false, val message: String? = null,
)

class TagsViewModel(app: Application) : AndroidViewModel(app) {
    private val appRef = app as App
    private val repoRepo = appRef.repoRepository
    private val credRepo = appRef.credentialRepository
    private val _state = MutableStateFlow(TagsUiState())
    val state: StateFlow<TagsUiState> = _state.asStateFlow()
    private var git: Git? = null
    private var repoId: Long = -1

    fun load(id: Long) {
        repoId = id
        viewModelScope.launch {
            val repo = repoRepo.getById(id) ?: return@launch
            when (val gr = GitEngine.openRepo(repo.fullSavePath)) {
                is GitResult.Error -> err(gr.message)
                is GitResult.Success -> { git = gr.data; refresh() }
            }
        }
    }

    private suspend fun refresh() {
        val g = git ?: return
        when (val r = GitEngine.listTags(g)) {
            is GitResult.Success -> _state.value = _state.value.copy(tags = r.data, isLoading = false)
            is GitResult.Error -> err(r.message)
        }
    }

    fun createTag(name: String, message: String) = op { g ->
        when (val r = GitEngine.createTag(g, name, message)) {
            is GitResult.Success -> ok("Created tag $name")
            is GitResult.Error -> err(r.message)
        }
    }

    fun deleteTag(name: String) = op { g ->
        when (val r = GitEngine.deleteTag(g, name)) {
            is GitResult.Success -> ok("Deleted tag $name")
            is GitResult.Error -> err(r.message)
        }
    }

    fun pushTags() = op { g ->
        val repo = repoRepo.getById(repoId) ?: return@op
        val cred = if (repo.credentialId != 0L) credRepo.getById(repo.credentialId) else null
        when (val r = GitEngine.pushTags(g, cred)) {
            is GitResult.Success -> ok("Tags pushed")
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
fun TagsScreen(repoId: Long, onBack: () -> Unit, vm: TagsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }
    var tagToDelete by remember { mutableStateOf<TagInfo?>(null) }
    var newTagName by remember { mutableStateOf("") }
    var newTagMsg by remember { mutableStateOf("") }

    LaunchedEffect(repoId) { vm.load(repoId) }
    LaunchedEffect(state.message) { state.message?.let { snack.showSnackbar(it); vm.dismiss() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tags", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = vm::pushTags, enabled = state.tags.isNotEmpty()) { Icon(Icons.Filled.Upload, "Push tags") }
                    IconButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, "Create tag") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface, navigationIconContentColor = MaterialTheme.colorScheme.onSurface, actionIconContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.tags.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Label, null, Modifier.size(48.dp), tint = StatusClean)
                    Spacer(Modifier.height(12.dp))
                    Text("No tags yet", color = StatusClean)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showCreate = true }) { Text("Create Tag") }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.tags, key = { it.name }) { t ->
                    TagRow(t, onDelete = { tagToDelete = t })
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(onDismissRequest = { showCreate = false },
            title = { Text("Create Tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newTagName, onValueChange = { newTagName = it }, label = { Text("Tag name (e.g. v1.0.0)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = newTagMsg, onValueChange = { newTagMsg = it }, label = { Text("Message (leave blank for lightweight)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { vm.createTag(newTagName, newTagMsg); showCreate = false; newTagName = ""; newTagMsg = "" }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } })
    }

    tagToDelete?.let { t ->
        ConfirmDialog("Delete tag ${t.name}?", "This cannot be undone. Remote tags must be deleted separately.", "Delete", danger = true,
            onConfirm = { vm.deleteTag(t.name); tagToDelete = null }, onDismiss = { tagToDelete = null })
    }
}

@Composable
private fun TagRow(t: TagInfo, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Label, null, Modifier.size(20.dp), tint = Amber)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                if (t.sha.isNotBlank()) Text(t.sha.take(12), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = StatusClean)
            }
            Box {
                IconButton(onClick = { expanded = true }, Modifier.size(32.dp)) { Icon(Icons.Filled.MoreVert, null, Modifier.size(16.dp), tint = StatusClean) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Delete", color = StatusDeleted) }, onClick = { expanded = false; onDelete() })
                }
            }
        }
    }
}
