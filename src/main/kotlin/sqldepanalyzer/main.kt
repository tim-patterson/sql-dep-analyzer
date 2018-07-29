package sqldepanalyzer

import kotlin.browser.document

// Require styles etc
val dummy = js("""
    require("foundation-sites/dist/css/foundation.min.css");
    window.jQuery = require("jquery/dist/jquery.min.js");
    require("foundation-sites/dist/js/foundation.min.js");
    require("css/styles.css");
    1; // The last line of this block gets compiled to dummy = 1; which then gets optimized out by dce
    // This is why we need this here, otherwise our last require gets optimized out.
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

