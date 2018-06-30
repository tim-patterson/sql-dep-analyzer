package sqldepanalyzer

import org.antlr.v4.kotlinruntime.ANTLRInputStream
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.ConsoleErrorListener
import org.antlr.v4.kotlinruntime.tree.ParseTreeWalker
import org.antlr.v4.kotlinruntime.tree.TerminalNode
import org.w3c.dom.*
import org.w3c.dom.events.Event
import kotlin.browser.window
import kotlin.js.Promise


class Application(private val dropArea: Element, private val contentArea: Element) {
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
        contentArea.innerHTML = ""
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
//        // For any queries that read from temp tables, we should replace the
//        // temp tables in the "tablesread" with any source tables used to generate
//        // the temp tables.
//        sqlFiles.map { file ->
//            val tempTables = file.definedTables.filter { it.temporary }.map { it.database to it.name }.toSet()
//            val tempTableMappings = mutableMapOf<Pair<String, String>, MutableList<Pair<String, String>>>()
//
//            file.queries.map { query ->
//                // update mappings for inserts into temp tables
//                query.tablesWriten.filter { it in tempTables }.forEach {
//                    val mappings = tempTableMappings.getOrPut(it) { mutableListOf() }
//                    mappings.addAll(query.tablesRead)
//                }
//
//                // Now lets do the actual updates for the queries reading these temp tables
//                query.tablesRead.flatMap { tempTableMappings[it] ?: mutableListOf() }
//            }
//        }


        val databases = sqlFiles.flatMap { file ->
            file.definedTables
        }.filterNot { it.temporary }.groupBy { table -> table.database }.mapValues { (database, tables) ->
            tables.sortedBy { it.name }
        }

        val files = sqlFiles.sortedBy { it.fullPath }

        render(databases, files).forEach {
            contentArea.appendChild(it)
        }

        js("window.jQuery(document).foundation();")
    }

    private fun parseSqlFile(file: SqlFile) {
        println("Parsing ${file.fullPath}")
        val input = ANTLRInputStream(file.contents)
        val lexer = SqlLexer(input)
        val tokens = CommonTokenStream(lexer)
        val parser = SqlParser(tokens)
        parser.addErrorListener(ConsoleErrorListener())
        val listener = FileListener(file)
        ParseTreeWalker.DEFAULT.walk(listener, parser.file())
        println(file.definedTables)
        println(file.queries)
    }


}

data class SqlFile(
        val fullPath: String,
        val name: String,
        val contents: String,
        val definedTables: MutableList<Table> = mutableListOf(),
        val queries: MutableList<Query> = mutableListOf()
) {
    override fun toString() = fullPath
}


data class Table(
        val database: String,
        val name: String,
        val fields: List<Field>,
        val temporary: Boolean,
        val comment: String?,
        val location: String?,
        val createTableStmt: String?,
        val definedIn: SqlFile?
)

data class Query(
        val tablesRead: List<Pair<String,String>>,
        val tablesWriten: List<Pair<String,String>>
)

data class Field(
        val name: String,
        val type: String,
        val nullable: Boolean = true,
        val partitionKey: Boolean = false,
        val _default: String?,
        val comment: String?
)




class FileListener(val file: SqlFile): SqlBaseListener() {
    private var tablesRead: MutableList<Pair<String, String>> = mutableListOf()
    private var tablesWriten: MutableList<Pair<String, String>> = mutableListOf()

    private var tablesInScope: MutableList<Pair<String, String>> = mutableListOf()

    override fun enterCreate_table_stmt(ctx: SqlParser.Create_table_stmtContext) {
        val name = tableIdentifierToPair(ctx.findTable_identifier()!!)
        val temporary = ctx.TEMPORARY() != null
        val comment = ctx.findCreate_table_comment_clause()?.STRING_LITERAL()?.let { stripString(it) }
        val location = ctx.findCreate_table_location_clause()?.STRING_LITERAL()?.let { stripString(it) }
        val content = ctx.position?.text(file.contents)
        var fields = ctx.findCreate_table_field_list()?.findField_spec()?.map { parseFieldSpec(it, false) } ?: listOf()
        fields += ctx.findCreate_table_partition_clause()?.findCreate_table_field_list()?.findField_spec()?.map { parseFieldSpec(it, true) } ?: listOf()
        file.definedTables.add(
                Table(
                        database = name.first,
                        name = name.second,
                        fields = fields,
                        comment = comment,
                        temporary = temporary,
                        definedIn = file,
                        location = location,
                        createTableStmt = content
                )
        )
    }

    override fun enterTop_level_insert_stmt(ctx: SqlParser.Top_level_insert_stmtContext) {
        tablesRead = mutableListOf()
        tablesWriten = mutableListOf()
        tablesInScope = tablesRead
    }

    override fun exitTop_level_insert_stmt(ctx: SqlParser.Top_level_insert_stmtContext) {
        file.queries.add(
                Query(tablesRead = tablesRead.distinct(), tablesWriten = tablesWriten.distinct())
        )
        tablesInScope = mutableListOf()
    }

    override fun enterTop_level_select_stmt(ctx: SqlParser.Top_level_select_stmtContext) {
        tablesRead = mutableListOf()
        tablesWriten = mutableListOf()
        tablesInScope = tablesRead
    }

    override fun exitTop_level_select_stmt(ctx: SqlParser.Top_level_select_stmtContext) {
        file.queries.add(
                Query(tablesRead = tablesRead.distinct(), tablesWriten = tablesWriten.distinct())
        )
        tablesInScope = mutableListOf()
    }

    override fun enterInsert_clause(ctx: SqlParser.Insert_clauseContext) {
        tablesInScope = tablesWriten
    }

    override fun exitInsert_clause(ctx: SqlParser.Insert_clauseContext) {
        tablesInScope = tablesRead
    }

    override fun enterTable_identifier(ctx: SqlParser.Table_identifierContext) {
        tablesInScope.add(tableIdentifierToPair(ctx))
    }

    private fun parseFieldSpec(ctx: SqlParser.Field_specContext, isPartition: Boolean): Field {
        val name = ctx.findIdentifier(0)!!.text.toUpperCase()
        val type = ctx.findType()!!.text.toUpperCase()
        val nullable = ctx.NOT() == null
        val default = ctx.findExpression()?.position?.text(file.contents)
        val comment = ctx.findCreate_table_comment_clause()?.STRING_LITERAL()?.let(::stripString)
        return Field(
                name = name,
                type = type,
                nullable = nullable,
                _default = default,
                comment = comment,
                partitionKey = isPartition
        )
    }


    private fun tableIdentifierToPair(ctx: SqlParser.Table_identifierContext): Pair<String, String> {
        val id = ctx.findQualified_identifier()!!
        return if(id.findIdentifier().size == 2) {
            id.findIdentifier(0)!!.text.toUpperCase() to id.findIdentifier(1)!!.text.toUpperCase()
        } else {
            "DEFAULT" to id.findIdentifier(0)!!.text.toUpperCase()
        }
    }

    private fun stripString(literal: TerminalNode): String {
        val text = literal.text
        return text.substring(1, text.lastIndex)
    }
}