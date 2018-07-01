package sqldepanalyzer

import org.w3c.dom.HTMLElement
import kotlinx.html.*
import kotlinx.html.dom.*
import kotlin.browser.document

fun render(allTables: Map<TableIdentifier, Table>, files: List<SqlFile>): List<HTMLElement> {
    val databases = allTables.values
            .filterNot { it.temporary }
            .groupBy { table -> table.id.database }
            .mapValues { (database, tables) ->
                tables.sortedBy { it.id.name }
            }

    val contents = renderTableOfContents(databases)

    val tablesView = allTables.values.filterNot { it.temporary }.sortedBy { it.id }.map{ renderTableDetails(allTables, it) }

    return listOf(contents) + tablesView
}

fun renderTableOfContents(tables: Map<String, List<Table>>): HTMLElement {
    return document.create.div {
        h3 { + "Tables" }
        ul {
            tables.keys.sorted().forEach { dbName ->
                li {
                    + dbName
                    ul {
                        tables[dbName]!!.forEach { table ->
                            li {
                                a(href = "#" + table.id.urlSafe()) {
                                    + table.id.name
                                }
                            }
                        }
                    }
                }
            }
        }
        hr()
    }
}

fun renderTableDetails(allTables: Map<TableIdentifier, Table>, table: Table): HTMLElement {
    return document.create.div("callout") {
        id = table.id.urlSafe()
        h5 { +"${table.id.database}.${table.id.name}" }
        table.comment?.let {
            blockQuote {
                + it
            }
        }
        table {
            thead {
                tr {
                    td { +"Name" }
                    td { +"Type" }
                    td { +"Nullable?" }
                    td { +"Partition?" }
                    td { +"Comment" }
                }
            }
            tbody {
                table.fields.forEach { field ->
                    tr {
                        td { +field.name }
                        td {
                            // Make sure the spaces in Decimal(1, 2) etc are non breaking,
                            // Allow breaking after "<" chars
                            +field.type.replace(",", ", ")
                                    .replace(Regex("(?!\\(\\d*), (?=\\d*\\))"), ",\u00A0")
                                    .replace("<", "<\u200B")
                        }
                        td { +field.nullable.toString() }
                        td { +field.partitionKey.toString() }
                        td { +field.comment.orEmpty() }
                    }
                }
            }
        }

        if (table.upstreamTables.isNotEmpty()) {
            h5 { +"Upstream Tables" }
            div("dep-list") { upstreamPartial(allTables, table) }
        }

        if(table.downstreamTables.isNotEmpty()) {
            h5 { +"Downstream Tables" }
            div("dep-list") { downstreamPartial(allTables, table) }
        }
        hr()
        h5 { + "Referenced by" }
        ul("dep-list") {
            table.referencedBy.sortedBy { it.fullPath }.forEach { file ->
                li { + file.fullPath }
            }
        }

        table.definedIn?.let {
            b { +"Defined in : " }
            +it.fullPath
        }

        table.createTableStmt?.let { content ->
            div("accordion tablesource") {
                attributes["data-accordion"] = ""
                attributes["data-allow-all-closed"] = "true"
                div("accordion-item") {
                    attributes["data-accordion-item"] = ""
                    a(href = "#", classes = "accordion-title") { +"Code" }
                    div("accordion-content") {
                        attributes["data-tab-content"] = ""
                        pre("code") {
                            + content
                        }
                    }
                }
            }
        }
    }
}

private fun FlowContent.upstreamPartial(allTables: Map<TableIdentifier, Table>, table: Table, alreadySeenTables: MutableSet<TableIdentifier> = mutableSetOf()) {
    val upstreams = upsteams(allTables, table)
    if (upstreams.isEmpty()) return
    ul {
        upstreams.forEach { upstream ->
            li {
                if (upstream.id in alreadySeenTables) {
                    a(href = "#" + upstream.id.urlSafe()) { +"${upstream.id.database}.${upstream.id.name} *" }
                } else {
                    a(href = "#" + upstream.id.urlSafe()) { +"${upstream.id.database}.${upstream.id.name}" }
                    alreadySeenTables += upstream.id
                    upstreamPartial(allTables, upstream, alreadySeenTables)
                }
            }
        }
    }
}


private fun FlowContent.downstreamPartial(allTables: Map<TableIdentifier, Table>, table: Table, alreadySeenTables: MutableSet<TableIdentifier> = mutableSetOf()) {
    val downstreams = downstreams(allTables, table)
    if (downstreams.isEmpty()) return
    ul {
        downstreams.forEach { downstream ->
            li {
                if (downstream.id in alreadySeenTables) {
                    a(href = "#" + downstream.id.urlSafe()) { +"${downstream.id.database}.${downstream.id.name} *" }
                } else {
                    a(href = "#" + downstream.id.urlSafe()) { +"${downstream.id.database}.${downstream.id.name}" }
                    alreadySeenTables += downstream.id
                    downstreamPartial(allTables, downstream, alreadySeenTables)
                }
            }
        }
    }
}

// Get upstream tables but inline temp tables
private fun upsteams(allTables: Map<TableIdentifier, Table>, table: Table): List<Table> {
    println("finding upstreams for ${table.id}")
    return table.upstreamTables.filter { it != table.id }.map { allTables[it]!! }.flatMap {t ->
        if (t.temporary) {
            upsteams(allTables, t)
        } else {
            listOf(t)
        }
    }.distinct().sortedBy { it.id }
}


private fun downstreams(allTables: Map<TableIdentifier, Table>, table: Table): List<Table> {
    println("finding downstreams for ${table.id}")
    return table.downstreamTables.filter { it != table.id }.map { allTables[it]!! }.flatMap {t ->
        if (t.temporary) {
            downstreams(allTables, t)
        } else {
            listOf(t)
        }
    }.distinct().sortedBy { it.id }
}

external fun decodeURIComponent(encodedURI: String): String

private fun TableIdentifier.urlSafe(): String {
    return decodeURIComponent("$database.$name")
}