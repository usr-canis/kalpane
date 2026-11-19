package dev.kalpane.sync

import dev.kalpane.data.writeAtomic
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

class SyncException(message: String) : Exception(message)
private class RefMoved : Exception()

data class RepoInfo(val tokenExpiry: String?)

data class SyncResult(val head: String, val base: Map<String, String>, val pulled: Int, val pushed: Int, val conflicts: Int)

/**
 * Syncs the notes folder with a branch of a private GitHub repository through the Git Data API.
 * Every push is a normal commit on top of the remote head; the branch is never force-updated.
 */
class GitHubSync(
    private val repo: String,
    private val branch: String,
    private val token: String,
    private val api: String = "https://api.github.com/",
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false) // never forward the token elsewhere
        .build()
    private var expiryHeader: String? = null

    init {
        if (!repo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) throw SyncException("Use the form owner/repository.")
    }

    private fun call(method: String, path: String, body: JsonElement? = null, allow: Set<Int> = emptySet()): Pair<Int, JsonElement?> {
        val url = api.toHttpUrl().newBuilder().addPathSegments("repos/$repo").apply {
            path.split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) }
        }
        val request = Request.Builder()
            .url(url.build())
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .method(method, body?.toString()?.toRequestBody("application/json".toMediaType()))
            .build()
        val response = try { client.newCall(request).execute() } catch (e: IOException) {
            throw SyncException("No connection to GitHub. Your notes are safe on this device.")
        }
        response.use { r ->
            r.header("github-authentication-token-expiration")?.let { expiryHeader = it }
            val text = r.body?.string().orEmpty()
            if (r.code in allow) return r.code to text.takeIf { it.isNotBlank() }?.let(json::parseToJsonElement)
            if (!r.isSuccessful) throw SyncException(
                when (r.code) {
                    401 -> "GitHub rejected the token. It may have expired; create a new one in Settings."
                    403 -> if (r.header("x-ratelimit-remaining") == "0") "GitHub rate limit reached. Try again later."
                    else "The token cannot access this repository. It needs Contents: read and write."
                    404 -> "Repository or branch not found. Check the name and that the token can access it."
                    409 -> "The repository is empty. Create it with a README first."
                    else -> "GitHub request failed (${r.code}). Your notes are safe on this device."
                }
            )
            return r.code to text.takeIf { it.isNotBlank() }?.let(json::parseToJsonElement)
        }
    }

    private fun get(path: String) = call("GET", path).second!!.jsonObject
    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    /** Checks the repository is private, writable, and has the branch. */
    fun verify(): RepoInfo {
        val meta = get("")
        if (meta["private"]?.jsonPrimitive?.boolean != true) throw SyncException("Choose a private repository for your notes.")
        if (meta["permissions"]?.jsonObject?.get("push")?.jsonPrimitive?.boolean == false) {
            throw SyncException("The token needs Contents: read and write.")
        }
        head()
        return RepoInfo(expiryHeader)
    }

    val tokenExpiry get() = expiryHeader

    private fun head(): String {
        val (code, body) = call("GET", "git/ref/heads/$branch", allow = setOf(404, 409))
        if (code == 409) throw SyncException("The repository is empty. Create it with a README first.")
        if (code == 404) throw SyncException("Branch “$branch” not found in $repo.")
        return body!!.jsonObject.getValue("object").jsonObject.str("sha")
    }

    private fun remoteTree(commit: String): Pair<String, Map<String, String>> {
        val tree = get("git/commits/$commit").getValue("tree").jsonObject.str("sha")
        val listing = fetchAbsolute(
            api.toHttpUrl().newBuilder().addPathSegments("repos/$repo/git/trees").addPathSegment(tree)
                .addQueryParameter("recursive", "1").build().toString()
        )
        if (listing["truncated"]?.jsonPrimitive?.boolean == true) throw SyncException("The repository is too large to sync.")
        val files = listing.getValue("tree").jsonArray.map { it.jsonObject }
            .filter { it.str("type") == "blob" && SyncPlanner.isNotePath(it.str("path")) }
            .associate { it.str("path") to it.str("sha") }
        return tree to files
    }

    private fun fetchAbsolute(url: String): JsonObject {
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28").build()
        val response = try { client.newCall(request).execute() } catch (e: IOException) {
            throw SyncException("No connection to GitHub. Your notes are safe on this device.")
        }
        response.use { r ->
            if (!r.isSuccessful) throw SyncException("GitHub request failed (${r.code}). Your notes are safe on this device.")
            return json.parseToJsonElement(r.body!!.string()).jsonObject
        }
    }

    private fun blob(sha: String): ByteArray {
        val b = get("git/blobs/$sha")
        return Base64.getMimeDecoder().decode(b.str("content"))
    }

    private fun createBlob(bytes: ByteArray): String = call("POST", "git/blobs", buildJsonObject {
        put("content", Base64.getEncoder().encodeToString(bytes))
        put("encoding", "base64")
    }).second!!.jsonObject.str("sha")

    /** Pulls, merges and pushes. [base] is the file state after the last successful sync. */
    fun sync(root: File, base: Map<String, String>): SyncResult {
        repeat(3) {
            try { return attempt(root, base) } catch (_: RefMoved) { /* Someone pushed meanwhile: merge again. */ }
        }
        throw SyncException("The repository kept changing during sync. Try again.")
    }

    private fun attempt(root: File, base: Map<String, String>): SyncResult {
        val head = head()
        val (tree, remote) = remoteTree(head)
        val local = SyncPlanner.scan(root)
        val plan = SyncPlanner.plan(local, remote, base)
        fun file(path: String) = File(root, path)

        // Bring remote changes down first; the local folder is the working copy.
        plan.download.forEach { writeAtomic(file(it), blob(remote.getValue(it))) }
        plan.deleteLocal.forEach { file(it).delete() }
        val upload = plan.upload.toMutableSet()
        for (path in plan.conflicts) {
            val theirs = blob(remote.getValue(path))
            when {
                path.endsWith("/notebook.json") -> {
                    writeAtomic(file(path), mergeOrder(file(path).readBytes(), theirs))
                    upload += path
                }
                path.contains("/assets/") -> upload += path
                else -> {
                    val copy = SyncPlanner.conflictPath(path) { file(it).exists() || it in remote }
                    writeAtomic(file(copy), theirs)
                    upload += path; upload += copy
                }
            }
        }
        pruneEmptyDirs(root)

        val pulled = plan.download.size + plan.deleteLocal.size
        if (upload.isEmpty() && plan.deleteRemote.isEmpty()) {
            return SyncResult(head, SyncPlanner.scan(root), pulled, 0, plan.conflicts.size)
        }
        val entries = buildJsonArray {
            for (path in upload.sorted()) {
                val bytes = file(path).readBytes()
                val sha = SyncPlanner.blobSha(bytes).takeIf { it in remote.values } ?: createBlob(bytes)
                add(buildJsonObject { put("path", path); put("mode", "100644"); put("type", "blob"); put("sha", sha) })
            }
            for (path in plan.deleteRemote) {
                add(buildJsonObject { put("path", path); put("mode", "100644"); put("type", "blob"); put("sha", JsonNull) })
            }
        }
        val newTree = call("POST", "git/trees", buildJsonObject { put("base_tree", tree); put("tree", entries) }).second!!.jsonObject.str("sha")
        val changes = upload.size + plan.deleteRemote.size
        val commit = call("POST", "git/commits", buildJsonObject {
            put("message", "Update notes from Kalpane ($changes ${if (changes == 1) "file" else "files"})")
            put("tree", newTree)
            put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(head)) })
        }).second!!.jsonObject.str("sha")
        val (code, _) = call("PATCH", "git/refs/heads/$branch", buildJsonObject {
            put("sha", commit); put("force", false)
        }, allow = setOf(409, 422))
        if (code == 409 || code == 422) throw RefMoved()
        return SyncResult(commit, SyncPlanner.scan(root), pulled, changes, plan.conflicts.size)
    }

    /** Page order from both sides: ours first, then pages only they have. */
    private fun mergeOrder(ours: ByteArray, theirs: ByteArray): ByteArray {
        fun pages(bytes: ByteArray) = runCatching {
            json.parseToJsonElement(bytes.decodeToString()).jsonObject["pages"]?.jsonArray?.map { it.jsonPrimitive.content }
        }.getOrNull().orEmpty()
        val a = pages(ours); val b = pages(theirs)
        val merged = a + b.filter { it !in a }
        return buildJsonObject { put("pages", JsonArray(merged.map { kotlinx.serialization.json.JsonPrimitive(it) })) }
            .toString().toByteArray()
    }

    private fun pruneEmptyDirs(root: File) {
        root.walkBottomUp().filter { it.isDirectory && it != root }.forEach { d ->
            if (d.listFiles().isNullOrEmpty()) d.delete()
        }
    }
}
