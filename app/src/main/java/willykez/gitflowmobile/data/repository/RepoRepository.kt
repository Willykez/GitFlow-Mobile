package willykez.gitflowmobile.data.repository

import willykez.gitflowmobile.data.db.dao.RepoDao
import willykez.gitflowmobile.data.db.entity.RepoEntity
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for repo metadata. ViewModels talk to this,
 * never to the DAO directly.
 */
class RepoRepository(private val dao: RepoDao) {

    val allRepos: Flow<List<RepoEntity>> = dao.getAllFlow()

    suspend fun getById(id: Long): RepoEntity? = dao.getById(id)

    suspend fun getByPath(path: String): RepoEntity? = dao.getByPath(path)

    /** Returns the new repo's row ID. */
    suspend fun addRepo(repo: RepoEntity): Long = dao.insert(repo)

    suspend fun updateRepo(repo: RepoEntity) = dao.update(repo)

    suspend fun markSyncSuccess(id: Long) = dao.markSyncSuccess(id)

    suspend fun markError(id: Long, error: String) = dao.markError(id, error)

    suspend fun updateBranch(id: Long, branch: String) = dao.updateBranch(id, branch)

    suspend fun deleteRepo(repo: RepoEntity) = dao.delete(repo)
}
