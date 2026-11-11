package io.github.jlai403.pocketsimengineer.app

import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The manifest resolves relative component names against the Gradle namespace,
 * while the Kotlin sources live a package deeper (`…pocketsimengineer.app`).
 * A mismatch makes `startService` fail at runtime with "not found", which only
 * shows up in logcat. This test fails the build instead.
 */
class ManifestComponentsTest {
    @Test
    fun `every manifest component resolves to a class`() {
        val manifest = File("src/main/AndroidManifest.xml")
        check(manifest.isFile) { "Manifest not found at ${manifest.absolutePath}" }
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(manifest)
        val application = document.getElementsByTagName("application")
            .item(0) as Element

        val names = buildList {
            add(application.getAttribute("android:name"))
            listOf("activity", "service", "receiver", "provider").forEach { tag ->
                val nodes = application.getElementsByTagName(tag)
                for (index in 0 until nodes.length) {
                    val element = nodes.item(index) as Element
                    add(element.getAttribute("android:name"))
                }
            }
        }.filter { it.isNotBlank() }

        val failures = names.mapNotNull { name ->
            val qualified = if (name.startsWith(".")) NAMESPACE + name else name
            runCatching { Class.forName(qualified) }.exceptionOrNull()?.let { name }
        }
        if (failures.isNotEmpty()) {
            fail("Manifest components without a class: ${failures.joinToString()}")
        }
    }

    private companion object {
        const val NAMESPACE = "io.github.jlai403.pocketsimengineer"
    }
}
