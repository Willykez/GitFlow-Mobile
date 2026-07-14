package willykez.gitflowmobile.ui.screens.editor

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import willykez.gitflowmobile.App
import willykez.gitflowmobile.data.db.entity.RepoEntity
import willykez.gitflowmobile.git.GitEngine
import willykez.gitflowmobile.git.GitResult
import willykez.gitflowmobile.ui.theme.StatusClean
import willykez.gitflowmobile.ui.theme.StatusDeleted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Files bigger than this open in a "too large to edit" notice instead of loading into the text field. */
private const val MAX_EDITABLE_BYTES = 2 * 1024 * 1024 // 2 MB

data class EditorUiState(
    val repo: RepoEntity? = null,
    val relativePath: String = "",
    val text: TextFieldValue = TextFieldValue(""),
    val isLoading: Boolean = true,
    val isBinaryOrTooLarge: Boolean = false,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
)

class FileEditorViewModel(app: Application) : AndroidViewModel(app) {
    private val appRef = app as App
    private val repoRepo = appRef.repoRepository
    private val credRepo = appRef.credentialRepository

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    fun load(repoId: Long, relativePath: String) {
        viewModelScope.launch {
            val repo = repoRepo.getById(repoId) ?: return@launch
            _uiState.value = _uiState.value.copy(repo = repo, relativePath = relativePath, isLoading = true)

            val file = File(repo.fullSavePath, relativePath)
            val result = withContext(Dispatchers.IO) {
                if (!file.exists() || file.length() > MAX_EDITABLE_BYTES) return@withContext null
                val bytes = file.readBytes()
                // Crude but effective binary check: a NUL byte essentially never
                // shows up in real text files, but is common in binary formats.
                if (bytes.contains(0)) return@withContext null
                bytes.toString(Charsets.UTF_8)
            }

            if (result == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, isBinaryOrTooLarge = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    text = TextFieldValue(result),
                    isLoading = false,
                    isBinaryOrTooLarge = false,
                )
            }
        }
    }

    fun onTextChanged(newValue: TextFieldValue) {
        _uiState.value = _uiState.value.copy(text = newValue, isDirty = true)
    }

    fun selectAll() {
        val current = _uiState.value.text
        _uiState.value = _uiState.value.copy(
            text = current.copy(selection = TextRange(0, current.text.length))
        )
    }

    fun save() {
        val repo = _uiState.value.repo ?: return
        val relativePath = _uiState.value.relativePath
        val content = _uiState.value.text.text

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            withContext(Dispatchers.IO) {
                File(repo.fullSavePath, relativePath).writeText(content)
            }
            _uiState.value = _uiState.value.copy(isSaving = false, isDirty = false, message = "Saved")
        }
    }

    /** Save, then stage + commit + push this one file — the quick path from "edited a file" to "pushed it". */
    fun saveCommitAndPush(commitMessage: String, authorName: String, authorEmail: String) {
        val repo = _uiState.value.repo ?: return
        val relativePath = _uiState.value.relativePath
        val content = _uiState.value.text.text

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            withContext(Dispatchers.IO) {
                File(repo.fullSavePath, relativePath).writeText(content)
            }

            when (val openResult = GitEngine.openRepo(repo.fullSavePath)) {
                is GitResult.Error -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, isDirty = false, message = "Saved, but couldn't open repo: ${openResult.message}")
                }
                is GitResult.Success -> {
                    val git = openResult.data
                    val stageResult = GitEngine.stageFile(git, relativePath)
                    if (stageResult is GitResult.Error) {
                        _uiState.value = _uiState.value.copy(isSaving = false, isDirty = false, message = "Saved, but staging failed: ${stageResult.message}")
                        git.close()
                        return@launch
                    }

                    val commitResult = GitEngine.commit(git, commitMessage, authorName, authorEmail)
                    if (commitResult is GitResult.Error) {
                        _uiState.value = _uiState.value.copy(isSaving = false, isDirty = false, message = "Saved + staged, but commit failed: ${commitResult.message}")
                        git.close()
                        return@launch
                    }

                    val credential = if (repo.credentialId != 0L) credRepo.getById(repo.credentialId) else null
                    when (val pushResult = GitEngine.push(git, credential = credential)) {
                        is GitResult.Error -> {
                            repoRepo.markError(repo.id, pushResult.message)
                            _uiState.value = _uiState.value.copy(isSaving = false, isDirty = false, message = "Committed, but push failed: ${pushResult.message}")
                        }
                        is GitResult.Success -> {
                            repoRepo.markSyncSuccess(repo.id)
                            _uiState.value = _uiState.value.copy(isSaving = false, isDirty = false, message = "Saved, committed, and pushed")
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
fun FileEditorScreen(
    repoId: Long,
    relativePath: String,
    onBack: () -> Unit,
    vm: FileEditorViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsState()
    val snack = remember { SnackbarHostState() }
    var showPushDialog by remember { mutableStateOf(false) }

    LaunchedEffect(repoId, relativePath) { vm.load(repoId, relativePath) }
    LaunchedEffect(state.message) {
        state.message?.let { snack.showSnackbar(it); vm.dismissMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(relativePath.substringAfterLast('/'), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (!state.isBinaryOrTooLarge) {
                        IconButton(onClick = vm::selectAll) { Icon(Icons.Filled.SelectAll, "Select all") }
                        IconButton(onClick = vm::save, enabled = state.isDirty && !state.isSaving) {
                            if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Filled.Save, "Save")
                        }
                        IconButton(onClick = { showPushDialog = true }, enabled = !state.isSaving) {
                            Icon(Icons.Filled.ArrowUpward, "Save, commit & push")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snack) { d -> Snackbar(d) } },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(androidx.compose.ui.Alignment.Center))
                state.isBinaryOrTooLarge -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.Warning, null, tint = StatusClean, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Can't open this file here", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "It's either a binary file or larger than 2 MB — this built-in editor is for text/source files.",
                        style = MaterialTheme.typography.bodyMedium, color = StatusClean,
                    )
                }
                else -> OutlinedTextField(
                    value = state.text,
                    onValueChange = vm::onTextChanged,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                )
            }
        }
    }

    if (showPushDialog) {
        var commitMessage by remember { mutableStateOf("Update ${relativePath.substringAfterLast('/')}") }
        AlertDialog(
            onDismissRequest = { showPushDialog = false },
            title = { Text("Save, commit & push") },
            text = {
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    label = { Text("Commit message") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPushDialog = false
                    vm.saveCommitAndPush(commitMessage, "GitFlow Mobile User", "gitflowmobile@users.noreply.github.com")
                }) { Text("Push") }
            },
            dismissButton = { TextButton(onClick = { showPushDialog = false }) { Text("Cancel") } },
        )
    }
}
