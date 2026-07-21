package willykez.gitflowmobile.data.github

import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** One workflow run — roughly one push/PR/manual trigger. */
data class WorkflowRun(
    val id: Long,
    val name: String,
    val displayTitle: String,
    val headBranch: String,
    val headSha: String,
    val status: String,       // "queued" | "in_progress" | "completed" | "waiting" ...
    val conclusion: String?,  // "success" | "failure" | "cancelled" | "skipped" | "timed_out" | null while not completed
    val event: String,        // "push" | "pull_request" | "workflow_dispatch" ...
    val runNumber: Int,
    val createdAt: String,
    val htmlUrl: String,
)

data class WorkflowStep(
    val name: String,
    val status: String,
    val conclusion: String?,
    val number: Int,
)

data class WorkflowJob(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val steps: List<WorkflowStep>,
)

/** A build output attached to a run — for this app's own workflows, that's the
 *  debug/release APK uploaded by `ci.yml`/`release.yml`'s "Upload ... artifact" step. */
data class WorkflowArtifact(
    val id: Long,
    val name: String,
    val sizeBytes: Long,
    val expired: Boolean,
)

/**
 * Whether a run/job/step is currently active — used to decide whether the UI
 * should keep polling and whether "Cancel" makes sense to show.
 */
fun isActiveStatus(status: String): Boolean = status != "completed"

/** Parses "owner/repo" out of a GitHub clone URL (https or ssh form). Returns
 *  null for anything that isn't recognizably a github.com URL — a repo cloned
 *  from GitLab/Gitea/self-hosted has no GitHub Actions runs to show anyway. */
fun parseGitHubOwnerRepo(cloneUrl: String): Pair<String, String>? {
    val httpsMatch = Regex("""github\.com[:/]+([^/]+)/([^/]+?)(?:\.git)?/?$""").find(cloneUrl)
    val (owner, repo) = httpsMatch?.destructured ?: return null
    return owner to repo
}

/**
 * GitHub Actions REST client — run/job status, log tails, rerun, cancel.
 * Same lightweight approach as `GitHubApi` (HttpURLConnection + org.json, no
 * new dependency), and the same PAT already used for git push/pull works
 * here too, since Actions read/write is covered by the standard `repo`
 * (or fine-grained "Actions" read/write) scope people already grant.
 */
object GitHubActionsApi {
    private const val BASE = "https://api.github.com"

    suspend fun listRuns(token: String, owner: String, repo: String, branch: String? = null): GitHubResult<List<WorkflowRun>> {
        val branchParam = if (!branch.isNullOrBlank()) "&branch=${java.net.URLEncoder.encode(branch, "UTF-8")}" else ""
        return when (val r = httpRequest("$BASE/repos/$owner/$repo/actions/runs?per_page=15$branchParam", "GET", token, null)) {
            is GitHubResult.Error -> r
            is GitHubResult.Success -> try {
                val arr = JSONObject(r.data).getJSONArray("workflow_runs")
                GitHubResult.Success((0 until arr.length()).map { i -> parseRun(arr.getJSONObject(i)) })
            } catch (e: Exception) {
                GitHubResult.Error("Couldn't read GitHub's response: ${e.message}")
            }
        }
    }

    suspend fun getRun(token: String, owner: String, repo: String, runId: Long): GitHubResult<WorkflowRun> {
        return when (val r = httpRequest("$BASE/repos/$owner/$repo/actions/runs/$runId", "GET", token, null)) {
            is GitHubResult.Error -> r
            is GitHubResult.Success -> try {
                GitHubResult.Success(parseRun(JSONObject(r.data)))
            } catch (e: Exception) {
                GitHubResult.Error("Couldn't read GitHub's response: ${e.message}")
            }
        }
    }

    suspend fun listJobs(token: String, owner: String, repo: String, runId: Long): GitHubResult<List<WorkflowJob>> {
        return when (val r = httpRequest("$BASE/repos/$owner/$repo/actions/runs/$runId/jobs", "GET", token, null)) {
            is GitHubResult.Error -> r
            is GitHubResult.Success -> try {
                val arr = JSONObject(r.data).getJSONArray("jobs")
                GitHubResult.Success((0 until arr.length()).map { i -> parseJob(arr.getJSONObject(i)) })
            } catch (e: Exception) {
                GitHubResult.Error("Couldn't read GitHub's response: ${e.message}")
            }
        }
    }

    /** Returns the *last [tailLines] lines* of a job's full log — the whole log for a CI
     *  job can be thousands of lines, and what you actually want after a failure is
     *  "what broke," which is almost always near the end. */
    suspend fun getJobLogTail(token: String, owner: String, repo: String, jobId: Long, tailLines: Int = 120): GitHubResult<String> =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                // This endpoint 302-redirects to a signed, time-limited plain-text log
                // URL (not GitHub's own API host) — HttpURLConnection follows redirects
                // by default, so a plain authenticated GET here just works, and no
                // GitHub-specific Accept header is needed for the redirected request.
                conn = (URL("$BASE/repos/$owner/$repo/actions/jobs/$jobId/logs").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    return@withContext GitHubResult.Error("Couldn't fetch log (HTTP $code) — it may not be available yet.")
                }
                val lines = conn.inputStream.bufferedReader().use { it.readLines() }
                GitHubResult.Success(lines.takeLast(tailLines).joinToString("\n"))
            } catch (e: Exception) {
                GitHubResult.Error(e.message ?: "Network error fetching log")
            } finally {
                conn?.disconnect()
            }
        }

    suspend fun listArtifacts(token: String, owner: String, repo: String, runId: Long): GitHubResult<List<WorkflowArtifact>> {
        return when (val r = httpRequest("$BASE/repos/$owner/$repo/actions/runs/$runId/artifacts", "GET", token, null)) {
            is GitHubResult.Error -> r
            is GitHubResult.Success -> try {
                val arr = JSONObject(r.data).getJSONArray("artifacts")
                GitHubResult.Success((0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    WorkflowArtifact(
                        id = o.getLong("id"),
                        name = o.optString("name", "artifact"),
                        sizeBytes = o.optLong("size_in_bytes", 0L),
                        expired = o.optBoolean("expired", false),
                    )
                })
            } catch (e: Exception) {
                GitHubResult.Error("Couldn't read GitHub's response: ${e.message}")
            }
        }
    }

    /** Downloads an artifact's archive to [destZip]. GitHub always wraps artifact
     *  content in a zip, even for a single file — the caller (see
     *  `WorkflowRunsViewModel.downloadAndInstall`) extracts the APK out of it. */
    suspend fun downloadArtifactZip(token: String, owner: String, repo: String, artifactId: Long, destZip: File): GitHubResult<Unit> =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL("$BASE/repos/$owner/$repo/actions/artifacts/$artifactId/zip").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 30_000 // artifacts can be several MB; a short timeout would fail large debug APKs
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    return@withContext GitHubResult.Error("Couldn't download artifact (HTTP $code) — it may have expired.")
                }
                conn.inputStream.use { input -> destZip.outputStream().use { output -> input.copyTo(output) } }
                GitHubResult.Success(Unit)
            } catch (e: Exception) {
                GitHubResult.Error(e.message ?: "Network error downloading artifact")
            } finally {
                conn?.disconnect()
            }
        }

    suspend fun rerunFailedJobs(token: String, owner: String, repo: String, runId: Long): GitHubResult<Unit> =
        postAction(token, "$BASE/repos/$owner/$repo/actions/runs/$runId/rerun-failed-jobs")

    suspend fun rerunAll(token: String, owner: String, repo: String, runId: Long): GitHubResult<Unit> =
        postAction(token, "$BASE/repos/$owner/$repo/actions/runs/$runId/rerun")

    suspend fun cancelRun(token: String, owner: String, repo: String, runId: Long): GitHubResult<Unit> =
        postAction(token, "$BASE/repos/$owner/$repo/actions/runs/$runId/cancel")

    private suspend fun postAction(token: String, url: String): GitHubResult<Unit> =
        when (val r = httpRequest(url, "POST", token, "")) {
            is GitHubResult.Error -> r
            is GitHubResult.Success -> GitHubResult.Success(Unit)
        }

    private fun parseRun(o: JSONObject): WorkflowRun = WorkflowRun(
        id = o.getLong("id"),
        name = o.optString("name", "Workflow"),
        displayTitle = o.optString("display_title", ""),
        headBranch = o.optString("head_branch", ""),
        headSha = o.optString("head_sha", ""),
        status = o.optString("status", "queued"),
        conclusion = if (o.isNull("conclusion")) null else o.optString("conclusion").ifBlank { null },
        event = o.optString("event", ""),
        runNumber = o.optInt("run_number", 0),
        createdAt = o.optString("created_at", ""),
        htmlUrl = o.optString("html_url", ""),
    )

    private fun parseJob(o: JSONObject): WorkflowJob {
        val stepsArr = o.optJSONArray("steps") ?: JSONArray()
        val steps = (0 until stepsArr.length()).map { i ->
            val s = stepsArr.getJSONObject(i)
            WorkflowStep(
                name = s.optString("name", "Step"),
                status = s.optString("status", "queued"),
                conclusion = if (s.isNull("conclusion")) null else s.optString("conclusion").ifBlank { null },
                number = s.optInt("number", i + 1),
            )
        }
        return WorkflowJob(
            id = o.getLong("id"),
            name = o.optString("name", "Job"),
            status = o.optString("status", "queued"),
            conclusion = if (o.isNull("conclusion")) null else o.optString("conclusion").ifBlank { null },
            steps = steps,
        )
    }

    /** Same shared-HTTP-plumbing shape as `GitHubApi.httpRequest` — kept as
     *  its own private copy rather than exposing GitHubApi's (which is
     *  private to that object) since this file has slightly different
     *  needs (POST with no meaningful body for the action endpoints). */
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
