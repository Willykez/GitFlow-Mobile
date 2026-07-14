package willykez.gitflowmobile.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import willykez.gitflowmobile.data.db.dao.CredentialDao
import willykez.gitflowmobile.data.db.dao.RepoDao
import willykez.gitflowmobile.data.db.entity.CredentialEntity
import willykez.gitflowmobile.data.db.entity.RepoEntity

@Database(
    entities = [RepoEntity::class, CredentialEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun repoDao(): RepoDao
    abstract fun credentialDao(): CredentialDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gitflowmobile.db"
                )
                    // Fine while the schema is still settling during early development.
                    // Once you ship a version people rely on, replace this with real
                    // Room migrations so upgrading the app doesn't wipe repo history.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
