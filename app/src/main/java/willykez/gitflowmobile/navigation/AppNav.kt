package willykez.gitflowmobile.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import willykez.gitflowmobile.ui.screens.branches.BranchesScreen
import willykez.gitflowmobile.ui.screens.blame.BlameScreen
import willykez.gitflowmobile.ui.screens.changes.ChangesScreen
import willykez.gitflowmobile.ui.screens.conflicts.ConflictsScreen
import willykez.gitflowmobile.ui.screens.credential.CredentialScreen
import willykez.gitflowmobile.ui.screens.diff.DiffScreen
import willykez.gitflowmobile.ui.screens.discover.DiscoverScreen
import willykez.gitflowmobile.ui.screens.editor.FileEditorScreen
import willykez.gitflowmobile.ui.screens.explorer.FileExplorerScreen
import willykez.gitflowmobile.ui.screens.gitignore.GitignoreScreen
import willykez.gitflowmobile.ui.screens.log.LogScreen
import willykez.gitflowmobile.ui.screens.problems.ProblemsScreen
import willykez.gitflowmobile.ui.screens.remote.RemoteScreen
import willykez.gitflowmobile.ui.screens.repolist.RepoListScreen
import willykez.gitflowmobile.ui.screens.settings.SettingsScreen
import willykez.gitflowmobile.ui.screens.stash.StashScreen
import willykez.gitflowmobile.ui.screens.tags.TagsScreen

object Routes {
    const val REPO_LIST  = "repo_list"
    const val CHANGES    = "changes/{repoId}"
    const val CREDENTIALS = "credentials"
    const val DISCOVER   = "discover"
    const val SETTINGS   = "settings"
    const val LOG        = "log/{repoId}"
    const val BRANCHES   = "branches/{repoId}"
    const val DIFF       = "diff/{repoId}/{encodedPath}/{staged}"
    const val STASH      = "stash/{repoId}"
    const val REMOTE     = "remote/{repoId}"
    const val TAGS       = "tags/{repoId}"
    const val GITIGNORE  = "gitignore/{repoId}"
    const val EXPLORER   = "explorer/{repoId}/{encodedPath}"
    const val EDITOR     = "editor/{repoId}/{encodedPath}?line={line}"
    const val CONFLICTS  = "conflicts/{repoId}"
    const val BLAME      = "blame/{repoId}/{encodedPath}"
    const val PROBLEMS   = "problems/{repoId}"

    fun changes(id: Long)  = "changes/$id"
    fun log(id: Long)      = "log/$id"
    fun branches(id: Long) = "branches/$id"
    fun stash(id: Long)    = "stash/$id"
    fun remote(id: Long)   = "remote/$id"
    fun tags(id: Long)     = "tags/$id"
    fun gitignore(id: Long)= "gitignore/$id"
    fun diff(id: Long, path: String, staged: Boolean) =
        "diff/$id/${java.net.URLEncoder.encode(path, "UTF-8")}/$staged"
    fun explorer(id: Long, path: String = "") =
        // "." is a stand-in for "repo root" — an actual empty string here would produce
        // a URI like "explorer/1/" with nothing after the trailing slash, which Navigation
        // Compose's {encodedPath} placeholder can't match (empty path segments never match
        // a route argument), so it'd silently fail to resolve and crash. See the matching
        // decode side in GitFlowMobileNavHost's EXPLORER composable.
        "explorer/$id/${java.net.URLEncoder.encode(path.ifBlank { "." }, "UTF-8")}"
    fun editor(id: Long, path: String, line: Int = 0) =
        "editor/$id/${java.net.URLEncoder.encode(path, "UTF-8")}?line=$line"
    fun conflicts(id: Long) = "conflicts/$id"
    fun blame(id: Long, path: String) = "blame/$id/${java.net.URLEncoder.encode(path, "UTF-8")}"
    fun problems(id: Long) = "problems/$id"
}

private const val T = 240

@Composable
fun GitFlowMobileNavHost(nav: NavHostController) {
    NavHost(
        navController = nav,
        startDestination = Routes.REPO_LIST,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(T)) },
        exitTransition  = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(T)) },
        popEnterTransition  = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(T)) },
        popExitTransition   = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(T)) },
    ) {
        composable(Routes.REPO_LIST) {
            RepoListScreen(
                onOpenRepo = { nav.navigate(Routes.changes(it)) },
                onOpenCredentials = { nav.navigate(Routes.CREDENTIALS) },
                onOpenDiscover = { nav.navigate(Routes.DISCOVER) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.DISCOVER) { DiscoverScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.CHANGES, arguments = listOf(navArgument("repoId") { type = NavType.LongType })) { bs ->
            val id = bs.arguments!!.getLong("repoId")
            ChangesScreen(
                repoId = id, onBack = { nav.popBackStack() },
                onOpenLog      = { nav.navigate(Routes.log(id)) },
                onOpenBranches = { nav.navigate(Routes.branches(id)) },
                onOpenStash    = { nav.navigate(Routes.stash(id)) },
                onOpenRemote   = { nav.navigate(Routes.remote(id)) },
                onOpenTags     = { nav.navigate(Routes.tags(id)) },
                onOpenGitignore= { nav.navigate(Routes.gitignore(id)) },
                onOpenFiles    = { nav.navigate(Routes.explorer(id)) },
                onOpenProblems = { nav.navigate(Routes.problems(id)) },
                onOpenConflicts= { nav.navigate(Routes.conflicts(id)) },
                onOpenDiff     = { path, staged -> nav.navigate(Routes.diff(id, path, staged)) },
            )
        }
        composable(Routes.CREDENTIALS) { CredentialScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.LOG, arguments = listOf(navArgument("repoId") { type = NavType.LongType })) { bs ->
            LogScreen(repoId = bs.arguments!!.getLong("repoId"), onBack = { nav.popBackStack() },
                onOpenDiff = { repoId, sha -> nav.navigate("diff/$repoId/${java.net.URLEncoder.encode(sha, "UTF-8")}/commit") })
        }
        composable(Routes.BRANCHES, arguments = listOf(navArgument("repoId") { type = NavType.LongType })) { bs ->
            BranchesScreen(repoId = bs.arguments!!.getLong("repoId"), onBack = { nav.popBackStack() })
        }
        composable(Routes.DIFF, arguments = listOf(
            navArgument("repoId") { type = NavType.LongType },
            navArgument("encodedPath") { type = NavType.StringType },
            navArgument("staged") { type = NavType.StringType },
        )) { bs ->
            DiffScreen(
                repoId = bs.arguments!!.getLong("repoId"),
                encodedPath = bs.arguments!!.getString("encodedPath") ?: "",
                stagedOrCommit = bs.arguments!!.getString("staged") ?: "false",
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.STASH, arguments = listOf(navArgument("repoId") { type = NavType.LongType })) { bs ->
            StashScreen(repoId = bs.arguments!!.getLong("repoId"), onBack = { nav.popBackStack() })
        }
        composable(Routes.REMOTE, arguments = listOf(navArgument("repoId") { type = NavType.LongType })) { bs ->
            RemoteScreen(repoId = bs.arguments!!.getLong("repoId"), onBack = { nav.popBackStack() })
        }
        composable(Routes.TAGS, arguments = listOf(navArgument("repoId") { type = NavType.LongType })) { bs ->
            TagsScreen(repoId = bs.arguments!!.getLong("repoId"), onBack = { nav.popBackStack() })
        }
        composable(Routes.GITIGNORE, arguments = listOf(navArgument("repoId") { type = NavType.LongType })) { bs ->
            GitignoreScreen(repoId = bs.arguments!!.getLong("repoId"), onBack = { nav.popBackStack() })
        }
        composable(Routes.EXPLORER, arguments = listOf(
            navArgument("repoId") { type = NavType.LongType },
            navArgument("encodedPath") { type = NavType.StringType },
        )) { bs ->
            val id = bs.arguments!!.getLong("repoId")
            val decoded = java.net.URLDecoder.decode(bs.arguments!!.getString("encodedPath") ?: "", "UTF-8")
            val path = if (decoded == ".") "" else decoded
            FileExplorerScreen(
                repoId = id,
                relativePath = path,
                onBack = { nav.popBackStack() },
                onOpenFolder = { childPath -> nav.navigate(Routes.explorer(id, childPath)) },
                onOpenFile = { filePath -> nav.navigate(Routes.editor(id, filePath)) },
                onOpenBlame = { filePath -> nav.navigate(Routes.blame(id, filePath)) },
            )
        }
        composable(Routes.EDITOR, arguments = listOf(
            navArgument("repoId") { type = NavType.LongType },
            navArgument("encodedPath") { type = NavType.StringType },
            navArgument("line") { type = NavType.IntType; defaultValue = 0 },
        )) { bs ->
            val path = java.net.URLDecoder.decode(bs.arguments!!.getString("encodedPath") ?: "", "UTF-8")
            FileEditorScreen(
                repoId = bs.arguments!!.getLong("repoId"),
                relativePath = path,
                initialLine = bs.arguments!!.getInt("line"),
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.CONFLICTS, arguments = listOf(navArgument("repoId") { type = NavType.LongType })) { bs ->
            val id = bs.arguments!!.getLong("repoId")
            ConflictsScreen(
                repoId = id,
                onBack = { nav.popBackStack() },
                onEditFile = { path -> nav.navigate(Routes.editor(id, path)) },
            )
        }
        composable(Routes.BLAME, arguments = listOf(
            navArgument("repoId") { type = NavType.LongType },
            navArgument("encodedPath") { type = NavType.StringType },
        )) { bs ->
            val id = bs.arguments!!.getLong("repoId")
            val path = java.net.URLDecoder.decode(bs.arguments!!.getString("encodedPath") ?: "", "UTF-8")
            BlameScreen(
                repoId = id, path = path,
                onBack = { nav.popBackStack() },
                onOpenCommit = { sha -> nav.navigate("diff/$id/${java.net.URLEncoder.encode(sha, "UTF-8")}/commit") },
            )
        }
        composable(Routes.PROBLEMS, arguments = listOf(navArgument("repoId") { type = NavType.LongType })) { bs ->
            val id = bs.arguments!!.getLong("repoId")
            ProblemsScreen(
                repoId = id,
                onBack = { nav.popBackStack() },
                onOpenFile = { path, line -> nav.navigate(Routes.editor(id, path, line)) },
            )
        }
    }
}
