package dev.kalpane

import dev.kalpane.data.*
import dev.kalpane.ink.InputPolicy
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class DocumentTest {
    private fun doc(page: Page) = Document(notebooks=listOf(Notebook(id="notebook",title="Lab",pages=listOf(page))))
    @Test fun concurrentPageEditsPreserveBothVersionsAndAssets() {
        val p=Page(id="p",title="Circuit");val base=doc(p)
        val local=doc(p.copy(layers=listOf(Layer(kind=LayerKind.Text,text="Local calculation"))))
        val remote=doc(p.copy(layers=listOf(Layer(kind=LayerKind.Image,asset="a".repeat(64)))))
        val merged=Merge.documents(base,local,remote)
        assertEquals(1,merged.conflicts);assertEquals(2,merged.document.pages().size)
        assertEquals(local.pages()[0],merged.document.pages()[0])
        assertEquals(remote.pages()[0].layers,merged.document.pages()[1].layers)
        assertNotEquals("p",merged.document.pages()[1].id)
        assertTrue(merged.document.pages()[1].title.contains("conflict"))
    }
    @Test fun independentEditsMergeWithoutConflicts() {
        val a=Page(id="a");val b=Page(id="b")
        val base=Document(notebooks=listOf(Notebook(id="n",pages=listOf(a,b))))
        val local=base.replace(a.copy(title="A local"));val remote=base.replace(b.copy(title="B remote"))
        val merged=Merge.documents(base,local,remote)
        assertEquals(0,merged.conflicts);assertEquals(listOf("A local","B remote"),merged.document.pages().map {it.title})
    }
    @Test fun deleteVersusEditKeepsRecoveryPage() {
        val p=Page(id="p");val base=doc(p)
        val deleted=Document(notebooks=listOf(base.notebooks[0].copy(pages=emptyList())))
        val merged=Merge.documents(base,deleted,doc(p.copy(title="Edited")))
        assertEquals(1,merged.conflicts);assertEquals(1,merged.document.pages().size)
        assertTrue(merged.document.pages()[0].title.contains("recovery"))
    }
    @Test fun layersShareOnePersistentOrder() {
        val ink=Layer(id="ink",kind=LayerKind.Ink);val text=Layer(id="text",kind=LayerKind.Text);val image=Layer(id="image",kind=LayerKind.Image,asset="a".repeat(64))
        val p=Page(layers=listOf(ink,text,image))
        assertEquals(listOf("image","ink","text"),p.moveLayer("image",false).layers.map {it.id})
        assertEquals(listOf("text","image","ink"),p.moveLayer("ink",true).layers.map {it.id})
    }
    @Test fun atomicStoreRecoversPreviousSnapshotWithoutDiscardingCorruption() {
        val root=Files.createTempDirectory("kalpane").toFile();val store=DiskStore(root)
        val first=Workspace(document=doc(Page(title="First")))
        store.save("owner/repo","main",first);store.save("owner/repo","main",first.copy(document=doc(Page(title="Second"))))
        root.resolve("workspaces").listFiles()!!.first {it.name.endsWith(".json")}.writeText("{interrupted")
        assertEquals(first,store.read("owner/repo","main"))
        assertTrue(root.resolve("workspaces").listFiles()!!.any {it.name.contains("corrupt-")})
    }
    @Test fun interruptedWriteLeavesCommittedSnapshotAndOfflineImage() {
        val root=Files.createTempDirectory("kalpane").toFile();val store=DiskStore(root)
        val bytes="image content".toByteArray();val asset=store.importAsset(bytes)
        val w=Workspace(document=doc(Page(layers=listOf(Layer(kind=LayerKind.Image,asset=asset)))))
        store.save("r/n","main",w)
        root.resolve("workspaces").listFiles()!!.first {it.name.endsWith(".json")}.let { java.io.File(it.path+".writing").writeText("partial") }
        val reopened=DiskStore(root)
        assertEquals(w,reopened.read("r/n","main"));assertArrayEquals(bytes,reopened.asset(asset).readBytes())
    }
    @Test fun branchWorkspacesRetainUnsyncedEdits() {
        val store=DiskStore(Files.createTempDirectory("kalpane").toFile())
        val main=Workspace(document=doc(Page(title="Unsynced main")));val experiment=Workspace(document=doc(Page(title="Other branch")))
        store.save("owner/repo","main",main);store.save("owner/repo","feature/ideas",experiment)
        assertEquals(main,store.read("owner/repo","main"));assertTrue(store.read("owner/repo","main").pending)
        assertEquals(experiment,store.read("owner/repo","feature/ideas"))
    }
    @Test fun penOnlyRejectsPalmBeforeAndDuringPen() {
        val policy=InputPolicy();policy.beginTouch(1000)
        assertFalse(policy.drawFinger(1000,false,1,.2f))
        assertFalse(policy.turnPage(1100,false,1f,500f,0f,100))
        policy.pen(1200,true)
        assertFalse(policy.touchAllowed(1300));assertFalse(policy.drawFinger(1300,true,1,.1f))
        policy.pen(1400,false)
        assertFalse(policy.turnPage(1600,true,1f,500f,0f,200))
        policy.beginTouch(2200);assertTrue(policy.touchAllowed(2200))
    }
    @Test fun cancellationPoisonsWholeTouchSequence() {
        val policy=InputPolicy();policy.beginTouch(1000);policy.cancelled()
        assertFalse(policy.turnPage(2000,true,1f,500f,0f,200))
        policy.beginTouch(3000);assertTrue(policy.turnPage(3200,true,1f,500f,0f,200))
        assertFalse(policy.turnPage(3200,true,2f,500f,0f,200))
        assertFalse(policy.drawFinger(3200,true,2,.1f));assertFalse(policy.drawFinger(3200,true,1,.8f))
    }
    @Test(expected=IllegalArgumentException::class) fun remoteImageCannotEscapeAssetDirectory() {
        doc(Page(layers=listOf(Layer(kind=LayerKind.Image,asset="../../secret")))).validate()
    }
}
