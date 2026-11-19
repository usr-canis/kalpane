package dev.kalpane.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64

class SyncPlannerTest {
    @Test fun threeWayDecisions() {
        val base = mapOf("a" to "1", "b" to "1", "c" to "1", "d" to "1", "e" to "1")
        val local = mapOf("a" to "1", "b" to "2", "c" to "2", "e" to "1", "n" to "9")
        val remote = mapOf("a" to "2", "b" to "1", "c" to "3", "d" to "1")
        val plan = SyncPlanner.plan(local, remote, base)
        assertEquals(listOf("a"), plan.download)
        assertEquals(listOf("b", "n"), plan.upload)
        assertEquals(listOf("c"), plan.conflicts)
        assertEquals(listOf("d"), plan.deleteRemote)
        assertEquals(listOf("e"), plan.deleteLocal)
    }

    @Test fun editBeatsDeletion() {
        val plan = SyncPlanner.plan(mapOf("x" to "2"), emptyMap(), mapOf("x" to "1"))
        assertEquals(listOf("x"), plan.upload)
        val plan2 = SyncPlanner.plan(emptyMap(), mapOf("x" to "2"), mapOf("x" to "1"))
        assertEquals(listOf("x"), plan2.download)
    }

    @Test fun blobShaMatchesGit() {
        // `printf 'hello\n' | git hash-object --stdin`
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", SyncPlanner.blobSha("hello\n".toByteArray()))
    }

    @Test fun notePathsAndConflictNames() {
        assertTrue(SyncPlanner.isNotePath("Work/Page 1.md"))
        assertFalse(SyncPlanner.isNotePath("README.md"))
        assertFalse(SyncPlanner.isNotePath(".github/x.yml"))
        assertFalse(SyncPlanner.isNotePath("Work/.Page 1.md.writing"))
        assertEquals("W/P (conflict).canvas.json", SyncPlanner.conflictPath("W/P.canvas.json") { false })
        assertEquals("W/P (conflict 2).md", SyncPlanner.conflictPath("W/P.md") { it == "W/P (conflict).md" })
    }
}

/** Runs a full sync against a fake GitHub Git Data API. */
class GitHubSyncTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private val blobs = mutableMapOf<String, ByteArray>()
    private var tree = mapOf<String, String>()
    private var newTreeEntries = listOf<String>()
    private var refUpdates = 0

    @After fun stop() = server.shutdown()

    private fun put(path: String, text: String) {
        val bytes = text.toByteArray()
        val sha = SyncPlanner.blobSha(bytes)
        blobs[sha] = bytes
        tree = tree + (path to sha)
    }

    private fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath.removePrefix("/repos/o/r/")
                fun ok(body: String) = MockResponse().setBody(body)
                return when {
                    path == "git/ref/heads/main" -> ok("""{"object":{"sha":"c1"}}""")
                    path == "git/commits/c1" -> ok("""{"tree":{"sha":"t1"}}""")
                    path == "git/trees/t1" -> ok(
                        """{"truncated":false,"tree":[""" + tree.entries.joinToString(",") { (p, s) -> """{"path":"$p","type":"blob","sha":"$s"}""" } +
                            """,{"path":"README.md","type":"blob","sha":"r"}]}"""
                    )
                    path.startsWith("git/blobs/") && request.method == "GET" -> {
                        val sha = path.removePrefix("git/blobs/")
                        ok("""{"content":"${Base64.getMimeEncoder().encodeToString(blobs.getValue(sha)).replace("\r\n", "\\n")}","encoding":"base64"}""")
                    }
                    path == "git/blobs" -> {
                        val bytes = Base64.getDecoder().decode(json.parseToJsonElement(request.body.readUtf8()).jsonObject["content"]!!.jsonPrimitive.content)
                        val sha = SyncPlanner.blobSha(bytes); blobs[sha] = bytes
                        ok("""{"sha":"$sha"}""")
                    }
                    path == "git/trees" -> {
                        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        assertEquals("t1", body["base_tree"]!!.jsonPrimitive.content)
                        newTreeEntries = body["tree"]!!.jsonArray.map { it.jsonObject["path"]!!.jsonPrimitive.content }
                        ok("""{"sha":"t2"}""")
                    }
                    path == "git/commits" -> ok("""{"sha":"c2"}""")
                    path == "git/refs/heads/main" -> {
                        assertTrue(request.body.readUtf8().contains("\"force\":false"))
                        refUpdates++
                        ok("""{"object":{"sha":"c2"}}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @Test fun pullsRemoteAndPushesLocal() {
        put("Work/Page 1.md", "# Remote")
        start()
        val root = tmp.newFolder("notes")
        root.resolve("Home").mkdirs()
        root.resolve("Home/Page 1.md").writeText("# Local")
        val result = GitHubSync("o/r", "main", "t", server.url("/").toString()).sync(root, emptyMap())
        assertEquals("# Remote", root.resolve("Work/Page 1.md").readText())
        assertEquals(listOf("Home/Page 1.md"), newTreeEntries)
        assertEquals(1, refUpdates)
        assertEquals("c2", result.head)
        assertEquals(setOf("Home/Page 1.md", "Work/Page 1.md"), result.base.keys)
    }

    @Test fun conflictKeepsBothVersions() {
        put("Work/Page 1.md", "theirs")
        start()
        val root = tmp.newFolder("notes")
        root.resolve("Work").mkdirs()
        root.resolve("Work/Page 1.md").writeText("ours")
        val base = mapOf("Work/Page 1.md" to SyncPlanner.blobSha("old".toByteArray()))
        val result = GitHubSync("o/r", "main", "t", server.url("/").toString()).sync(root, base)
        assertEquals("ours", root.resolve("Work/Page 1.md").readText())
        assertEquals("theirs", root.resolve("Work/Page 1 (conflict).md").readText())
        assertEquals(1, result.conflicts)
        assertTrue("Work/Page 1 (conflict).md" in newTreeEntries)
    }

    @Test fun upToDateDoesNotPush() {
        put("Work/Page 1.md", "same")
        start()
        val root = tmp.newFolder("notes")
        root.resolve("Work").mkdirs()
        root.resolve("Work/Page 1.md").writeText("same")
        GitHubSync("o/r", "main", "t", server.url("/").toString()).sync(root, emptyMap())
        assertEquals(0, refUpdates)
    }
}
