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
        // Add in any tables referenced but not defined
        val allDefinedTables = sqlFiles.flatMap { file ->
            file.definedTables
        }.associateBy { it.id }

        val allReferencedTables = sqlFiles.flatMap { it.referencedTables }.toSet()

        val missingTableIds = allReferencedTables - allDefinedTables.keys

        val missingTables = missingTableIds.map { Table(
                id = it,
                fields = listOf(),
                temporary = false
        )}.associateBy { it.id }

        val allTables = allDefinedTables + missingTables

        // Populate table.referencedBy
        sqlFiles.forEach { file ->
            file.referencedTables.forEach { id ->
                allTables[id]!!.referencedBy.add(file)
            }
        }

        // Populate read and written tables
        val allQueries = sqlFiles.flatMap { it.queries }

        allQueries.forEach { query ->
            query.tablesRead.forEach { allTables[it]!!.downstreamTables.addAll(query.tablesWritten) }
            query.tablesWritten.forEach { allTables[it]!!.upstreamTables.addAll(query.tablesRead) }
        }

        val files = sqlFiles.sortedBy { it.fullPath }

        render(allTables, files).forEach {
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
    }


}

data class SqlFile(
        val fullPath: String,
        val name: String,
        val contents: String,
        val definedTables: MutableList<Table> = mutableListOf(),
        val queries: MutableList<Query> = mutableListOf(),
        val referencedTables: MutableSet<TableIdentifier> = mutableSetOf()
) {
    override fun toString() = fullPath
}

// Uniquely identifies a table,
// scope is used to tables scoped to a file (ie temp tables)
data class TableIdentifier(
        val scope: String,
        val database: String,
        val name: String
): Comparable<TableIdentifier> {
    override fun compareTo(other: TableIdentifier): Int {
        var c = this.scope.compareTo(other.scope)
        if (c != 0) {
            return c
        }
        c = this.database.compareTo(other.database)
        if (c != 0) {
            return c
        }
        return this.name.compareTo(other.name)
    }
}

data class Table(
        val id: TableIdentifier,
        val fields: List<Field>,
        val temporary: Boolean,
        val comment: String? = null,
        val location: String? = null,
        val createTableStmt: String? = null,
        val definedIn: SqlFile? = null,
        val upstreamTables: MutableSet<TableIdentifier> = mutableSetOf(),
        val downstreamTables: MutableSet<TableIdentifier> = mutableSetOf(),
        val referencedBy: MutableSet<SqlFile> = mutableSetOf()
) {
    override fun equals(other: Any?) = if (other is Table) id.equals(other.id) else false
    override fun hashCode() = id.hashCode()
}

data class Query(
        val tablesRead: Set<TableIdentifier>,
        val tablesWritten: Set<TableIdentifier>
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
    private val tempTablesDefinedSoFar = mutableSetOf<TableIdentifier>()

    // These are just in the scope of an individual query
    private var cteAliasesInScope = mutableSetOf<String>()
    private var tablesRead = mutableSetOf<TableIdentifier>()
    private var tablesWritten = mutableSetOf<TableIdentifier>()

    // In practice should be an alias to one of the two sets defined above
    // most of the time
    private var tablesInScope = mutableSetOf<TableIdentifier>()

    override fun enterCreate_table_stmt(ctx: SqlParser.Create_table_stmtContext) {
        val (db, name) = tableIdentifierToPair(ctx.findTable_identifier()!!)
        val temporary = ctx.TEMPORARY() != null
        val scope = if(temporary) file.fullPath else ""
        val id = TableIdentifier(scope, db, name)
        val comment = ctx.findCreate_table_comment_clause()?.STRING_LITERAL()?.let { stripString(it) }
        val location = ctx.findCreate_table_location_clause()?.STRING_LITERAL()?.let { stripString(it) }
        val content = ctx.position?.text(file.contents)
        var fields = ctx.findCreate_table_field_list()?.findField_spec()?.map { parseFieldSpec(it, false) } ?: listOf()
        fields += ctx.findCreate_table_partition_clause()?.findCreate_table_field_list()?.findField_spec()?.map { parseFieldSpec(it, true) } ?: listOf()

        file.definedTables.add(
                Table(
                        id = id,
                        fields = fields,
                        comment = comment,
                        temporary = temporary,
                        definedIn = file,
                        location = location,
                        createTableStmt = content
                )
        )

        if (temporary) { tempTablesDefinedSoFar.add(id) }
    }

    // We'll just treat a view like a table and a query...
    override fun enterCreate_view_stmt(ctx: SqlParser.Create_view_stmtContext) {
        tablesRead = mutableSetOf()
        tablesInScope = tablesRead
    }

    override fun exitCreate_view_stmt(ctx: SqlParser.Create_view_stmtContext) {
        val (db, name) = tableIdentifierToPair(ctx.findTable_identifier()!!)
        val id = TableIdentifier("", db, name)
        val content = ctx.position?.text(file.contents)
        file.definedTables.add(
                Table(
                        id = id,
                        fields = listOf(),
                        definedIn = file,
                        temporary = false,
                        createTableStmt = content
                )
        )

        file.queries.add(
                Query(tablesRead = tablesRead, tablesWritten = mutableSetOf(id))
        )
    }

    override fun enterTop_level_insert_stmt(ctx: SqlParser.Top_level_insert_stmtContext) {
        cteAliasesInScope = mutableSetOf()
        tablesRead = mutableSetOf()
        tablesWritten = mutableSetOf()
        tablesInScope = tablesRead
    }

    override fun exitTop_level_insert_stmt(ctx: SqlParser.Top_level_insert_stmtContext) {
        file.queries.add(
                Query(tablesRead = tablesRead, tablesWritten = tablesWritten)
        )
        tablesInScope = mutableSetOf()
        cteAliasesInScope = mutableSetOf()
    }

    override fun enterTop_level_select_stmt(ctx: SqlParser.Top_level_select_stmtContext) {
        tablesRead = mutableSetOf()
        tablesWritten = mutableSetOf()
        tablesInScope = tablesRead
    }

    override fun exitTop_level_select_stmt(ctx: SqlParser.Top_level_select_stmtContext) {
        file.queries.add(
                Query(tablesRead = tablesRead, tablesWritten = tablesWritten)
        )
        tablesInScope = mutableSetOf()
        cteAliasesInScope = mutableSetOf()
    }

    override fun enterInsert_clause(ctx: SqlParser.Insert_clauseContext) {
        tablesInScope = tablesWritten
    }

    override fun exitInsert_clause(ctx: SqlParser.Insert_clauseContext) {
        tablesInScope = tablesRead
    }

    override fun exitCte_sub_clause(ctx: SqlParser.Cte_sub_clauseContext) {
        cteAliasesInScope.add(ctx.findIdentifier()!!.text.toUpperCase())
    }

    override fun enterTable_identifier(ctx: SqlParser.Table_identifierContext) {
        val (db, name) = tableIdentifierToPair(ctx)
        if (db != "DEFAULT" || name !in cteAliasesInScope) {
            val tempId = TableIdentifier(file.fullPath, db, name)
            if (tempId in tempTablesDefinedSoFar) {
                tablesInScope.add(tempId)
            } else {
                val id = TableIdentifier("", db, name)
                file.referencedTables.add(id)
                tablesInScope.add(id)
            }
        }
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