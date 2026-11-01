package de.markusressel.mkdocseditor.feature.filebrowser.data

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkPathsTest {

    /**
     * The test vectors of the backend (`docs/link-paths.json`, copied unchanged). Clients must
     * produce the same link paths as the backend, which rewrites links when items move.
     */
    @Test
    fun `backend link path vectors`() {
        val json = javaClass.classLoader!!.getResource("link-paths.json")!!.readText()
        val vectors = JsonParser.parseString(json).asJsonObject.getAsJsonArray("vectors")
        assertTrue(vectors.size() > 0)
        val failures = vectors.map { it.asJsonObject }.mapNotNull { vector ->
            val target = vector.getAsJsonObject("target")
            val result = LinkPaths.linkPath(
                fromPath = vector.get("from").asString,
                target = LinkPaths.Target(
                    type = LinkPaths.TargetType.valueOf(target.get("type").asString.uppercase()),
                    path = target.get("path").asString,
                    index = target.get("index")?.asString ?: "",
                ),
                anchor = vector.get("anchor")?.asString ?: "",
            )
            val expected = vector.get("expected").asString
            if (result != expected) "${vector.get("name").asString}: expected <$expected>, got <$result>" else null
        }
        assertEquals(emptyList(), failures)
    }

    @Test
    fun `resolves paths relative to the document`() {
        assertEquals("guide/install.md", LinkPaths.resolvePath("guide/setup.md", "install.md#step-1"))
        assertEquals("index.md", LinkPaths.resolvePath("guide/setup.md", "../index.md?x=1"))
        assertEquals("guide/img/photo 1.png", LinkPaths.resolvePath("guide/setup.md", "./img/photo%201.png"))
        assertEquals("a/Küche.md", LinkPaths.resolvePath("index.md", "a/K%C3%BCche.md"))
        assertEquals("guide", LinkPaths.resolvePath("index.md", "guide/"))
        assertEquals("", LinkPaths.resolvePath("guide/setup.md", "../"))
    }

    @Test
    fun `a leading slash is relative to the docs folder`() {
        assertEquals("guide/setup.md", LinkPaths.resolvePath("a/b/c.md", "/guide/setup.md"))
    }

    @Test
    fun `decoding is lenient`() {
        assertEquals("100%zz.md", LinkPaths.percentDecode("100%zz.md"))
        assertEquals("a+b.md", LinkPaths.percentDecode("a+b.md"))
        assertEquals("50%", LinkPaths.percentDecode("50%"))
        assertEquals("a b%2", LinkPaths.percentDecode("a%20b%2"))
    }

    @Test
    fun `external destinations anchors and paths outside the docs folder resolve to nothing`() {
        assertNull(LinkPaths.resolvePath("index.md", "https://example.com/a.md"))
        assertNull(LinkPaths.resolvePath("index.md", "mailto:a@b.c"))
        assertNull(LinkPaths.resolvePath("index.md", "//cdn.example.com/x.png"))
        assertNull(LinkPaths.resolvePath("index.md", "#heading"))
        assertNull(LinkPaths.resolvePath("index.md", "../outside.md"))
        assertNull(LinkPaths.resolvePath("guide/a.md", "/../x.md"))
    }

    @Test
    fun `encodes the specified characters only`() {
        assertEquals("my%20file%20%28v2%29%23%25.md", LinkPaths.encode("my file (v2)#%.md"))
        assertEquals("%22%27%3C%3E%3F%5B%5C%5D%5E%60%7B%7C%7D", LinkPaths.encode("\"'<>?[\\]^`{|}"))
        assertEquals("%09%7F", LinkPaths.encode("\t\u007F"))
        assertEquals("Küche-日本語+&=!.md", LinkPaths.encode("Küche-日本語+&=!.md"))
    }
}
