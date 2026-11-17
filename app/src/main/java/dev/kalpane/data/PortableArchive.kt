package dev.kalpane.data

import kotlinx.serialization.encodeToString
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A credential-free, content-addressed portable backup. Never extracts arbitrary ZIP paths. */
object PortableArchive {
    private const val MAX_TOTAL=200L*1024*1024
    fun export(document: Document,store: DiskStore,output: OutputStream) {
        document.validate()
        val json=notebookJson.encodeToString(document).toByteArray()
        require(json.size<=40*1024*1024) {"Notebook metadata exceeds the 40 MB portable-backup limit."}
        require(json.size+document.assets().sumOf {store.asset(it).length()}<=MAX_TOTAL) {"Portable backup exceeds 200 MB."}
        ZipOutputStream(output).use {zip->
            fun add(name: String,bytes: ByteArray) {zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry()}
            add("workspace.json",json)
            document.assets().sorted().forEach {name->val bytes=store.asset(name).readBytes();check(hash(bytes)==name);add("assets/$name",bytes)}
        }
    }
    fun import(store: DiskStore,input: InputStream): Document {
        var document: Document?=null;var total=0L
        val seen=mutableSetOf<String>();val assets=mutableSetOf<String>()
        ZipInputStream(input).use {zip->
            while(true) {
                val entry=zip.nextEntry ?: break
                require(seen.add(entry.name) && seen.size<=20001) {"Duplicate entries or too many files in backup."}
                require(!entry.isDirectory && (entry.name=="workspace.json" || entry.name.matches(Regex("assets/[a-f0-9]{64}")))) {"Unexpected path in backup."}
                val limit=if(entry.name=="workspace.json") 40*1024*1024 else 20*1024*1024
                val bytes=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(8192)
                while(true) {
                    val count=zip.read(buffer);if(count<0) break
                    total+=count
                    require(bytes.size()+count<=limit && total<=MAX_TOTAL) {"Backup is too large to import safely."}
                    bytes.write(buffer,0,count)
                }
                if(entry.name=="workspace.json") document=notebookJson.decodeFromString<Document>(bytes.toString(Charsets.UTF_8.name())).validate()
                else {
                    val expected=entry.name.removePrefix("assets/");val contents=bytes.toByteArray()
                    require(hash(contents)==expected) {"An image checksum in this backup is invalid."}
                    store.importAsset(contents);assets+=expected
                }
                zip.closeEntry()
            }
        }
        val result=document ?: error("This ZIP has no Kalpane notebook document.")
        require(assets.containsAll(result.assets())) {"The backup is missing one or more image files."}
        return result
    }
    fun asNewNotebooks(document: Document): List<Notebook> = document.notebooks.map {n->
        n.copy(id=id(),title="${n.title} · imported",pages=n.pages.map {p->p.copy(id=id(),layers=p.layers.map {it.copy(id=id())})})
    }
}
