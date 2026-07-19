package willykez.gitflowmobile.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import willykez.gitflowmobile.App
import willykez.gitflowmobile.git.GitEngine
import willykez.gitflowmobile.git.GitResult

/**
 * Periodic background job: fetches (never pulls/merges) every tracked repo, so ahead/behind
 * counts are current next time the app is opened. Deliberately fetch-only — auto-merging in
 * the background risks a silent conflict landing in the working tree while the user isn't
 * looking, with no chance to review it first. A manual Pull is still needed to actually bring
 * changes in; this just means "you'll already know there's something to pull."
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as App
        val repos = app.repoRepository.allRepos.first() // one-shot read; this worker is headless

        var anyFailure = false
        val reposWithNewCommits = mutableListOf<String>()

        for (repo in repos) {
            val credential = if (repo.credentialId != 0L) app.credentialRepository.getById(repo.credentialId) else null

            when (val opened = GitEngine.openRepo(repo.fullSavePath)) {
                is GitResult.Error -> {
                    anyFailure = true
                    app.repoRepository.markError(repo.id, opened.message)
                }
                is GitResult.Success -> {
                    val git = opened.data
                    when (val result = GitEngine.fetch(git, credential = credential)) {
                        is GitResult.Success -> {
                            app.repoRepository.markSyncSuccess(repo.id)
                            val aheadBehind = GitEngine.getAheadBehind(git)
                            if (aheadBehind is GitResult.Success && aheadBehind.data.second > 0) {
                                reposWithNewCommits.add(repo.name)
                            }
                        }
                        is GitResult.Error -> {
                            anyFailure = true
                            app.repoRepository.markError(repo.id, result.message)
                        }
                    }
                    git.close()
                }
            }
        }

        if (reposWithNewCommits.isNotEmpty()) {
            SyncNotifications.notifyNewCommits(applicationContext, reposWithNewCommits)
        }

        // Retry later on failure (e.g. a transient network issue) rather than giving up for
        // the rest of the scheduled period; repos that did fetch successfully still count.
        return if (anyFailure) Result.retry() else Result.success()
    }

    companion object {
        const val WORK_NAME = "gitflowmobile_background_sync"
    }
}
