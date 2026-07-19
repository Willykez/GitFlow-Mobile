package willykez.gitflowmobile.ui.screens.search

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import willykez.gitflowmobile.App
import willykez.gitflowmobile.ui.components.GlassCard
import willykez.gitflowmobile.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SearchMatch(val filePath: String, val line: Int, val lineText: String, val matchRange: IntRange)

data class RepoSearchUiState(
    val query: String = "",
    val caseSensitive: Boolean = false,
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<SearchMatch> = emptyList(),
    val message: String? = null,
)

/** Extensions unlikely to be readable text — same skip-list rationale as the Problems scan. */
private val BinaryExtensions = setOf(
    "png", "jpg", "jpeg", "gif", "ico", "webp", "bmp", "pdf", "zip", "jar", "aar",
    "apk", "so", "class", "dex", "ttf", "otf", "woff", "woff2", "mp3", "mp4", "mov",
    "exe", "bin", "keystore", "jks",
)

class RepoSearchViewModel(app: Application) : AndroidViewModel(app) {
    private val repoRepo = (app as App).repoRepository
    private val _state = MutableStateFlow(RepoSearchUiState())
    val state: StateFlow<RepoSearchUiState> = _state.asStateFlow()
    private var repoId: Long = -1

    fun setRepo(id: Long) { repoId = id }
    fun onQueryChanged(q: String) { _state.value = _state.value.copy(query = q) }
    fun toggleCaseSensitive() { _state.value = _state.value.copy(caseSensitive = !_state.value.caseSensitive) }

    /** Triggered explicitly (search button / IME action), not on every keystroke —
     *  this walks every text file in the repo, which is too heavy to re-run per
     *  character the way find-in-file's single-file search can. */
    fun search() {
        val query = _state.value.query
        if (query.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSearching = true, hasSearched = true)
            val repo = repoRepo.getById(repoId)
            if (repo == null) {
                _state.value = _state.value.copy(isSearching = false, message = "Repo not found")
                return@launch
            }
            val results = withContext(Dispatchers.IO) {
                searchRepo(File(repo.fullSavePath), query, _state.value.caseSensitive)
            }
            _state.value = _state.value.copy(isSearching = false, results = results)
        }
    }

    /** Same file-walking shape as the Problems scan (skip .git, skip binary-looking
     *  extensions, cap file count/size) — kept separate rather than shared for now
     *  since the match-finding logic differs (arbitrary query + range, not a fixed
     *  marker pattern), but both could fold onto one walker utility later. */
    private fun searchRepo(root: File, query: String, caseSensitive: Boolean): List<SearchMatch> {
        if (!root.exists()) return emptyList()
        val results = mutableListOf<SearchMatch>()
        var scanned = 0
        val needle = if (caseSensitive) query else query.lowercase()

        root.walkTopDown()
            .onEnter { dir -> dir.name != ".git" }
            .forEach { file ->
                if (file.isDirectory) return@forEach
                if (scanned >= 5000 || results.size >= 500) return@forEach
                if (file.extension.lowercase() in BinaryExtensions) return@forEach
                if (file.length() > 1_000_000L) return@forEach
                scanned++
                runCatching {
                    file.bufferedReader().useLines { lines ->
                        lines.forEachIndexed { idx, line ->
                            val haystack = if (caseSensitive) line else line.lowercase()
                            val col = haystack.indexOf(needle)
                            if (col >= 0) {
                                results.add(
                                    SearchMatch(
                                        filePath = file.relativeTo(root).path,
                                        line = idx + 1,
                                        lineText = line.trim().take(200),
                                        matchRange = col until (col + needle.length),
                                    )
                                )
                            }
                        }
                    }
                }
            }
        return results.sortedWith(compareBy({ it.filePath }, { it.line }))
    }

    fun dismiss() { _state.value = _state.value.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoSearchScreen(
    repoId: Long,
    onBack: () -> Unit,
    onOpenFile: (path: String, line: Int) -> Unit,
    vm: RepoSearchViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(repoId) { vm.setRepo(repoId) }
    LaunchedEffect(state.message) { state.message?.let { snack.showSnackbar(it); vm.dismiss() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search repo", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::onQueryChanged,
                    placeholder = { Text("Search file contents…") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { IconButton(onClick = vm::search) { Icon(Icons.Filled.Send, "Search") } },
                    modifier = Modifier.weight(1f),
                    keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Checkbox(checked = state.caseSensitive, onCheckedChange = { vm.toggleCaseSensitive() })
                Text("Match case", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                if (state.hasSearched && !state.isSearching) {
                    Text(
                        "${state.results.size} match${if (state.results.size == 1) "" else "es"}",
                        style = MaterialTheme.typography.labelSmall, color = StatusClean,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            when {
                state.isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                !state.hasSearched -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Search across every file in this repo", color = StatusClean, style = MaterialTheme.typography.bodyMedium)
                }
                state.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No matches", color = StatusClean)
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.results) { match ->
                        SearchResultRow(match) { onOpenFile(match.filePath, match.line) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(match: SearchMatch, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(10.dp)) {
            Text(
                "${match.filePath}:${match.line}",
                style = MaterialTheme.typography.labelSmall, color = CommandBlue,
                fontFamily = FontFamily.Monospace, maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                highlightMatch(match.lineText, match.matchRange),
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 1,
            )
        }
    }
}

private fun highlightMatch(text: String, range: IntRange): AnnotatedString {
    if (range.first < 0 || range.last >= text.length) return AnnotatedString(text)
    return AnnotatedString.Builder().apply {
        append(text.substring(0, range.first))
        withStyle(SpanStyle(background = Amber.copy(alpha = 0.35f), fontWeight = FontWeight.Bold)) {
            append(text.substring(range.first, range.last + 1))
        }
        append(text.substring(range.last + 1))
    }.toAnnotatedString()
}
