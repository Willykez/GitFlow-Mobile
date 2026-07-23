package willykez.gitflowmobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * Repos now live in a PUBLIC folder — /storage/emulated/0/.GitFlow/repos/<name> —
 * instead of this app's private Android/data/willykez.gitflowmobile/files folder.
 *
 * The leading dot makes it a hidden folder by the usual Android/Linux
 * convention — most file managers and gallery apps skip it by default in
 * their normal listing (you can still navigate into it directly, or toggle
 * "show hidden files"). It's still genuinely public storage, not the app's
 * private sandbox — a file manager, another app, Termux, or a PC over
 * USB/MTP can all still reach it, same as before; it's just not cluttering
 * the top level of shared storage for casual browsing.
 *
 * Why public storage at all: Android/data is locked down starting Android
 * 11 — even file manager apps can't easily browse into another app's
 * Android/data folder anymore. A public folder under the root of shared
 * storage is visible to any file manager, any other app, Termux, a PC over
 * USB/MTP, etc, so you can patch files with other tools and GitFlow Mobile
 * will see the changes next time you open the repo.
 *
 * The trade-off: JGit needs a real java.io.File path (not a SAF content://
 * URI), so the simplest way to get one for a public folder is the
 * MANAGE_EXTERNAL_STORAGE ("All files access") permission on Android 11+.
 * That's a manual toggle in system Settings — Android won't show a normal
 * permission popup for it — so this class also handles building the Intent
 * that takes the user straight to the right settings screen.
 *
 * NOTE on the folder rename (GitFlowMobile → .GitFlow): repos already
 * cloned under the old visible "GitFlowMobile" folder keep working exactly
 * as before — each repo's location is stored as an absolute path in the
 * database, not recomputed from this constant, so nothing breaks for
 * existing repos. What changes going forward: new clones land in the
 * hidden folder, and the local-repo auto-detect scan now only looks inside
 * the hidden folder — a repo manually dropped into the old visible folder
 * won't be auto-detected anymore. Move it under the new hidden folder (or
 * just clone fresh) if you want it picked up.
 */
object PublicStorage {
    private const val FOLDER_NAME = ".GitFlow"

    /** Root folder for all cloned repos. Creates it if it doesn't exist yet. */
    fun reposRootDir(): File {
        val root = File(Environment.getExternalStorageDirectory(), FOLDER_NAME)
        val repos = File(root, "repos")
        repos.mkdirs()
        return repos
    }

    /** Where downloaded build artifacts (debug/release APKs pulled from the Actions
     *  screen) land — a sibling of `repos/` under the same shared `.GitFlow` root,
     *  not the app's private cache. Same reasoning as repos living in public storage:
     *  a downloaded APK stays reachable (and re-installable, or shareable) through a
     *  normal file manager even outside this app, rather than disappearing into a
     *  cache folder that's invisible without this app and gets cleared under storage
     *  pressure. Creates it if it doesn't exist yet. */
    fun releaseDir(): File {
        val root = File(Environment.getExternalStorageDirectory(), FOLDER_NAME)
        val release = File(root, "release")
        release.mkdirs()
        return release
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
