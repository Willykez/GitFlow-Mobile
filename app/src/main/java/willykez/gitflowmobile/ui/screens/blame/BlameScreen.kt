package willykez.gitflowmobile.ui.screens.blame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import willykez.gitflowmobile.git.BlameLine
import willykez.gitflowmobile.ui.theme.Amber
import willykez.gitflowmobile.ui.theme.StatusClean
import java.text.DateFormat
import java.util.Date

/** Assigns a stable, muted color per commit SHA so runs of lines from the same commit read
 * as one visual block without needing a comprehensive color-per-author scheme. */
private val blameColors = listOf(
    Color(0xFF2E4B3F), Color(0xFF4A3B2E), Color(0xFF2E3A4B), Color(0xFF4B2E45),
    Color(0xFF3F4B2E), Color(0xFF2E4B49), Color(0xFF4B382E), Color(0xFF3A2E4B),
)
private fun colorFor(sha: String): Color =
    if (sha.isBlank()) Color.DarkGray else blameColors[Math.floorMod(sha.hashCode(), blameColors.size)]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlameScreen(
    repoId: Long,
    path: String,
    onBack: () -> Unit,
    onOpenCommit: (sha: String) -> Unit,
    vm: BlameViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsState()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(repoId, path) { vm.load(repoId, path) }
    LaunchedEffect(state.message) { state.message?.let { snack.showSnackbar(it); vm.dismissMessage() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(path.substringAfterLast('/'), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                items(state.lines, key = { it.lineNumber }) { line ->
                    val previous = state.lines.getOrNull(line.lineNumber - 2)
                    val isNewBlock = previous == null || previous.commitSha != line.commitSha
                    BlameLineRow(line, showAttribution = isNewBlock, onClick = { onOpenCommit(line.commitSha) })
                }
            }
        }
    }
}

@Composable
private fun BlameLineRow(line: BlameLine, showAttribution: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(colorFor(line.commitSha)),
        )
        Column(
            Modifier.width(120.dp).padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
        ) {
            if (showAttribution) {
                Text(line.shortSha, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Amber)
                Text(line.authorName, fontSize = 10.sp, color = StatusClean, maxLines = 1)
                if (line.authorTime > 0) {
                    Text(
                        DateFormat.getDateInstance(DateFormat.SHORT).format(Date(line.authorTime)),
                        fontSize = 10.sp, color = StatusClean,
                    )
                }
            }
        }
        Text(
            "${line.lineNumber}",
            modifier = Modifier.width(36.dp).padding(top = 2.dp),
            fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = StatusClean,
        )
        Text(
            line.content,
            modifier = Modifier.weight(1f).padding(top = 2.dp, end = 8.dp),
            fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            maxLines = 1,
        )
    }
}
