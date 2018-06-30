package sqldepanalyzer

import kotlin.browser.document

// Require styles etc
val dummy = js("""
    require("../node_modules/foundation-sites/dist/css/foundation.min.css");
    window.jQuery = require("../node_modules/jquery/dist/jquery.min.js");
    require("../node_modules/foundation-sites/dist/js/foundation.min.js");
    require("../resources/main/styles.css");
""")


fun main(args: Array<String>) {
    document.addEventListener("DOMContentLoaded", {
        println("initializing")

        val dropArea = document.getElementById("drop-area")!!
        val contentArea = document.getElementById("content-area")!!

        Application(dropArea, contentArea)
        println("initialization done")
    })
}

