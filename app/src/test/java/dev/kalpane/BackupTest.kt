package dev.kalpane

import dev.kalpane.data.*
import dev.kalpane.sync.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.nio.file.Files

class BackupTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitHubBackup
    @Before fun setup() {server=MockWebServer();server.start();client=GitHubBackup("owner/private","test-token",DiskStore(Files.createTempDirectory("backup").toFile()),server.url("/").toString())}
    @After fun teardown() {server.shutdown()}
    private fun response(body: String,code: Int=200) {server.enqueue(MockResponse().setResponseCode(code).setBody(body))}
    @Test fun rejectsChangedRemoteBeforeUploading() {
        response("""{"private":true,"permissions":{"push":true}}""")
        response("""{"object":{"sha":"new-remote"}}""")
        assertThrows(RemoteChanged::class.java) {client.push("main",Workspace(remoteHead="old"))}
        assertEquals(2,server.requestCount)
    }
    @Test fun pushUsesExpectedParentBaseTreeAndNonForcedRefUpdate() {
        response("""{"private":true,"permissions":{"push":true}}""")
        response("""{"object":{"sha":"old"}}""");response("""{"tree":{"sha":"original-tree"}}""")
        response("""{"sha":"blob"}""");response("""{"sha":"new-tree"}""");response("""{"sha":"new-commit"}""")
        response("""{"object":{"sha":"old"}}""");response("""{"ref":"refs/heads/main"}""")
        assertEquals("new-commit",client.push("main",Workspace(remoteHead="old")))
        server.takeRequest() // private-repository check
        val requests=(1..7).map {server.takeRequest()}
        assertTrue(requests[3].body.readUtf8().contains("\"base_tree\":\"original-tree\""))
        assertTrue(requests[4].body.readUtf8().contains("\"parents\":[\"old\"]"))
        assertEquals("PATCH",requests[6].method);assertTrue(requests[6].body.readUtf8().contains("\"force\":false"))
    }
    @Test fun lateRemoteRaceDoesNotAdvanceRef() {
        response("""{"private":true,"permissions":{"push":true}}""")
        response("""{"object":{"sha":"old"}}""");response("""{"tree":{"sha":"tree"}}""")
        response("""{"sha":"blob"}""");response("""{"sha":"tree2"}""");response("""{"sha":"commit"}""")
        response("""{"object":{"sha":"changed-during-upload"}}""")
        assertThrows(RemoteChanged::class.java) {client.push("main",Workspace(remoteHead="old"))}
        assertEquals(7,server.requestCount)
    }
    @Test fun serverRejectsRaceAfterPreflightWithoutMarkingSuccess() {
        response("""{"private":true,"permissions":{"push":true}}""")
        response("""{"object":{"sha":"old"}}""");response("""{"tree":{"sha":"tree"}}""")
        response("""{"sha":"blob"}""");response("""{"sha":"tree2"}""");response("""{"sha":"commit"}""")
        response("""{"object":{"sha":"old"}}""");response("{}",422)
        assertThrows(RemoteChanged::class.java) {client.push("main",Workspace(remoteHead="old"))}
    }
    @Test fun verificationRejectsPublicRepositories() {
        response("""{"private":false}""")
        assertThrows(IllegalArgumentException::class.java) {client.verify()}
    }
    @Test fun pushRefusesRepositoryMadePublicAfterConnection() {
        response("""{"private":false}""")
        assertThrows(IllegalArgumentException::class.java) {client.push("main",Workspace(remoteHead="old"))}
        assertEquals(1,server.requestCount)
    }
    @Test fun branchNamesAreValidated() {
        assertTrue(GitHubBackup.validBranch("feature/ideas"))
        listOf("../main","a..b","a.lock","a b","a\\b","a~b","/bad","bad/","a//b",".hidden","a@{b").forEach {assertFalse(it,GitHubBackup.validBranch(it))}
    }
}
