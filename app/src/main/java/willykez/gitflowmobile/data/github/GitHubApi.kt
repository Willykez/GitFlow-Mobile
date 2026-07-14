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

    private fun parseRepoArray(arr: JSONArray): List<GitHubRepoSummary> {
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GitHubRepoSummary(
                fullName = o.getString("full_name"),
                description = if (o.isNull("description")) null else o.optString("description").ifBlank { null },
                cloneUrl = o.getString("clone_url"),
                stars = o.optInt("stargazers_count", 0),
                defaultBranch = o.optString("default_branch", "main"),
                private = o.optBoolean("private", false),
            )
        }
    }

    private suspend fun httpGet(urlStr: String, token: String?): GitHubResult<String> =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
                }

                val code = conn.responseCode
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
