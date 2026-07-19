package willykez.gitflowmobile.ui.screens.problems

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

data class ProblemItem(
    val filePath: String, // relative to repo root
    val line: Int,
    val marker: String,   // "TODO" / "FIXME" / "XXX" / "HACK"
    val text: String,     // the rest of the line, trimmed
)

data class ProblemsUiState(
    val isScanning: Boolean = true,
    val items: List<ProblemItem> = emptyList(),
    val message: String? = null,
)

private val MarkerPattern = Regex("""(?i)\b(TODO|FIXME|XXX|HACK)\b[:\s\-]*(.*)""")

/** Extensions unlikely to be readable text — skipped outright rather than
 *  read-and-discovered-empty, which would waste time on a large repo. */
private val BinaryExtensions = setOf(
    "png", "jpg", "jpeg", "gif", "ico", "webp", "bmp", "pdf", "zip", "jar", "aar",
    "apk", "so", "class", "dex", "ttf", "otf", "woff", "woff2", "mp3", "mp4", "mov",
    "exe", "bin", "keystore", "jks",
)

class ProblemsViewModel(app: Application) : AndroidViewModel(app) {
    private val repoRepo = (app as App).repoRepository
    private val _state = MutableStateFlow(ProblemsUiState())
    val state: StateFlow<ProblemsUiState> = _state.asStateFlow()

    fun scan(repoId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isScanning = true)
            val repo = repoRepo.getById(repoId)
            if (repo == null) {
                _state.value = _state.value.copy(isScanning = false, message = "Repo not found")
                return@launch
            }
            val items = withContext(Dispatchers.IO) { scanRepo(File(repo.fullSavePath)) }
            _state.value = _state.value.copy(isScanning = false, items = items)
        }
    }

    /** Walks every text file in the repo (skipping .git and obviously-binary
     *  extensions) looking for TODO/FIXME/XXX/HACK markers. Capped at 5,000
     *  files and 1 MB per file so a huge or generated-content-heavy repo
     *  can't hang the scan indefinitely — a repo that size is rare, and
     *  this can be re-run any time via the refresh action. */
    private fun scanRepo(root: File): List<ProblemItem> {
        if (!root.exists()) return emptyList()
        val results = mutableListOf<ProblemItem>()
        var scanned = 0

        root.walkTopDown()
            .onEnter { dir -> dir.name != ".git" }
            .forEach { file ->
                if (file.isDirectory) return@forEach
                if (scanned >= 5000) return@forEach
                if (file.extension.lowercase() in BinaryExtensions) return@forEach
                if (file.length() > 1_000_000L) return@forEach
                scanned++
                runCatching {
                    file.bufferedReader().useLines { lines ->
                        lines.forEachIndexed { idx, line ->
                            val m = MarkerPattern.find(line) ?: return@forEachIndexed
                            results.add(
                                ProblemItem(
                                    filePath = file.relativeTo(root).path,
                                    line = idx + 1,
                                    marker = m.groupValues[1].uppercase(),
                                    text = m.groupValues[2].trim().take(140),
                                )
                            )
                        }
                    }
                } // unreadable file (odd encoding, permissions) — skip it, don't fail the whole scan
            }
        return results.sortedWith(compareBy({ it.filePath }, { it.line }))
    }

    fun dismiss() { _state.value = _state.value.copy(message = null) }
}

@Composable
fun ProblemsScreen(
    repoId: Long,
    onBack: () -> Unit,
    onOpenFile: (path: String, line: Int) -> Unit,
    vm: ProblemsViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(repoId) { vm.scan(repoId) }
    LaunchedEffect(state.message) { state.message?.let { snack.showSnackbar(it); vm.dismiss() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Problems", fontWeight = FontWeight.SemiBold)
                        if (!state.isScanning) {
                            Text(
                                "${state.items.size} found", style = MaterialTheme.typography.labelSmall, color = StatusClean,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { vm.scan(repoId) }, enabled = !state.isScanning) {
                        Icon(Icons.Filled.Refresh, "Rescan")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        when {
            state.isScanning -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.items.isEmpty() -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.TaskAlt, null, tint = StatusAdded, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No TODOs or FIXMEs found", style = MaterialTheme.typography.titleMedium)
                    Text("Nothing marked in the code right now.", color = StatusClean, style = MaterialTheme.typography.bodySmall)
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items) { item -> ProblemRow(item) { onOpenFile(item.filePath, item.line) } }
            }
        }
    }
}

@Composable
private fun ProblemRow(item: ProblemItem, onClick: () -> Unit) {
    val markerColor = when (item.marker) {
        "FIXME" -> StatusDeleted
        "HACK" -> Color(0xFFD5A6FF)
        "XXX" -> CommandBlue
        else -> Amber // TODO
    }
    GlassCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), accent = markerColor) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .background(markerColor.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(item.marker, style = MaterialTheme.typography.labelSmall, color = markerColor, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${item.filePath}:${item.line}",
                    style = MaterialTheme.typography.labelSmall, color = StatusClean,
                    fontFamily = FontFamily.Monospace, maxLines = 1,
                )
            }
            if (item.text.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(item.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
        }
    }
}
