package sqldepanalyzer

import kotlin.browser.document

// Require styles
external fun require(name: String): dynamic
val foundationStyles = require("../node_modules/foundation-sites/dist/css/foundation.min.css")
val appStyles = require("../resources/main/styles.css")


fun main(args: Array<String>) {
    document.addEventListener("DOMContentLoaded", {
        println("initializing")

        val dropArea = document.getElementById("drop-area")!!

        Application(dropArea)
        println("initialization done")
    })
}

