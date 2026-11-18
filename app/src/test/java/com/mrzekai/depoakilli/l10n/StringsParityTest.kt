package com.mrzekai.depoakilli.l10n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class StringsParityTest {
    private val directories = listOf(
        "values", "values-tr", "values-es", "values-b+pt+BR", "values-it",
        "values-fr", "values-de", "values-ru", "values-in", "values-ar",
    )
    private val placeholders = Regex("%(?:[0-9]+\\$)?[-#+ 0,(<]*[0-9]*(?:\\.[0-9]+)?[a-zA-Z]")

    private fun read(directory: File): Map<String, Set<List<String>>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(directory, "strings.xml"))
        val nodes = document.documentElement.childNodes
        val result = mutableMapOf<String, Set<List<String>>>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            if (node.tagName !in setOf("string", "plurals")) continue
            val values = if (node.tagName == "string") {
                listOf(node.textContent)
            } else {
                val items = node.getElementsByTagName("item")
                (0 until items.length).map { items.item(it).textContent }
            }
            result["${node.tagName}:${node.getAttribute("name")}"] =
                values.map { value -> placeholders.findAll(value.replace("%%", "")).map { it.value }.sorted().toList() }.toSet()
        }
        return result
    }

    @Test fun allLocalesHaveMatchingKeysAndFormatArguments() {
        val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val english = read(File(root, directories.first()))
        for (directory in directories.drop(1)) {
            val translated = read(File(root, directory))
            assertEquals("Keys in $directory", english.keys, translated.keys)
            for (key in english.keys) {
                assertEquals("Placeholders for $key in $directory", english[key], translated[key])
            }
        }
    }
}
