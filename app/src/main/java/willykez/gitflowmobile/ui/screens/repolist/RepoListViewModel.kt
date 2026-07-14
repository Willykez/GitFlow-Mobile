package willykez.gitflowmobile.ui.screens.repolist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import willykez.gitflowmobile.App
import willykez.gitflowmobile.data.db.entity.RepoEntity
import willykez.gitflowmobile.git.GitEngine
import willykez.gitflowmobile.git.GitResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

enum class RepoSortMode { RECENT, NAME, HAS_CHANGES }

data class RepoListUiState(
    val repos: List<RepoEntity> = emptyList(),
    val busyRepoId: Long? = null,
    val snackbarMessage: String? = null,
    val searchQuery: String = "",
    val sortMode: RepoSortMode = RepoSortMode.RECENT,
    /** Uncommitted (staged + unstaged) file count per repo id, for the list badge. Absent
     * key = not computed yet (e.g. still opening the repo), not necessarily zero changes. */
    val changeCounts: Map<Long, Int> = emptyMap(),
) {
    /** Repos filtered by [searchQuery] (name or clone URL, case-insensitive) and sorted by
     * [sortMode]. Computed here rather than stored separately so there's exactly one source
     * of truth for the raw repo list. */
    val visibleRepos: List<RepoEntity> get() {
        val filtered = if (searchQuery.isBlank()) {
            repos
        } else {
            repos.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.cloneUrl.contains(searchQuery, ignoreCase = true)
            }
        }
        return when (sortMode) {
            RepoSortMode.RECENT -> filtered.sortedByDescending { it.addedAt }
            RepoSortMode.NAME -> filtered.sortedBy { it.name.lowercase() }
            RepoSortMode.HAS_CHANGES -> filtered.sortedByDescending { changeCounts[it.id] ?: -1 }
        }
    }
}

class RepoListViewModel(app: Application) : AndroidViewModel(app) {
    private val appRef = app as App
    private val repoRepository = appRef.repoRepository
    private val credentialRepository = appRef.credentialRepository
    private val busyRepoId = MutableStateFlow<Long?>(null)
    private val snackbarMessage = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val sortMode = MutableStateFlow(RepoSortMode.RECENT)
    private val changeCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())

    private val filters = combine(searchQuery, sortMode, changeCounts) { q, s, c -> Triple(q, s, c) }

    val uiState: StateFlow<RepoListUiState> = combine(
        repoRepository.allRepos, busyRepoId, snackbarMessage, filters,
    ) { repos, busy, msg, (query, sort, counts) ->
        RepoListUiState(repos, busy, msg, query, sort, counts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RepoListUiState())

    init {
        // Compute each repo's uncommitted-change count the first time it's seen. Cheap
        // enough for a handful of repos; if this list grows into the hundreds this should
        // move to something lazier (e.g. only visible items), but that's not this app's
        // scale today.
        viewModelScope.launch {
            repoRepository.allRepos.collect { repos ->
                repos.forEach { repo ->
                    if (!changeCounts.value.containsKey(repo.id)) loadChangeCount(repo)
                }
            }
        }
    }

    fun setSearchQuery(q: String) { searchQuery.value = q }
    fun setSortMode(m: RepoSortMode) { sortMode.value = m }

    private fun loadChangeCount(repo: RepoEntity) {
        viewModelScope.launch {
            when (val opened = GitEngine.openRepo(repo.fullSavePath)) {
                is GitResult.Error -> Unit // leave uncomputed rather than showing a false "0"
                is GitResult.Success -> {
                    val git = opened.data
                    val statusResult = GitEngine.getStatus(git)
                    if (statusResult is GitResult.Success) {
                        changeCounts.value = changeCounts.value + (repo.id to statusResult.data.size)
                    }
                    git.close()
                }
            }
        }
    }

    fun dismissSnackbar() { snackbarMessage.value = null }

    fun deleteRepo(repo: RepoEntity, alsoDeleteFiles: Boolean) {
        viewModelScope.launch {
            repoRepository.deleteRepo(repo)
            if (alsoDeleteFiles) File(repo.fullSavePath).deleteRecursively()
        }
    }

    fun fetch(repo: RepoEntity) = remoteOp(repo) { git, cred ->
        GitEngine.fetch(git, credential = cred).also { r ->
            if (r is GitResult.Success) snackbarMessage.value = "${repo.name}: ${r.data}"
        }
    }

    fun pull(repo: RepoEntity) = remoteOp(repo) { git, cred ->
        GitEngine.pullMerge(git, cred).also { r ->
            if (r is GitResult.Success) snackbarMessage.value = "${repo.name}: ${r.data}"
        }
    }

    fun pullForce(repo: RepoEntity) = remoteOp(repo) { git, cred ->
        GitEngine.pullForce(git, cred).also { r ->
            if (r is GitResult.Success) snackbarMessage.value = "${repo.name}: ${r.data}"
        }
    }

    fun push(repo: RepoEntity) = remoteOp(repo) { git, cred ->
        GitEngine.push(git, credential = cred).also { r ->
            if (r is GitResult.Success) snackbarMessage.value = "Pushed ${repo.name}"
        }
    }

    fun pushForce(repo: RepoEntity) = remoteOp(repo) { git, cred ->
        GitEngine.push(git, force = true, credential = cred).also { r ->
            if (r is GitResult.Success) snackbarMessage.value = "Force pushed ${repo.name}"
        }
    }

    private fun remoteOp(repo: RepoEntity, block: suspend (org.eclipse.jgit.api.Git, willykez.gitflowmobile.data.repository.DecryptedCredential?) -> GitResult<*>) {
        viewModelScope.launch {
            busyRepoId.value = repo.id
            val cred = if (repo.credentialId != 0L) credentialRepository.getById(repo.credentialId) else null
            when (val open = GitEngine.openRepo(repo.fullSavePath)) {
                is GitResult.Error -> fail(repo, open.message)
                is GitResult.Success -> {
                    val git = open.data
                    when (val r = block(git, cred)) {
                        is GitResult.Error -> fail(repo, r.message)
                        is GitResult.Success -> {
                            repoRepository.markSyncSuccess(repo.id)
                            loadChangeCount(repo)
                        }
                    }
                    git.close()
                }
            }
            busyRepoId.value = null
        }
    }

    private suspend fun fail(repo: RepoEntity, message: String) {
        repoRepository.markError(repo.id, message)
        snackbarMessage.value = "${repo.name}: $message"
    }
}
