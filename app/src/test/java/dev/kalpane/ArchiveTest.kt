package dev.kalpane

import dev.kalpane.data.*
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files
import java.io.ByteArrayOutputStream
import java.util.zip.*

class ArchiveTest {
    @Test fun archiveRestoresMixedLayersImagesAndTypographyOffline() {
        val a=DiskStore(Files.createTempDirectory("export").toFile());val b=DiskStore(Files.createTempDirectory("import").toFile())
        val image=a.importAsset("image bytes".toByteArray())
        val page=Page(layers=listOf(Layer(kind=LayerKind.Text,text="V = I × R",textFace=TextFace.Monospace),Layer(kind=LayerKind.Image,asset=image),Layer(kind=LayerKind.Ink,points=listOf(InkPoint(20f,30f,0)))))
        val doc=Document(notebooks=listOf(Notebook(pages=listOf(page))))
        val out=ByteArrayOutputStream();PortableArchive.export(doc,a,out)
        val restored=PortableArchive.import(b,out.toByteArray().inputStream())
        assertEquals(doc,restored);assertArrayEquals(a.asset(image).readBytes(),b.asset(image).readBytes())
        val copies=PortableArchive.asNewNotebooks(restored)
        assertNotEquals(doc.notebooks[0].id,copies[0].id);assertNotEquals(page.id,copies[0].pages[0].id)
    }
    @Test fun rejectsTraversalWithoutWritingOutsideAssets() {
        val store=DiskStore(Files.createTempDirectory("archive").toFile())
        val out=ByteArrayOutputStream();ZipOutputStream(out).use {it.putNextEntry(ZipEntry("../../credentials"));it.write("malicious".toByteArray());it.closeEntry()}
        assertThrows(IllegalArgumentException::class.java) {PortableArchive.import(store,out.toByteArray().inputStream())}
    }
    @Test fun rejectsTamperedImageBeforeApplyingDocument() {
        val store=DiskStore(Files.createTempDirectory("archive").toFile())
        val out=ByteArrayOutputStream();ZipOutputStream(out).use {it.putNextEntry(ZipEntry("assets/"+"a".repeat(64)));it.write("wrong hash".toByteArray());it.closeEntry()}
        assertThrows(IllegalArgumentException::class.java) {PortableArchive.import(store,out.toByteArray().inputStream())}
    }
}
