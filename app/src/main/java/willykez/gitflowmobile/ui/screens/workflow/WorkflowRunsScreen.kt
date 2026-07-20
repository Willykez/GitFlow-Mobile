package willykez.gitflowmobile.ui.screens.workflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import willykez.gitflowmobile.data.github.WorkflowJob
import willykez.gitflowmobile.data.github.WorkflowRun
import willykez.gitflowmobile.data.github.WorkflowStep
import willykez.gitflowmobile.data.github.isActiveStatus
import willykez.gitflowmobile.ui.components.GlassCard
import willykez.gitflowmobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowRunsScreen(
    repoId: Long,
    onBack: () -> Unit,
    vm: WorkflowRunsViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(repoId) { vm.load(repoId) }
    LaunchedEffect(state.message) { state.message?.let { snack.showSnackbar(it); vm.dismiss() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Actions", fontWeight = FontWeight.SemiBold)
                        if (state.repoLabel.isNotBlank()) {
                            Text(state.repoLabel, style = MaterialTheme.typography.labelSmall, color = StatusClean)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, "Refresh") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.notGitHub -> EmptyNotice(
                pad, Icons.Filled.Cloud,
                "Not a GitHub repo",
                "This repo's remote isn't on github.com, so there are no Actions runs to show here.",
            )
            state.noToken -> EmptyNotice(
                pad, Icons.Filled.Key,
                "No credential attached",
                "Attach a credential to this repo first — from the home screen, open this repo's \"⋮\" menu → \"Set credential…\". The same token used for push/pull is used here too.",
            )
            state.runs.isEmpty() -> EmptyNotice(pad, Icons.Filled.PlayCircle, "No runs yet", "Nothing has triggered a workflow run on this repo yet.")
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.runs, key = { it.id }) { run ->
                    RunRow(
                        run = run,
                        isExpanded = state.expandedRunId == run.id,
                        jobs = state.jobsByRun[run.id],
                        isLoadingJobs = state.isLoadingJobs && state.expandedRunId == run.id,
                        logForJobId = state.logForJobId,
                        logTail = state.logTail,
                        isLoadingLog = state.isLoadingLog,
                        onToggle = { vm.toggleExpand(run.id) },
                        onViewLog = { jobId -> vm.viewLog(jobId) },
                        onRerunFailed = { vm.rerunFailed(run.id) },
                        onRerunAll = { vm.rerunAll(run.id) },
                        onCancel = { vm.cancelRun(run.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyNotice(pad: PaddingValues, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Box(Modifier.fillMaxSize().padding(pad).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = StatusClean, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = StatusClean, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/** Color + icon for a run/job/step's current state — shared logic so the run row,
 *  job row, and step row all read the same status the same way. */
private fun statusVisual(status: String, conclusion: String?): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> = when {
    isActiveStatus(status) -> Icons.Filled.Sync to CommandBlue
    conclusion == "success" -> Icons.Filled.CheckCircle to StatusAdded
    conclusion == "failure" || conclusion == "timed_out" -> Icons.Filled.Cancel to StatusDeleted
    conclusion == "cancelled" -> Icons.Filled.RemoveCircle to StatusClean
    conclusion == "skipped" -> Icons.Filled.SkipNext to StatusClean
    else -> Icons.Filled.HourglassEmpty to StatusClean
}

private fun statusLabel(status: String, conclusion: String?): String = when {
    status == "queued" -> "Queued"
    status == "in_progress" -> "Running"
    status == "waiting" -> "Waiting"
    conclusion != null -> conclusion.replaceFirstChar { it.uppercase() }
    else -> status.replaceFirstChar { it.uppercase() }
}

@Composable
private fun RunRow(
    run: WorkflowRun,
    isExpanded: Boolean,
    jobs: List<WorkflowJob>?,
    isLoadingJobs: Boolean,
    logForJobId: Long?,
    logTail: String?,
    isLoadingLog: Boolean,
    onToggle: () -> Unit,
    onViewLog: (Long) -> Unit,
    onRerunFailed: () -> Unit,
    onRerunAll: () -> Unit,
    onCancel: () -> Unit,
) {
    val (icon, color) = statusVisual(run.status, run.conclusion)
    val hasFailure = run.conclusion == "failure"

    GlassCard(modifier = Modifier.fillMaxWidth(), accent = color) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        run.displayTitle.ifBlank { run.name },
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1,
                    )
                    Text(
                        "${run.headBranch} · #${run.runNumber} · ${statusLabel(run.status, run.conclusion)}",
                        style = MaterialTheme.typography.labelSmall, color = StatusClean,
                    )
                }
                Icon(
                    if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null,
                    tint = StatusClean, modifier = Modifier.size(20.dp),
                )
            }

            if (isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Column(Modifier.padding(12.dp)) {
                    // Actions
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isActiveStatus(run.status)) {
                            TextButton(onClick = onCancel) { Text("Cancel", color = StatusDeleted) }
                        } else {
                            if (hasFailure) TextButton(onClick = onRerunFailed) { Text("Rerun failed jobs") }
                            TextButton(onClick = onRerunAll) { Text("Rerun all") }
                        }
                    }

                    if (isLoadingJobs && jobs == null) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        jobs?.forEach { job ->
                            JobBlock(job, logForJobId, logTail, isLoadingLog, onViewLog)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobBlock(job: WorkflowJob, logForJobId: Long?, logTail: String?, isLoadingLog: Boolean, onViewLog: (Long) -> Unit) {
    val (jobIcon, jobColor) = statusVisual(job.status, job.conclusion)
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(jobIcon, null, tint = jobColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(job.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
        job.steps.forEach { step -> StepRow(step) }

        if (job.conclusion == "failure") {
            TextButton(onClick = { onViewLog(job.id) }, modifier = Modifier.padding(start = 20.dp)) {
                Icon(Icons.Filled.Article, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("View log")
            }
            if (logForJobId == job.id) {
                if (isLoadingLog) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                } else if (logTail != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            logTail,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: WorkflowStep) {
    val (icon, color) = statusVisual(step.status, step.conclusion)
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, top = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(step.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}
