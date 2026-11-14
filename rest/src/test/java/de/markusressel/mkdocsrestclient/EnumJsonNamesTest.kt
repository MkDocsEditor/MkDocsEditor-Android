package de.markusressel.mkdocsrestclient

import com.squareup.moshi.Json
import java.io.File
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * R8 drops the `@Json` names of enum constants in the release build, and Moshi reads them by reflection, so such an
 * enum parses in tests but fails in the app. API models keep those values as text instead.
 */
class EnumJsonNamesTest {

    @Test
    fun `no enum of the API models takes its JSON names from annotations`() {
        val location = File(IMkDocsRestClient::class.java.protectionDomain!!.codeSource.location.toURI())
        val classFiles = if (location.isDirectory) {
            location.walk().filter { it.extension == "class" }.map { it.relativeTo(location).invariantSeparatorsPath }.toList()
        } else {
            JarFile(location).use { jar -> jar.entries().toList().map { it.name }.filter { it.endsWith(".class") } }
        }
        val enums = classFiles
            .filter { it.startsWith("de/markusressel/mkdocsrestclient/") }
            .map { Class.forName(it.removeSuffix(".class").replace('/', '.'), false, javaClass.classLoader) }
            .filter { it.isEnum }
        assertTrue(enums.isNotEmpty(), "the classes were found")

        val annotated = enums.flatMap { enum ->
            enum.declaredFields.filter { it.isEnumConstant && it.isAnnotationPresent(Json::class.java) }.map { "${enum.simpleName}.${it.name}" }
        }
        assertEquals(emptyList(), annotated)
    }
}
