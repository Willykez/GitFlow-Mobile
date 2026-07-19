package willykez.gitflowmobile.data

import org.eclipse.jgit.lib.Config
import java.io.File

/**
 * Finds git repos that already exist on disk (dropped in via a file
 * manager, USB/MTP, Termux, etc.) but aren't tracked in the app's
 * database yet — the "local" counterpart to cloning: you don't have to
 * clone a repo through this app to use it here, you can just put one in
 * the app's repos folder and have it show up.
 */
object LocalRepoScanner {

    data class Candidate(val name: String, val path: String, val originUrl: String)

    /**
     * Looks for folders containing a `.git` directory under [root], skipping
     * anything already in [knownPaths]. Only descends [maxDepth] levels (repos
     * normally live directly under the repos root, but this tolerates one or
     * two levels of manual organizing, e.g. dropping a repo inside a personal
     * subfolder) and never recurses *into* a found repo — a repo containing
     * git submodules shouldn't surface those as separate top-level candidates.
     */
    fun scan(root: File, knownPaths: Set<String>, maxDepth: Int = 2): List<Candidate> {
        if (!root.exists() || !root.isDirectory) return emptyList()
        val results = mutableListOf<Candidate>()
        val normalizedKnown = knownPaths.map { File(it).absolutePath }.toSet()
        root.listFiles()?.forEach { child ->
            if (child.isDirectory) findGitRepos(child, depth = 0, maxDepth, normalizedKnown, results)
        }
        return results
    }

    private fun findGitRepos(dir: File, depth: Int, maxDepth: Int, knownPaths: Set<String>, out: MutableList<Candidate>) {
        val gitDir = File(dir, ".git")
        if (gitDir.exists() && gitDir.isDirectory) {
            if (dir.absolutePath !in knownPaths) {
                out.add(Candidate(dir.name, dir.absolutePath, readOriginUrl(gitDir)))
            }
            return // this folder is a repo — don't look for repos inside it
        }
        if (depth >= maxDepth) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory && !child.name.startsWith(".")) {
                findGitRepos(child, depth + 1, maxDepth, knownPaths, out)
            }
        }
    }

    /** Best-effort read of `[remote "origin"] url = ...` straight from the
     *  `.git/config` file — doesn't need a full JGit Repository/Git instance
     *  open just to answer one string, and failing to read it (missing
     *  remote, unreadable file) isn't fatal: the repo still gets added,
     *  just with an empty clone URL the person can fill in later via the
     *  Remote screen. */
    private fun readOriginUrl(gitDir: File): String {
        return try {
            val configFile = File(gitDir, "config")
            if (!configFile.exists()) return ""
            val config = Config()
            config.fromText(configFile.readText())
            config.getString("remote", "origin", "url") ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
