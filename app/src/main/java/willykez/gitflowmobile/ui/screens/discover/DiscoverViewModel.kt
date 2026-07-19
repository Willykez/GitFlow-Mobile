package willykez.gitflowmobile.ui.screens.discover

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import willykez.gitflowmobile.App
import willykez.gitflowmobile.data.PublicStorage
import willykez.gitflowmobile.data.db.entity.RepoEntity
import willykez.gitflowmobile.data.github.GitHubApi
import willykez.gitflowmobile.data.github.GitHubRepoSummary
import willykez.gitflowmobile.data.github.GitHubResult
import willykez.gitflowmobile.data.repository.DecryptedCredential
import willykez.gitflowmobile.git.GitEngine
import willykez.gitflowmobile.git.GitResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class DiscoverUiState(
    val query: String = "",
    val results: List<GitHubRepoSummary> = emptyList(),
    val hasSearched: Boolean = false,
    val isSearching: Boolean = false,
    val showingMine: Boolean = false,
    val credentials: List<DecryptedCredential> = emptyList(),
    val selectedCredentialId: Long? = null,
    val cloningFullName: String? = null,
    val message: String? = null,
    val showCreateSheet: Boolean = false,
    val isCreating: Boolean = false,
    val deletingFullName: String? = null,
    val confirmDeleteRepo: GitHubRepoSummary? = null,
)

/**
 * Backs the Discover screen: search GitHub (or list a signed-in account's own repos) and
 * clone straight from a result, without ever needing to open github.com to copy a URL.
 */
class DiscoverViewModel(app: Application) : AndroidViewModel(app) {
    private val appRef = app as App
    private val repoRepo = appRef.repoRepository
    private val credRepo = appRef.credentialRepository

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            credRepo.allCredentials.collect { list ->
                _state.value = _state.value.copy(
                    credentials = list,
                    selectedCredentialId = _state.value.selectedCredentialId ?: list.firstOrNull()?.id,
                )
            }
        }
    }

    fun onQueryChanged(q: String) { _state.value = _state.value.copy(query = q) }
    fun onCredentialSelected(id: Long?) { _state.value = _state.value.copy(selectedCredentialId = id) }

    fun search() {
        val q = _state.value.query
        if (q.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSearching = true, showingMine = false, hasSearched = true)
            when (val r = GitHubApi.searchRepos(q, selectedToken())) {
                is GitHubResult.Success -> _state.value = _state.value.copy(results = r.data, isSearching = false)
                is GitHubResult.Error -> _state.value = _state.value.copy(isSearching = false, message = r.message)
            }
        }
    }

    fun loadMyRepos() {
        val token = selectedToken()
        if (token.isNullOrBlank()) {
            _state.value = _state.value.copy(message = "Pick a saved credential with a GitHub token first")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isSearching = true, showingMine = true, hasSearched = true)
            when (val r = GitHubApi.listMyRepos(token)) {
                is GitHubResult.Success -> _state.value = _state.value.copy(results = r.data, isSearching = false)
                is GitHubResult.Error -> _state.value = _state.value.copy(isSearching = false, message = r.message)
            }
        }
    }

    private fun selectedToken(): String? =
        _state.value.credentials.firstOrNull { it.id == _state.value.selectedCredentialId }?.token

    fun cloneRepo(repo: GitHubRepoSummary) {
        val context = getApplication<App>()
        if (!PublicStorage.hasStorageAccess(context)) {
            _state.value = _state.value.copy(
                message = "GitFlow Mobile needs storage access to save repos in a public folder. " +
                    "Grant it from the banner on the repo list, then try again.",
            )
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(cloningFullName = repo.fullName)

            val name = repo.fullName.substringAfterLast("/")
            val destination = File(PublicStorage.reposRootDir(), name)
            if (destination.exists()) {
                _state.value = _state.value.copy(
                    cloningFullName = null,
                    message = "A folder named \"$name\" already exists — rename or remove it first.",
                )
                return@launch
            }

            val credentialId = _state.value.selectedCredentialId
            val credential = credentialId?.let { credRepo.getById(it) }

            when (val result = GitEngine.cloneRepo(
                url = repo.cloneUrl,
                localPath = destination.absolutePath,
                branch = repo.defaultBranch,
                credential = if (repo.private) credential else null, // no need to auth for a public clone
            )) {
                is GitResult.Success -> {
                    result.data.close()
                    repoRepo.addRepo(
                        RepoEntity(
                            name = name,
                            fullSavePath = destination.absolutePath,
                            cloneUrl = repo.cloneUrl,
                            branch = repo.defaultBranch,
                            // Keep the credential regardless of repo.private: cloning a public
                            // repo needs no auth, but *pushing* to it later always does — and
                            // picking a credential here is a signal the person intends to push,
                            // not just browse. Discarding it for public repos was the bug behind
                            // "Authentication is required but no CredentialsProvider" on push.
                            credentialId = credentialId ?: 0L,
                        ),
                    )
                    _state.value = _state.value.copy(cloningFullName = null, message = "Cloned $name")
                }
                is GitResult.Error -> {
                    destination.deleteRecursively()
                    _state.value = _state.value.copy(cloningFullName = null, message = result.message)
                }
            }
        }
    }

    fun dismissMessage() { _state.value = _state.value.copy(message = null) }

    fun openCreateSheet() { _state.value = _state.value.copy(showCreateSheet = true) }
    fun dismissCreateSheet() { _state.value = _state.value.copy(showCreateSheet = false) }

    /** Creates a new repo on GitHub and, if [cloneAfter], clones it straight
     *  onto the device — the common case for a git client is "make a repo
     *  because I want to start working in it right now," not just create it
     *  and leave it on the website. */
    fun createRepo(name: String, description: String, private: Boolean, cloneAfter: Boolean) {
        val token = selectedToken()
        if (token.isNullOrBlank()) {
            _state.value = _state.value.copy(message = "Pick a saved credential with a GitHub token first")
            return
        }
        if (name.isBlank()) {
            _state.value = _state.value.copy(message = "Repo name can't be empty")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isCreating = true)
            when (val r = GitHubApi.createRepo(token, name, description.ifBlank { null }, private)) {
                is GitHubResult.Success -> {
                    _state.value = _state.value.copy(
                        isCreating = false, showCreateSheet = false,
                        results = listOf(r.data) + _state.value.results, hasSearched = true, showingMine = true,
                        message = "Created ${r.data.fullName}",
                    )
                    if (cloneAfter) cloneRepo(r.data)
                }
                is GitHubResult.Error -> _state.value = _state.value.copy(isCreating = false, message = r.message)
            }
        }
    }

    fun requestDelete(repo: GitHubRepoSummary) { _state.value = _state.value.copy(confirmDeleteRepo = repo) }
    fun cancelDelete() { _state.value = _state.value.copy(confirmDeleteRepo = null) }

    /** Deletes a repo on GitHub itself — not the local clone, if one exists
     *  (that's a separate, deliberately-untouched thing; removing it from
     *  this app is still "remove repo" from the repo list, same as any
     *  other repo). Requires a token with the `delete_repo` scope, which
     *  most PATs don't have by default — GitHub's own 403 message for that
     *  case is passed straight through so it's clear what's actually wrong. */
    fun confirmDeleteRepo() {
        val repo = _state.value.confirmDeleteRepo ?: return
        val token = selectedToken()
        if (token.isNullOrBlank()) {
            _state.value = _state.value.copy(confirmDeleteRepo = null, message = "Pick a saved credential with a GitHub token first")
            return
        }
        val parts = repo.fullName.split("/")
        if (parts.size != 2) {
            _state.value = _state.value.copy(confirmDeleteRepo = null, message = "Unexpected repo name format")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(deletingFullName = repo.fullName, confirmDeleteRepo = null)
            when (val r = GitHubApi.deleteRepo(token, parts[0], parts[1])) {
                is GitHubResult.Success -> _state.value = _state.value.copy(
                    deletingFullName = null,
                    results = _state.value.results.filterNot { it.fullName == repo.fullName },
                    message = "Deleted ${repo.fullName} on GitHub",
                )
                is GitHubResult.Error -> _state.value = _state.value.copy(deletingFullName = null, message = r.message)
            }
        }
    }
}
