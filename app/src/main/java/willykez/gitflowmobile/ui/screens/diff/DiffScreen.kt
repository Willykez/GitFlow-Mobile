package willykez.gitflowmobile.ui.screens.diff

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import willykez.gitflowmobile.App
import willykez.gitflowmobile.git.DiffHunk
import willykez.gitflowmobile.git.DiffLine
import willykez.gitflowmobile.git.DiffLineType
import willykez.gitflowmobile.git.GitEngine
import willykez.gitflowmobile.git.GitResult
import willykez.gitflowmobile.git.parseDiff
import willykez.gitflowmobile.ui.screens.editor.CodeLanguage
import willykez.gitflowmobile.ui.screens.editor.highlightText
import willykez.gitflowmobile.ui.screens.editor.languageForPath
import willykez.gitflowmobile.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder

data class DiffUiState(
    val title: String = "",
    val hunks: List<DiffHunk> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null,
)

class DiffViewModel(app: Application) : AndroidViewModel(app) {
    private val repoRepo = (app as App).repoRepository
    private val _state = MutableStateFlow(DiffUiState())
    val state: StateFlow<DiffUiState> = _state.asStateFlow()

    private var lastRepoId: Long = -1
    private var lastEncodedPath: String = ""
    private var lastMode: String = "false"

    fun load(repoId: Long, encodedPath: String, stagedOrCommit: String) {
        lastRepoId = repoId; lastEncodedPath = encodedPath; lastMode = stagedOrCommit
        viewModelScope.launch {
            val repo = repoRepo.getById(repoId) ?: return@launch
            val path = URLDecoder.decode(encodedPath, "UTF-8")
            _state.value = _state.value.copy(title = path, isLoading = true)
            when (val gr = GitEngine.openRepo(repo.fullSavePath)) {
                is GitResult.Error -> _state.value = _state.value.copy(isLoading = false, message = gr.message)
                is GitResult.Success -> {
                    val g = gr.data
                    val rawResult = when (stagedOrCommit) {
                        "true" -> GitEngine.getDiff(g, path, staged = true)
                        "false" -> GitEngine.getDiff(g, path, staged = false)
                        "commit" -> GitEngine.getCommitDiff(g, path) // path = sha here
                        else -> GitEngine.getDiff(g, path, staged = false)
                    }
                    g.close()
                    when (rawResult) {
                        is GitResult.Error -> _state.value = _state.value.copy(isLoading = false, message = rawResult.message)
                        is GitResult.Success -> {
                            val hunks = parseDiff(rawResult.data, fallbackPath = path)
                            _state.value = _state.value.copy(hunks = hunks, isLoading = false)
                        }
                    }
                }
            }
        }
    }

    /** Staging or discarding a hunk shifts every later hunk's line numbers in
     *  that file, so rather than patch local state (fragile — easy to get
     *  subtly out of sync with what the index/working tree actually now
     *  contain) this just re-fetches the diff, same as a manual refresh. */
    private fun reload() = load(lastRepoId, lastEncodedPath, lastMode)

    fun stageHunk(hunk: DiffHunk) {
        viewModelScope.launch {
            val repo = repoRepo.getById(lastRepoId) ?: return@launch
            when (val gr = GitEngine.openRepo(repo.fullSavePath)) {
                is GitResult.Error -> _state.value = _state.value.copy(message = gr.message)
                is GitResult.Success -> {
                    val g = gr.data
                    val result = GitEngine.stageHunk(g, hunk.filePath, hunk)
                    g.close()
                    result.onError { _state.value = _state.value.copy(message = it) }
                    reload()
                }
            }
        }
    }

    fun discardHunk(hunk: DiffHunk) {
        viewModelScope.launch {
            val repo = repoRepo.getById(lastRepoId) ?: return@launch
            when (val gr = GitEngine.openRepo(repo.fullSavePath)) {
                is GitResult.Error -> _state.value = _state.value.copy(message = gr.message)
                is GitResult.Success -> {
                    val g = gr.data
                    val result = GitEngine.discardHunk(g, hunk.filePath, hunk)
                    g.close()
                    result.onError { _state.value = _state.value.copy(message = it) }
                    reload()
                }
            }
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

    // For a multi-file commit diff, hunks aren't collapsed by default (every
    // file's changes stay visible, matching what a person expects from
    // "show me this commit"); for a single-file diff there's usually only
    // one hunk anyway so this has no effect either way.
    val collapsed = remember { mutableStateMapOf<Int, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 15.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.hunks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("No diff available", color = StatusClean)
            }
        } else {
            val hScroll = rememberScrollState()
            val syntaxColors = currentSyntaxColors()
            val showFileHeaders = state.hunks.map { it.filePath }.distinct().size > 1

            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                itemsIndexed(state.hunks) { hunkIdx, hunk ->
                    val isCollapsed = collapsed[hunkIdx] == true
                    val lang = remember(hunk.filePath) { languageForPath(hunk.filePath) }

                    if (showFileHeaders) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { collapsed[hunkIdx] = !isCollapsed }
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                hunk.filePath, style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1,
                            )
                            Icon(if (isCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, null, tint = StatusClean)
                        }
                    }
                    if (!isCollapsed) {
                        val canActOnHunk = stagedOrCommit == "false" // only the unstaged (index vs working tree) view supports hunk actions
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                hunk.headerText,
                                color = CommandBlue,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            if (canActOnHunk) {
                                TextButton(onClick = { vm.discardHunk(hunk) }) {
                                    Text("Discard", color = StatusDeleted, fontSize = 12.sp)
                                }
                                TextButton(onClick = { vm.stageHunk(hunk) }) {
                                    Text("Stage", color = StatusAdded, fontSize = 12.sp)
                                }
                            }
                        }
                        Column(Modifier.horizontalScroll(hScroll)) {
                            hunk.lines.forEach { line -> DiffLineRow(line, lang, syntaxColors) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffLineRow(line: DiffLine, lang: CodeLanguage, syntaxColors: SyntaxColorSet) {
    // Translucent tints over the normal surface (not opaque hardcoded colors) so this
    // reads correctly in both dark and light theme, and still shows the app's real
    // background color showing through — matching the rest of the app's glass look.
    val bg = when (line.type) {
        DiffLineType.ADDED -> StatusAdded.copy(alpha = 0.14f)
        DiffLineType.REMOVED -> StatusDeleted.copy(alpha = 0.14f)
        DiffLineType.CONTEXT -> Color.Transparent
    }
    val marker = when (line.type) {
        DiffLineType.ADDED -> "+"
        DiffLineType.REMOVED -> "−"
        DiffLineType.CONTEXT -> " "
    }
    val markerColor = when (line.type) {
        DiffLineType.ADDED -> StatusAdded
        DiffLineType.REMOVED -> StatusDeleted
        DiffLineType.CONTEXT -> StatusClean
    }
    val highlighted = remember(line.content, lang, syntaxColors) { highlightText(line.content, lang, syntaxColors) }

    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(vertical = 1.dp),
    ) {
        Text(
            line.oldLineNo?.toString() ?: "",
            color = StatusClean, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            textAlign = TextAlign.End, modifier = Modifier.width(34.dp).padding(end = 4.dp),
        )
        Text(
            line.newLineNo?.toString() ?: "",
            color = StatusClean, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            textAlign = TextAlign.End, modifier = Modifier.width(34.dp).padding(end = 6.dp),
        )
        Text(marker, color = markerColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Text(
            highlighted,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            softWrap = false,
        )
    }
}
