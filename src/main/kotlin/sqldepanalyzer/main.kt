package sqldepanalyzer

import org.antlr.v4.kotlinruntime.ANTLRInputStream
import org.antlr.v4.kotlinruntime.CommonTokenStream
import kotlin.browser.document

// Require styles
external fun require(name: String): dynamic
val foundationStyles = require("../node_modules/foundation-sites/dist/css/foundation.min.css")
val appStyles = require("../resources/main/styles.css")


fun main(args: Array<String>) {
//    document.addEventListener("DOMContentLoaded", {
//        println("initializing")
//
//        val dropArea = document.getElementById("drop-area")!!
//
//        Application(dropArea)
//        println("initialization done")
//    })
    val query = "select foo, bar + 2 from json 'something';"
    val ins = ANTLRInputStream(query)
    val lexer = SqlLexer(ins)
    val tokens = CommonTokenStream(lexer)
    val parser = SqlParser(tokens)
    console.log(parser.stmt())
}

