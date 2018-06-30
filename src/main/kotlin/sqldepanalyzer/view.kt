package sqldepanalyzer

import org.w3c.dom.HTMLElement
import kotlinx.html.*
import kotlinx.html.dom.*
import kotlin.browser.document

external fun decodeURIComponent(encodedURI: String): String

fun render(tables: Map<String, List<Table>>, files: List<SqlFile>): List<HTMLElement> {
    val contents = renderTableOfContents(tables)

    val tables = tables.keys.sorted().flatMap { dbName ->
        tables[dbName]!!
    }.map(::renderTableDetails)

    return listOf(contents) + tables
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
                                a(href = "#" + decodeURIComponent("${table.database}.${table.name}")) {
                                    + table.name
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

fun renderTableDetails(table: Table): HTMLElement {
    return document.create.div("callout") {
        id = "${table.database}.${table.name}"
        h5 { +"${table.database}.${table.name}" }
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
        b { + "Defined in : " }
        + table.definedIn!!.fullPath
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