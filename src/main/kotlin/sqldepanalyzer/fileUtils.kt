package sqldepanalyzer

import org.w3c.dom.DataTransferItem
import org.w3c.dom.DataTransferItemList
import org.w3c.dom.ErrorEvent
import org.w3c.dom.get
import org.w3c.files.Blob
import org.w3c.files.File
import org.w3c.files.FileReader
import kotlin.js.Promise

// Helpers to make js apis more kotlin like

fun Blob.read(): Promise<String> {
    return Promise { resolve, reject ->
        val reader = FileReader()
        reader.onload = {
            resolve(reader.result as String)
        }
        reader.onerror = { err ->
            reject(Exception((err as ErrorEvent).message))
        }
        reader.readAsText(this)
    }
}

fun FileSystemEntry.read(): Promise<String> {
    return Promise { resolve, reject ->
        this.file(onSuccess = {
            it.read().then(resolve).catch(reject)
        })
    }
}


fun DataTransferItemList.asList(): List<DataTransferItem> {
    return 0.until(this.length).map { this[it]!! }
}

fun FileSystemEntry.listEntries(): Promise<List<FileSystemEntry>> {
    return if (this.isDirectory) {
        Promise { resolve, _ ->
            val reader = this.createReader()
            val entries = mutableListOf<FileSystemEntry>()
            fun doBatch() {
                reader.readEntries {
                    entries.addAll(it)
                    if (it.isNotEmpty()) {
                        doBatch()
                    } else {
                        resolve(entries)
                    }
                }
            }
            doBatch()
        }
    } else {
        Promise.resolve(listOf())
    }
}



// External JS interfaces

fun DataTransferItem.webkitGetAsEntry(): FileSystemEntry {
    return this.asDynamic().webkitGetAsEntry().unsafeCast<FileSystemEntry>()
}

external interface FileSystemEntry {
    val isFile: Boolean
    val isDirectory: Boolean
    val name: String
    val fullPath: String
    // File apis
    fun file(onSuccess: (File) -> Unit, onError: ((dynamic) -> Unit)? = definedExternally)
    // Directory apis
    fun createReader(): FileSystemDirectoryReader
}

external interface FileSystemDirectoryReader {
    fun readEntries(onSuccess: (Array<FileSystemEntry>)-> Unit)
}