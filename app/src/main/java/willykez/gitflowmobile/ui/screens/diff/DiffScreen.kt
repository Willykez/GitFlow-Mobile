package willykez.gitflowmobile.ui.screens.diff

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import willykez.gitflowmobile.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder

data class DiffUiState(
    val title: String = "",
    val lines: List<DiffLine> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null,
)

data class DiffLine(val text: String, val type: DiffLineType)
enum class DiffLineType { ADDED, REMOVED, CONTEXT, HEADER, META }

class DiffViewModel(app: Application) : AndroidViewModel(app) {
    private val repoRepo = (app as App).repoRepository
    private val _state = MutableStateFlow(DiffUiState())
    val state: StateFlow<DiffUiState> = _state.asStateFlow()

    fun load(repoId: Long, encodedPath: String, stagedOrCommit: String) {
        viewModelScope.launch {
            val repo = repoRepo.getById(repoId) ?: return@launch
            val path = URLDecoder.decode(encodedPath, "UTF-8")
            _state.value = _state.value.copy(title = path, isLoading = true)
            when (val gr = GitEngine.openRepo(repo.fullSavePath)) {
                is GitResult.Error -> _state.value = _state.value.copy(isLoading = false, message = gr.message)
                is GitResult.Success -> {
                    val g = gr.data
                    val rawResult = when (stagedOrCommit) {
                        "true"   -> GitEngine.getDiff(g, path, staged = true)
                        "false"  -> GitEngine.getDiff(g, path, staged = false)
                        "commit" -> GitEngine.getCommitDiff(g, path) // path = sha here
                        else     -> GitEngine.getDiff(g, path, staged = false)
                    }
                    g.close()
                    when (rawResult) {
                        is GitResult.Error -> _state.value = _state.value.copy(isLoading = false, message = rawResult.message)
                        is GitResult.Success -> {
                            val lines = parseDiff(rawResult.data)
                            _state.value = _state.value.copy(lines = lines, isLoading = false)
                        }
                    }
                }
            }
        }
    }

    private fun parseDiff(raw: String): List<DiffLine> = raw.lines().map { line ->
        when {
            line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff ") ||
            line.startsWith("index ") || line.startsWith("new file") || line.startsWith("deleted file") ->
                DiffLine(line, DiffLineType.META)
            line.startsWith("@@") -> DiffLine(line, DiffLineType.HEADER)
            line.startsWith("+")  -> DiffLine(line, DiffLineType.ADDED)
            line.startsWith("-")  -> DiffLine(line, DiffLineType.REMOVED)
            else                  -> DiffLine(line, DiffLineType.CONTEXT)
        }
    }

    fun dismiss() { _state.value = _state.value.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffScreen(
    repoId: Long, encodedPath: String, stagedOrCommit: String,
    onBack: () -> Unit, vm: DiffViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(repoId, encodedPath) { vm.load(repoId, encodedPath, stagedOrCommit) }
    LaunchedEffect(state.message) { state.message?.let { snack.showSnackbar(it); vm.dismiss() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 15.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface, navigationIconContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.lines.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("No diff available", color = StatusClean)
            }
        } else {
            val hScroll = rememberScrollState()
            LazyColumn(
                Modifier.fillMaxSize().padding(pad).horizontalScroll(hScroll),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                itemsIndexed(state.lines) { _, line ->
                    DiffLineRow(line)
                }
            }
        }
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val bg = when (line.type) {
        DiffLineType.ADDED   -> Color(0xFF1A3A1A)
        DiffLineType.REMOVED -> Color(0xFF3A1A1A)
        DiffLineType.HEADER  -> Color(0xFF1A2A3A)
        DiffLineType.META    -> Color(0xFF1E1E2E)
        DiffLineType.CONTEXT -> Color.Transparent
    }
    val fg = when (line.type) {
        DiffLineType.ADDED   -> StatusAdded
        DiffLineType.REMOVED -> StatusDeleted
        DiffLineType.HEADER  -> Color(0xFF88AACC)
        DiffLineType.META    -> StatusClean
        DiffLineType.CONTEXT -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 1.dp)
    ) {
        Text(
            text = line.text,
            color = fg,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            softWrap = false,
        )
    }
}
