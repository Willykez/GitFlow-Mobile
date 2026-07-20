package willykez.gitflowmobile.ui.screens.workflow

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import willykez.gitflowmobile.App
import willykez.gitflowmobile.data.github.GitHubActionsApi
import willykez.gitflowmobile.data.github.GitHubResult
import willykez.gitflowmobile.data.github.WorkflowJob
import willykez.gitflowmobile.data.github.WorkflowRun
import willykez.gitflowmobile.data.github.parseGitHubOwnerRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
)

class WorkflowRunsViewModel(app: Application) : AndroidViewModel(app) {
    private val repoRepo = (app as App).repoRepository
    private val credRepo = app.credentialRepository

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
        }
    }

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
