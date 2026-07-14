package willykez.gitflowmobile

import android.app.Application
import willykez.gitflowmobile.data.db.AppDatabase
import willykez.gitflowmobile.data.repository.CredentialRepository
import willykez.gitflowmobile.data.repository.RepoRepository
import willykez.gitflowmobile.sync.SyncScheduler

/**
 * Application class. Holds simple hand-rolled singletons for the database
 * and repositories — no DI framework needed for an app this small.
 */
class App : Application() {

    lateinit var repoRepository: RepoRepository
        private set

    lateinit var credentialRepository: CredentialRepository
        private set

    override fun onCreate() {
        super.onCreate()

        val db = AppDatabase.getDatabase(this)
        repoRepository = RepoRepository(db.repoDao())
        credentialRepository = CredentialRepository(db.credentialDao())

        // Re-applies whatever the user last set in Settings — WorkManager schedules don't
        // survive a full app data wipe/reinstall, but they do survive normal process death,
        // so this is mainly a safety net plus the thing that (re)schedules after a toggle.
        SyncScheduler.applyFromPrefs(this)
    }
}
