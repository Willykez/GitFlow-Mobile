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
                            credentialId = if (repo.private) (credentialId ?: 0L) else 0L,
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
}
