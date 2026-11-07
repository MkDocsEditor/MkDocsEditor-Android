package de.markusressel.mkdocseditor.feature.qr.domain

import de.markusressel.mkdocseditor.feature.editor.domain.SummaryText
import java.text.Normalizer

/**
 * A QR code key of a page, with where its sticker is
 *
 * @param label null without one
 */
internal data class QrKey(val key: String, val label: String? = null)

/**
 * QR code keys in the front matter of a page (`docs/qr.md` of the backend): `qr: waschmaschine`, a list of keys, or
 * entries `{key, label}`, also mixed. The same rules as `markdown/qrKeys.ts` of the web client, so both write the same.
 *
 * There is no YAML parser in the app, so this reads only the `qr` entry, in the forms the clients write and the
 * backend documents. Anything else in it counts as no key, like the backend does with values it can't use.
 */
internal object QrKeys {

    private val KEY = Regex("^[a-z0-9][a-z0-9-]{0,63}$")

    private const val MAX_LENGTH = 64

    /** the key as the backend compares it, null for a value that is no key */
    fun normalize(value: String): String? = value.trim().lowercase().takeIf { KEY.matches(it) }

    /** a key made from a page name: "Waschmaschine (Keller)" becomes `waschmaschine-keller` */
    fun suggest(name: String): String {
        val replaced = name.lowercase()
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        return Normalizer.normalize(replaced, Normalizer.Form.NFKD)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(MAX_LENGTH)
            .trimEnd('-')
    }

    /** the valid keys of a page, each once, in their order */
    fun keysOf(text: String): List<QrKey> {
        val lines = text.split('\n')
        val entry = entryOf(lines, SummaryText.frontMatterLines(text)) ?: return emptyList()
        val keys = mutableListOf<QrKey>()
        for (value in values(lines.subList(entry.first, entry.last + 1).map { it.trimEnd('\r') })) {
            val key = value.key?.let(::normalize) ?: continue
            if (keys.any { it.key == key }) continue
            keys += QrKey(key, value.label?.trim()?.takeIf { it.isNotEmpty() })
        }
        return keys
    }

    /**
     * The text with [keys] as its `qr` entry: the entry is replaced, added before the end of the front matter, or a
     * front matter is added at the top. No keys removes the entry. The rest of the text stays as it is.
     */
    fun withKeys(text: String, keys: List<QrKey>): String {
        val count = SummaryText.frontMatterLines(text)
        if (count == 0) {
            if (keys.isEmpty()) return text
            return (listOf("---") + lines(keys) + "---" + if (text.isEmpty()) listOf("") else listOf("", text)).joinToString("\n")
        }
        val lines = text.split('\n').toMutableList()
        val entry = entryOf(lines, count)
        val replacement = if (keys.isEmpty()) emptyList() else lines(keys)
        if (entry != null) {
            repeat(entry.last - entry.first + 1) { lines.removeAt(entry.first) }
            lines.addAll(entry.first, replacement)
        } else {
            lines.addAll(count - 1, replacement)
        }
        return lines.joinToString("\n")
    }

    /** the lines of the `qr` entry: its own and the indented or listed ones after it, null without one */
    private fun entryOf(lines: List<String>, frontMatterLines: Int): IntRange? {
        if (frontMatterLines == 0) return null
        val closing = frontMatterLines - 1
        val start = (1 until closing).firstOrNull { ENTRY.containsMatchIn(lines[it]) } ?: return null
        var end = start
        while (end + 1 < closing && CONTINUATION.containsMatchIn(lines[end + 1])) end++
        // blank lines at its end belong to what follows
        while (end > start && lines[end].isBlank()) end--
        return start..end
    }

    private val ENTRY = Regex("^qr[ \\t]*:")
    private val CONTINUATION = Regex("^([ \\t]+\\S|-[ \\t]|-\\r?$|[ \\t]*\\r?$)")

    /** a value of the entry: a key, or null for a value that is no text */
    private class Value(val key: String?, val label: String? = null)

    private fun values(entry: List<String>): List<Value> {
        val inline = entry.first().substringAfter(':').let(::withoutComment).trim()
        if (inline.isNotEmpty()) {
            if (inline.startsWith("[")) {
                if (!inline.endsWith("]")) return emptyList()
                return splitFlow(inline.substring(1, inline.length - 1)).map { Value(scalar(it)) }
            }
            return listOf(Value(scalar(inline)))
        }
        // a block list: each item starts with "- ", a mapping item goes on in deeper lines
        val items = mutableListOf<MutableList<String>>()
        var itemIndent = -1
        for (line in entry.drop(1)) {
            if (line.isBlank()) continue
            val indent = line.length - line.trimStart().length
            val content = line.trimStart()
            if (content.startsWith("-") && (content.length == 1 || content[1] == ' ' || content[1] == '\t') &&
                (itemIndent < 0 || indent <= itemIndent)
            ) {
                itemIndent = indent
                items += mutableListOf(content.drop(1).trim())
            } else {
                items.lastOrNull()?.add(content)
            }
        }
        return items.map(::item)
    }

    /** one item of a block list: a key, or a mapping with `key` and `label` */
    private fun item(lines: List<String>): Value {
        val first = withoutComment(lines.first()).trim()
        val fields = lines.mapNotNull { line ->
            val match = FIELD.find(withoutComment(line).trim()) ?: return@mapNotNull null
            match.groupValues[1] to match.groupValues[2].trim()
        }.toMap()
        if (FIELD.containsMatchIn(first) && !first.startsWith("\"") && !first.startsWith("'")) {
            return Value(fields["key"]?.let(::scalar), fields["label"]?.let(::scalar))
        }
        // a nested list or mapping is no key
        if (first.startsWith("[") || first.startsWith("{")) return Value(null)
        return Value(scalar(first))
    }

    private val FIELD = Regex("^([A-Za-z_][A-Za-z0-9_-]*)[ \\t]*:(?:[ \\t]+(.*)|$)")

    /** a YAML scalar as text, null for what YAML reads as no text (booleans, null) */
    private fun scalar(raw: String): String? {
        val value = raw.trim()
        return when {
            value.length >= 2 && value.startsWith("\"") && value.endsWith("\"") -> unescapeDouble(value.substring(1, value.length - 1))
            value.length >= 2 && value.startsWith("'") && value.endsWith("'") -> value.substring(1, value.length - 1).replace("''", "'")
            value.isEmpty() || value.lowercase() in NOT_TEXT -> null
            else -> value
        }
    }

    private val NOT_TEXT = setOf("true", "false", "null", "~")

    private fun unescapeDouble(value: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'n' -> result.append('\n')
                    't' -> result.append('\t')
                    'u' -> if (i + 5 < value.length) {
                        result.append(value.substring(i + 2, i + 6).toIntOrNull(16)?.toChar() ?: '?')
                        i += 4
                    }
                    else -> result.append(next)
                }
                i += 2
            } else {
                result.append(c)
                i++
            }
        }
        return result.toString()
    }

    /** the items of a flow list, split at commas outside of quotes */
    private fun splitFlow(content: String): List<String> {
        if (content.isBlank()) return emptyList()
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        for (c in content) {
            when {
                quote != null -> {
                    current.append(c)
                    if (c == quote) quote = null
                }
                c == '"' || c == '\'' -> {
                    quote = c
                    current.append(c)
                }
                c == ',' -> {
                    parts += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
        }
        parts += current.toString()
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** the line without a comment: a `#` after a blank, outside of quotes */
    private fun withoutComment(line: String): String {
        var quote: Char? = null
        for (i in line.indices) {
            val c = line[i]
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == '#' && (i == 0 || line[i - 1] == ' ' || line[i - 1] == '\t') -> return line.substring(0, i)
            }
        }
        return line
    }

    /** the `qr` entry for [keys], as lines */
    private fun lines(keys: List<QrKey>): List<String> {
        if (keys.all { it.label.isNullOrEmpty() }) {
            return if (keys.size == 1) listOf("qr: ${yamlKey(keys.single().key)}")
            else listOf("qr: [${keys.joinToString(", ") { yamlKey(it.key) }}]")
        }
        val lines = mutableListOf("qr:")
        for ((key, label) in keys) {
            if (label.isNullOrEmpty()) {
                lines += "  - ${yamlKey(key)}"
            } else {
                lines += "  - key: ${yamlKey(key)}"
                lines += "    label: ${quoted(label)}"
            }
        }
        return lines
    }

    private val PLAIN_KEY = Regex("^[a-z][a-z0-9-]*$")
    private val YAML_WORDS = setOf("true", "false", "yes", "no", "on", "off", "null", "y", "n")

    /** a key as YAML: quoted when YAML would read it as something else than text */
    private fun yamlKey(key: String): String = if (PLAIN_KEY.matches(key) && key !in YAML_WORDS) key else quoted(key)

    /** a double-quoted YAML string, the same as JSON */
    private fun quoted(value: String): String = buildString {
        append('"')
        for (c in value) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }
}
