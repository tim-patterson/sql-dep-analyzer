package sqldepanalyzer

import org.w3c.dom.DragEvent
import org.w3c.dom.Element
import org.w3c.dom.events.Event
import kotlin.js.Promise


class Application(dropArea: Element) {
    private val sqlFiles: MutableList<SqlFile> = mutableListOf()

    init {
        initializeDropEvents(dropArea)
    }



    private fun initializeDropEvents(dropArea: Element) {
        // Stop the browser just opening the file
        listOf("dragenter", "dragleave", "dragover", "drop").forEach {
            dropArea.addEventListener(it, { event ->
                event.preventDefault()
                event.stopPropagation()
            })
        }
        dropArea.addEventListener("drop", ::handleDrop)
    }

    private fun handleDrop(event: Event) {
        if (event is DragEvent) {
            event.dataTransfer?.items?.asList()?.let {
                val promises = it.filter { it.kind == "file" }.map {
                    val entry = it.webkitGetAsEntry()
                    processEntry(entry)
                }
                Promise.all(promises.toTypedArray()).then {
                    println("Drop event done")
                    sqlFiles.forEach {
                        println("File: ${it.fullPath}\n${it.contents}\n\n\n")
                    }
                }
            }
        }
    }

    private fun processEntry(entry: FileSystemEntry): Promise<*> {
        return if (entry.isFile) {
            if (entry.name.endsWith(".sql")) {
                entry.read().then {
                    sqlFiles.add(SqlFile(entry.fullPath, entry.name, it))
                }
            } else {
                Promise.resolve(Unit)
            }
        } else {
            entry.listEntries().then { Promise.all(it.map(::processEntry).toTypedArray()) }
        }
    }
}

data class SqlFile(
        val fullPath: String,
        val name: String,
        val contents: String
)