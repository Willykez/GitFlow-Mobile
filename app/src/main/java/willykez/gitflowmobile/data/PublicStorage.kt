package willykez.gitflowmobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * Repos now live in a PUBLIC folder — /storage/emulated/0/GitFlowMobile/repos/<name> —
 * instead of this app's private Android/data/willykez.gitflowmobile/files folder.
 *
 * Why this matters: Android/data is locked down starting Android 11 — even
 * file manager apps can't easily browse into another app's Android/data
 * folder anymore. A public folder under the root of shared storage is
 * visible to any file manager, any other app, Termux, a PC over USB/MTP,
 * etc, so you can patch files with other tools and GitFlow Mobile will see the
 * changes next time you open the repo.
 *
 * The trade-off: JGit needs a real java.io.File path (not a SAF content://
 * URI), so the simplest way to get one for a public folder is the
 * MANAGE_EXTERNAL_STORAGE ("All files access") permission on Android 11+.
 * That's a manual toggle in system Settings — Android won't show a normal
 * permission popup for it — so this class also handles building the Intent
 * that takes the user straight to the right settings screen.
 */
object PublicStorage {
    private const val FOLDER_NAME = "GitFlowMobile"

    /** Root folder for all cloned repos. Creates it if it doesn't exist yet. */
    fun reposRootDir(): File {
        val root = File(Environment.getExternalStorageDirectory(), FOLDER_NAME)
        val repos = File(root, "repos")
        repos.mkdirs()
        return repos
    }

    /**
     * Whether the app currently has the access it needs to read/write the
     * public folder above. On Android 10 and below this is always true here
     * (classic WRITE_EXTERNAL_STORAGE, requested separately, covers it).
     */
    fun hasStorageAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /** Only meaningful on Android 11+ — builds the Intent for the All Files Access settings screen. */
    fun allFilesAccessIntent(context: Context): Intent {
        return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
}
