package sqldepanalyzer

import org.antlr.v4.kotlinruntime.ANTLRInputStream
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.ConsoleErrorListener
import org.antlr.v4.kotlinruntime.tree.ParseTreeWalker
import org.w3c.dom.DragEvent
import org.w3c.dom.Element
import org.w3c.dom.events.Event
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import kotlin.browser.window
import kotlin.js.Promise


class Application(private val dropArea: Element) {
    private val sqlFiles: MutableList<SqlFile> = mutableListOf()
    private val meterBar: HTMLElement = dropArea.getElementsByClassName("progress-meter")[0] as HTMLElement  //document.getElementById("progress-bar")!! as HTMLElement

    init {
        initializeDropEvents()
    }

    private fun initializeDropEvents() {
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

            startUploadAndParse()
            event.dataTransfer?.items?.asList()?.let {
                val promises = it.filter { it.kind == "file" }.map {
                    val entry = it.webkitGetAsEntry()
                    processEntry(entry)
                }
                Promise.all(promises.toTypedArray()).then { startParse() }
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

    private fun startUploadAndParse() {
        meterBar.style.width = "0%"

        sqlFiles.clear()
    }

    private fun startParse() {
        val fileCount = sqlFiles.size
        var currFile = 0
        val iter = sqlFiles.iterator()
        fun parseFiles() {
            if (iter.hasNext()) {
                currFile++
                val file = iter.next()
                parseSqlFile(file)
                meterBar.style.width = "${100 * currFile / fileCount}%"
                window.setTimeout(::parseFiles)
            } else {
                endParse()
            }
        }
        parseFiles()
    }


    private fun endParse() {

    }

    private fun parseSqlFile(file: SqlFile) {
        println("Parsing ${file.fullPath}")
        val input = ANTLRInputStream(file.contents)
        val lexer = SqlLexer(input)
        val tokens = CommonTokenStream(lexer)
        val parser = SqlParser(tokens)
        parser.addErrorListener(ConsoleErrorListener())

        ParseTreeWalker.DEFAULT.walk(listener, parser.file())
    }

    val listener = object: SqlBaseListener() {
        override fun enterTable_identifier(ctx: SqlParser.Table_identifierContext) {
            val identifier = ctx.findQualified_identifier()!!
            val tableName = identifier.findIdentifier().joinToString(".") { it.text }
            println("Found table $tableName")
        }
    }


}

data class SqlFile(
        val fullPath: String,
        val name: String,
        val contents: String
)