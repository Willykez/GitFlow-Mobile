package willykez.gitflowmobile.ui.screens.workflow

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import willykez.gitflowmobile.App
import willykez.gitflowmobile.data.PublicStorage
import willykez.gitflowmobile.data.github.GitHubActionsApi
import willykez.gitflowmobile.data.github.GitHubResult
import willykez.gitflowmobile.data.github.WorkflowArtifact
import willykez.gitflowmobile.data.github.WorkflowJob
import willykez.gitflowmobile.data.github.WorkflowRun
import willykez.gitflowmobile.data.github.parseGitHubOwnerRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

data class WorkflowRunsUiState(
    val repoLabel: String = "",
    val isLoading: Boolean = true,
    val runs: List<WorkflowRun> = emptyList(),
    val expandedRunId: Long? = null,
    val jobsByRun: Map<Long, List<WorkflowJob>> = emptyMap(),
    val isLoadingJobs: Boolean = false,
    val logForJobId: Long? = null,
    val logTail: String? = null,
    val isLoadingLog: Boolean = false,
    val message: String? = null,
    /** Set instead of loading runs when the repo isn't hosted on github.com — there's
     *  nothing this screen can show for a GitLab/Gitea/self-hosted remote. */
    val notGitHub: Boolean = false,
    /** Set when the repo has no credential attached — same PAT already used for git
     *  push/pull is what's used here too, so without one there's nothing to auth with. */
    val noToken: Boolean = false,
    val artifactsByRun: Map<Long, List<WorkflowArtifact>> = emptyMap(),
    val downloadingArtifactId: Long? = null,
    /** One-shot signal: a content:// URI ready to hand to the system installer.
     *  The Composable launches the install Intent when this becomes non-null, then
     *  calls [WorkflowRunsViewModel.consumeInstallUri] so it doesn't re-fire on the
     *  next recomposition (e.g. after a config change). */
    val installUri: Uri? = null,
)

class WorkflowRunsViewModel(app: Application) : AndroidViewModel(app) {
    private val appRef = app as App
    private val repoRepo = appRef.repoRepository
    private val credRepo = appRef.credentialRepository

    private val _state = MutableStateFlow(WorkflowRunsUiState())
    val state: StateFlow<WorkflowRunsUiState> = _state.asStateFlow()

    private var owner = ""
    private var repoSlug = ""
    private var token = ""
    private var pollJob: Job? = null

    fun load(repoId: Long) {
        viewModelScope.launch {
            val repo = repoRepo.getById(repoId) ?: return@launch
            val ownerRepo = parseGitHubOwnerRepo(repo.cloneUrl)
            if (ownerRepo == null) {
                _state.value = _state.value.copy(isLoading = false, notGitHub = true)
                return@launch
            }
            owner = ownerRepo.first
            repoSlug = ownerRepo.second
            val cred = if (repo.credentialId != 0L) credRepo.getById(repo.credentialId) else null
            if (cred == null) {
                _state.value = _state.value.copy(isLoading = false, noToken = true)
                return@launch
            }
            token = cred.token
            _state.value = _state.value.copy(repoLabel = "$owner/$repoSlug")
            refresh()
            startPolling()
        }
    }

    fun refresh() {
        if (token.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = _state.value.runs.isEmpty())
            when (val r = GitHubActionsApi.listRuns(token, owner, repoSlug)) {
                is GitHubResult.Success -> _state.value = _state.value.copy(isLoading = false, runs = r.data)
                is GitHubResult.Error -> _state.value = _state.value.copy(isLoading = false, message = r.message)
            }
            _state.value.expandedRunId?.let { loadJobs(it) }
        }
    }

    /** While this screen is open, keeps run/job status fresh without the person having to
     *  manually pull-refresh — the whole point is "don't make me tab back to GitHub to see
     *  if it's done yet." Runs the whole time the screen is open, not just while something
     *  is active, so a run that starts *after* you opened this screen still shows up. */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(12_000)
                refresh()
            }
        }
    }

    fun toggleExpand(runId: Long) {
        val current = _state.value.expandedRunId
        if (current == runId) {
            _state.value = _state.value.copy(expandedRunId = null, logForJobId = null, logTail = null)
        } else {
            _state.value = _state.value.copy(expandedRunId = runId, logForJobId = null, logTail = null)
            loadJobs(runId)
            loadArtifacts(runId)
        }
    }

    private fun loadArtifacts(runId: Long) {
        viewModelScope.launch {
            when (val r = GitHubActionsApi.listArtifacts(token, owner, repoSlug, runId)) {
                is GitHubResult.Success -> _state.value = _state.value.copy(artifactsByRun = _state.value.artifactsByRun + (runId to r.data))
                is GitHubResult.Error -> { /* not fatal to show the run — jobs/steps still work without artifacts */ }
            }
        }
    }

    /** Downloads [artifact]'s zip, pulls the first `.apk` out of it, and signals the
     *  Composable (via [WorkflowRunsUiState.installUri]) to hand it to the system
     *  installer. GitHub always wraps artifact content in a zip even for a single
     *  file, so "download the artifact" and "extract an APK from it" are always two
     *  separate steps here, not one. */
    fun downloadAndInstall(artifact: WorkflowArtifact) {
        if (artifact.expired) {
            _state.value = _state.value.copy(message = "This artifact has expired on GitHub's side and can no longer be downloaded.")
            return
        }
        val context = getApplication<Application>()
        if (!PublicStorage.hasStorageAccess(context)) {
            _state.value = _state.value.copy(message = "GitFlow Mobile needs storage access to save downloads to .GitFlow/release — grant it from the home screen's storage-access prompt first.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(downloadingArtifactId = artifact.id)
            val releaseDir = PublicStorage.releaseDir()
            val zipFile = File(releaseDir, "artifact-${artifact.id}.zip")
            val dlResult = GitHubActionsApi.downloadArtifactZip(token, owner, repoSlug, artifact.id, zipFile)
            if (dlResult is GitHubResult.Error) {
                _state.value = _state.value.copy(downloadingArtifactId = null, message = dlResult.message)
                return@launch
            }
            val apkFile = withContext(Dispatchers.IO) { extractFirstApk(zipFile, releaseDir, artifact.name) }
            zipFile.delete() // the zip itself was just a wrapper — no reason to keep it around
            if (apkFile == null) {
                _state.value = _state.value.copy(downloadingArtifactId = null, message = "No .apk file found inside \"${artifact.name}\" — is this actually a build artifact?")
                return@launch
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            _state.value = _state.value.copy(downloadingArtifactId = null, installUri = uri, message = "Saved to .GitFlow/release/${apkFile.name}")
        }
    }

    /** Walks [zipFile]'s entries for the first one ending in `.apk` and copies just
     *  that entry out to its own file — everything else in the zip (if anything) is
     *  ignored, since for this app's own workflows the artifact is always exactly
     *  one APK. Returns null if no `.apk` entry exists. */
    private fun extractFirstApk(zipFile: File, destDir: File, artifactName: String): File? {
        ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                    val outFile = File(destDir, "$artifactName.apk")
                    outFile.outputStream().use { out -> zip.copyTo(out) }
                    return outFile
                }
                entry = zip.nextEntry
            }
        }
        return null
    }

    fun consumeInstallUri() { _state.value = _state.value.copy(installUri = null) }

    private fun loadJobs(runId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingJobs = true)
            when (val r = GitHubActionsApi.listJobs(token, owner, repoSlug, runId)) {
                is GitHubResult.Success -> _state.value = _state.value.copy(
                    isLoadingJobs = false, jobsByRun = _state.value.jobsByRun + (runId to r.data),
                )
                is GitHubResult.Error -> _state.value = _state.value.copy(isLoadingJobs = false, message = r.message)
            }
        }
    }

    fun viewLog(jobId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingLog = true, logForJobId = jobId, logTail = null)
            when (val r = GitHubActionsApi.getJobLogTail(token, owner, repoSlug, jobId)) {
                is GitHubResult.Success -> _state.value = _state.value.copy(isLoadingLog = false, logTail = r.data)
                is GitHubResult.Error -> _state.value = _state.value.copy(isLoadingLog = false, logTail = null, message = r.message)
            }
        }
    }

    fun rerunFailed(runId: Long) {
        viewModelScope.launch {
            when (val r = GitHubActionsApi.rerunFailedJobs(token, owner, repoSlug, runId)) {
                is GitHubResult.Error -> _state.value = _state.value.copy(message = r.message)
                is GitHubResult.Success -> { _state.value = _state.value.copy(message = "Rerunning failed jobs…"); refresh() }
            }
        }
    }

    fun rerunAll(runId: Long) {
        viewModelScope.launch {
            when (val r = GitHubActionsApi.rerunAll(token, owner, repoSlug, runId)) {
                is GitHubResult.Error -> _state.value = _state.value.copy(message = r.message)
                is GitHubResult.Success -> { _state.value = _state.value.copy(message = "Rerunning…"); refresh() }
            }
        }
    }

    fun cancelRun(runId: Long) {
        viewModelScope.launch {
            when (val r = GitHubActionsApi.cancelRun(token, owner, repoSlug, runId)) {
                is GitHubResult.Error -> _state.value = _state.value.copy(message = r.message)
                is GitHubResult.Success -> { _state.value = _state.value.copy(message = "Cancelling…"); refresh() }
            }
        }
    }

    fun dismiss() { _state.value = _state.value.copy(message = null) }

    override fun onCleared() { super.onCleared(); pollJob?.cancel() }
}
