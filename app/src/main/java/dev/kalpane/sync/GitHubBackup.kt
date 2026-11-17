package dev.kalpane.sync

import dev.kalpane.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

class RemoteChanged : IllegalStateException("The remote branch changed. Pull to merge both versions, then push again.")
data class RemoteSnapshot(val head: String, val tree: String, val document: Document)

/** Git Database API commits a complete snapshot, then advances the ref with force=false. */
class GitHubBackup(
    private val repo: String, private val token: String, private val store: DiskStore,
    private val api: String = "https://api.github.com/",
) {
    private val client = OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    init { require(repo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Use owner/repository." } }
    private fun request(method: String, path: String, body: JsonElement? = null, allow404: Boolean = false): JsonObject? {
        val url = api.toHttpUrl().newBuilder().addPathSegments("repos/$repo").apply { path.split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) } }.build()
        val payload = body?.toString()?.toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).header("Authorization", "Bearer $token").header("Accept","application/vnd.github+json")
            .header("X-GitHub-Api-Version","2022-11-28").method(method,payload).build()
        client.newCall(req).execute().use { response ->
            if (response.code == 404 && allow404) return null
            if (response.code == 409 || response.code == 422) throw RemoteChanged()
            check(response.isSuccessful) { when(response.code) {
                401 -> "Credential rejected. Enter a valid repository-scoped token."
                403 -> "Access denied or GitHub rate limit reached. Check Contents permissions."
                404 -> "Repository or branch unavailable. Check its name and token access."
                else -> "GitHub request failed (${response.code}). Local changes are safe."
            } }
            val raw = response.body?.string() ?: error("Empty GitHub response")
            val parsed = notebookJson.parseToJsonElement(raw)
            return if (parsed is JsonArray) buildJsonObject { put("items", parsed) } else parsed.jsonObject
        }
    }
    private fun requirePrivateRepository() {
        val metadata = request("GET", "")!!
        require(metadata["private"]?.jsonPrimitive?.boolean == true) { "Choose a private repository for notebook backups." }
        require(metadata["permissions"]?.jsonObject?.get("push")?.jsonPrimitive?.boolean != false) { "This credential needs Contents read and write access." }
    }
    fun verify(): List<String> {requirePrivateRepository();return branches()}
    fun branches(): List<String> {
        // Git refs endpoint includes branch names containing slashes without query interpolation.
        val result = request("GET","git/matching-refs/heads")!!
        val names = result["items"]?.jsonArray.orEmpty().map { it.jsonObject.getValue("ref").jsonPrimitive.content.removePrefix("refs/heads/") }
        require(names.isNotEmpty()) { "Initialize the private repository with a README, then verify again." }
        return names
    }
    fun head(branch: String): String = request("GET", "git/ref/heads/$branch")!!.getValue("object").jsonObject.getValue("sha").jsonPrimitive.content
    private fun blob(sha: String): ByteArray {
        val b = request("GET", "git/blobs/$sha")!!
        require(b.getValue("size").jsonPrimitive.long <= 40L * 1024 * 1024) { "Backup file is too large." }
        require(b.getValue("encoding").jsonPrimitive.content == "base64")
        return Base64.getMimeDecoder().decode(b.getValue("content").jsonPrimitive.content)
    }
    fun pull(branch: String): RemoteSnapshot {
        requirePrivateRepository()
        val head = head(branch)
        val commit = request("GET", "git/commits/$head")!!
        val tree = commit.getValue("tree").jsonObject.getValue("sha").jsonPrimitive.content
        // Walk exact directories. Avoid recursive tree truncation and never trust filenames as local paths.
        fun entries(sha: String) = request("GET", "git/trees/$sha")!!.getValue("tree").jsonArray.map { it.jsonObject }
        val root = entries(tree)
        val folder = root.find { it["path"]?.jsonPrimitive?.content == "kalpane" && it["type"]?.jsonPrimitive?.content == "tree" }
            ?: return RemoteSnapshot(head,tree,Document())
        val files = entries(folder.getValue("sha").jsonPrimitive.content)
        val docEntry = files.find { it["path"]?.jsonPrimitive?.content == "workspace.json" && it["type"]?.jsonPrimitive?.content == "blob" }
            ?: return RemoteSnapshot(head,tree,Document())
        val doc = notebookJson.decodeFromString<Document>(blob(docEntry.getValue("sha").jsonPrimitive.content).toString(Charsets.UTF_8)).validate()
        val required = doc.assets().filter { !store.asset(it).exists() || hash(store.asset(it).readBytes()) != it }
        if (required.isNotEmpty()) {
            val assetTree = files.find { it["path"]?.jsonPrimitive?.content == "assets" && it["type"]?.jsonPrimitive?.content == "tree" } ?: error("Backup is missing images; pull was not applied.")
            val assets = entries(assetTree.getValue("sha").jsonPrimitive.content).associateBy { it.getValue("path").jsonPrimitive.content }
            required.forEach { name ->
                val entry = assets[name] ?: error("Backup image is missing; pull was not applied.")
                require(entry["type"]?.jsonPrimitive?.content == "blob")
                val bytes = blob(entry.getValue("sha").jsonPrimitive.content)
                require(hash(bytes) == name) { "Backup image checksum failed; pull was not applied." }
                store.importAsset(bytes)
            }
        }
        return RemoteSnapshot(head,tree,doc)
    }
    fun push(branch: String, workspace: Workspace): String {
        requirePrivateRepository()
        val expected = workspace.remoteHead ?: error("Pull once before pushing so remote changes can be checked.")
        if (head(branch) != expected) throw RemoteChanged()
        val commit = request("GET", "git/commits/$expected")!!
        val baseTree = commit.getValue("tree").jsonObject.getValue("sha").jsonPrimitive.content
        fun createBlob(bytes: ByteArray): String = request("POST", "git/blobs", buildJsonObject {
            put("content",Base64.getEncoder().encodeToString(bytes)); put("encoding","base64")
        })!!.getValue("sha").jsonPrimitive.content
        val entries = mutableListOf<JsonObject>()
        fun add(path: String, bytes: ByteArray) { entries += buildJsonObject { put("path",path); put("mode","100644"); put("type","blob"); put("sha",createBlob(bytes)) } }
        add("kalpane/workspace.json", notebookJson.encodeToString(workspace.document).toByteArray())
        workspace.document.assets().forEach { name ->
            val bytes = store.asset(name).readBytes()
            check(hash(bytes) == name) { "Local image checksum failed; push stopped." }
            add("kalpane/assets/$name",bytes)
        }
        val tree = request("POST","git/trees",buildJsonObject { put("base_tree",baseTree); put("tree",JsonArray(entries)) })!!.getValue("sha").jsonPrimitive.content
        val newCommit = request("POST","git/commits",buildJsonObject {
            put("message","Back up Kalpane notebooks"); put("tree",tree); put("parents",buildJsonArray { add(expected) })
        })!!.getValue("sha").jsonPrimitive.content
        // A preflight check is helpful, but the non-forced ref update is the server-side race guard.
        if (head(branch) != expected) throw RemoteChanged()
        request("PATCH","git/refs/heads/$branch",buildJsonObject { put("sha",newCommit); put("force",false) })
        return newCommit
    }
    fun createBranch(name: String, from: String) {
        require(validBranch(name)) { "Use a valid Git branch name." }
        request("POST","git/refs",buildJsonObject { put("ref","refs/heads/$name"); put("sha",head(from)) })
    }
    companion object {
        fun validBranch(s: String): Boolean = s.isNotBlank() && s != "@" && !s.startsWith('-') && !s.endsWith('.') &&
            !s.contains("..") && !s.contains("@{") && !s.contains(Regex("[ ~^:?*\\\\\\[\\x00-\\x1f\\x7f]")) &&
            s.split('/').all { it.isNotEmpty() && !it.startsWith('.') && !it.endsWith(".lock") }
    }
}
