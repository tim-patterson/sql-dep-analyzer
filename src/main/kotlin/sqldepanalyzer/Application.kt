package sqldepanalyzer

import org.antlr.v4.kotlinruntime.ANTLRInputStream
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.ConsoleErrorListener
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
            sqlFiles.clear()
            event.dataTransfer?.items?.asList()?.let {
                val promises = it.filter { it.kind == "file" }.map {
                    val entry = it.webkitGetAsEntry()
                    processEntry(entry)
                }
                Promise.all(promises.toTypedArray()).then {
                    println("Drop event done")
                    println("Parsing files")
                    sqlFiles.forEach(::parseSqlFile)
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

    private fun parseSqlFile(file: SqlFile) {
        println("Parsing ${file.fullPath}")
        val input = ANTLRInputStream(file.contents)
        val lexer = SqlLexer(input)
        val tokens = CommonTokenStream(lexer)
        val parser = SqlParser(tokens)
        parser.addErrorListener(ConsoleErrorListener())
        parser.file().findStmt().map(::parseStmt)
    }

    private fun parseStmt(node: SqlParser.StmtContext) {
        node.findCreate_table_stmt()?.let(::parseCreateTableStmt)
    }

    private fun parseCreateTableStmt(node: SqlParser.Create_table_stmtContext) {
        node.findTable_identifier()?.let(::parseTableIdentifier)
    }

    private fun parseTableIdentifier(node: SqlParser.Table_identifierContext) {
        val i = node.findQualified_identifier()!!
        if (i.findIdentifier().size == 2) {
            println("Found fully qualified table ${i.findIdentifier(0)?.text}.${i.findIdentifier(1)?.text}")
        } else {
            println("Found table ${i.findIdentifier(0)?.text}")
        }
    }
}

data class SqlFile(
        val fullPath: String,
        val name: String,
        val contents: String
)