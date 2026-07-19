package willykez.gitflowmobile.data.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One row in the Discover list — enough to display and clone, nothing more. */
data class GitHubRepoSummary(
    val fullName: String,
    val description: String?,
    val cloneUrl: String,
    val stars: Int,
    val defaultBranch: String,
    val private: Boolean,
)

sealed class GitHubResult<out T> {
    data class Success<T>(val data: T) : GitHubResult<T>()
    data class Error(val message: String) : GitHubResult<Nothing>()
}

/**
 * Minimal GitHub REST client — just enough to search public repos and list a signed-in
 * user's own repos, so cloning can be driven from inside the app instead of copying a URL
 * off github.com in a browser. Deliberately built on HttpURLConnection + org.json (both
 * already part of the Android platform) rather than adding Retrofit/OkHttp/Gson for what's
 * really just two GET endpoints.
 *
 * Search works with no token (GitHub's public search API is unauthenticated, just rate
 * limited — 10 requests/minute per IP). "My repos" needs a token, since it's inherently
 * about a specific account: pass any saved credential's PAT and it works the same way it
 * already does for git push/pull (needs at least the "repo" scope to list private repos).
 */
object GitHubApi {
    private const val BASE = "https://api.github.com"

    suspend fun searchRepos(query: String, token: String? = null): GitHubResult<List<GitHubRepoSummary>> {
        if (query.isBlank()) return GitHubResult.Success(emptyList())
        val url = "$BASE/search/repositories?q=${URLEncoder.encode(query, "UTF-8")}&per_page=30"
        return when (val r = httpGet(url, token)) {
            is GitHubResult.Error -> r
            is GitHubResult.Success -> try {
                val json = JSONObject(r.data)
                GitHubResult.Success(parseRepoArray(json.getJSONArray("items")))
            } catch (e: Exception) {
                GitHubResult.Error("Couldn't read GitHub's response: ${e.message}")
            }
        }
    }

    suspend fun listMyRepos(token: String): GitHubResult<List<GitHubRepoSummary>> {
        val url = "$BASE/user/repos?per_page=50&sort=updated"
        return when (val r = httpGet(url, token)) {
            is GitHubResult.Error -> r
            is GitHubResult.Success -> try {
                GitHubResult.Success(parseRepoArray(JSONArray(r.data)))
            } catch (e: Exception) {
                GitHubResult.Error("Couldn't read GitHub's response: ${e.message}")
            }
        }
    }

    /** Creates a new repo under the signed-in account. [name] is required by
     *  GitHub; everything else is optional. Returns the created repo's info
     *  (including its real clone URL) on success. */
    suspend fun createRepo(
        token: String, name: String, description: String? = null,
        private: Boolean = false, autoInit: Boolean = true,
    ): GitHubResult<GitHubRepoSummary> {
        val body = JSONObject().apply {
            put("name", name)
            if (!description.isNullOrBlank()) put("description", description)
            put("private", private)
            put("auto_init", autoInit) // without this, a brand-new repo has no default branch to clone
        }
        return when (val r = httpRequest("$BASE/user/repos", "POST", token, body.toString())) {
            is GitHubResult.Error -> r
            is GitHubResult.Success -> try {
                GitHubResult.Success(parseRepoObject(JSONObject(r.data)))
            } catch (e: Exception) {
                GitHubResult.Error("Couldn't read GitHub's response: ${e.message}")
            }
        }
    }

    /** Renames/redescribes/re-visibilities a repo. Pass only the fields that
     *  should change — GitHub's PATCH endpoint leaves anything omitted as-is. */
    suspend fun updateRepo(
        token: String, owner: String, repo: String,
        newName: String? = null, description: String? = null, private: Boolean? = null,
    ): GitHubResult<GitHubRepoSummary> {
        val body = JSONObject().apply {
            if (!newName.isNullOrBlank()) put("name", newName)
            if (description != null) put("description", description)
            if (private != null) put("private", private)
        }
        return when (val r = httpRequest("$BASE/repos/$owner/$repo", "PATCH", token, body.toString())) {
            is GitHubResult.Error -> r
            is GitHubResult.Success -> try {
                GitHubResult.Success(parseRepoObject(JSONObject(r.data)))
            } catch (e: Exception) {
                GitHubResult.Error("Couldn't read GitHub's response: ${e.message}")
            }
        }
    }

    /** Permanently deletes a repo on GitHub. Requires a token with the
     *  `delete_repo` scope specifically — most personal access tokens don't
     *  have it by default even if they can push/pull fine, since GitHub
     *  treats deletion as a separate, deliberately-opt-in permission. A
     *  token missing that scope gets a 403 here, surfaced as-is so the
     *  error message points at the real cause rather than looking like a
     *  generic failure. */
    suspend fun deleteRepo(token: String, owner: String, repo: String): GitHubResult<Unit> {
        return when (val r = httpRequest("$BASE/repos/$owner/$repo", "DELETE", token, null)) {
            is GitHubResult.Error -> r
            is GitHubResult.Success -> GitHubResult.Success(Unit)
        }
    }

    private fun parseRepoObject(o: JSONObject): GitHubRepoSummary = GitHubRepoSummary(
        fullName = o.getString("full_name"),
        description = if (o.isNull("description")) null else o.optString("description").ifBlank { null },
        cloneUrl = o.getString("clone_url"),
        stars = o.optInt("stargazers_count", 0),
        defaultBranch = o.optString("default_branch", "main"),
        private = o.optBoolean("private", false),
    )

    private fun parseRepoArray(arr: JSONArray): List<GitHubRepoSummary> {
        return (0 until arr.length()).map { i -> parseRepoObject(arr.getJSONObject(i)) }
    }

    private suspend fun httpGet(urlStr: String, token: String?): GitHubResult<String> =
        httpRequest(urlStr, "GET", token, null)

    /** Shared HTTP plumbing for every verb this client needs. [jsonBody], when
     *  present, is sent as an `application/json` request body (POST/PATCH);
     *  GET/DELETE pass null. A 204 No Content (what DELETE returns on
     *  success) is treated as success with an empty body rather than a
     *  parse failure. */
    private suspend fun httpRequest(urlStr: String, method: String, token: String?, jsonBody: String?): GitHubResult<String> =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
                    if (jsonBody != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
                    }
                }

                val code = conn.responseCode
                if (code == 204) return@withContext GitHubResult.Success("")

                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() } ?: ""

                if (code !in 200..299) {
                    val msg = try {
                        JSONObject(body).optString("message", "GitHub returned HTTP $code")
                    } catch (e: Exception) {
                        "GitHub returned HTTP $code"
                    }
                    GitHubResult.Error(msg)
                } else {
                    GitHubResult.Success(body)
                }
            } catch (e: Exception) {
                GitHubResult.Error(e.message ?: "Network error reaching GitHub")
            } finally {
                conn?.disconnect()
            }
        }
}
