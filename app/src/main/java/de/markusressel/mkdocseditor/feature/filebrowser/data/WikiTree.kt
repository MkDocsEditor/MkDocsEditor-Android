package de.markusressel.mkdocseditor.feature.filebrowser.data

import java.net.URLDecoder

/**
 * An item of the wiki tree that a link can point to
 */
sealed interface WikiItem {
    val id: String
    val name: String

    data class Section(val section: SectionData) : WikiItem {
        override val id get() = section.id
        override val name get() = section.name
    }

    data class Document(val document: DocumentData) : WikiItem {
        override val id get() = document.id
        override val name get() = document.name
    }

    data class Resource(val resource: ResourceData) : WikiItem {
        override val id get() = resource.id
        override val name get() = resource.name
    }
}

/**
 * Navigates the wiki tree by paths, the way MkDocs resolves links between files.
 * The web client resolves links the same way (`resolveRelative` in `src/stores/tree.ts`).
 *
 * @param root the root section of the wiki
 */
class WikiTree(val root: SectionData) {

    private val parents = HashMap<String, SectionData>()
    private val sectionsById = HashMap<String, SectionData>()
    private val documentParents = HashMap<String, SectionData>()
    private val resourceParents = HashMap<String, SectionData>()

    init {
        fun index(section: SectionData) {
            sectionsById[section.id] = section
            section.documents.forEach { documentParents[it.id] = section }
            section.resources.forEach { resourceParents[it.id] = section }
            section.subsections.forEach {
                parents[it.id] = section
                index(it)
            }
        }
        index(root)
    }

    fun section(id: String): SectionData? = sectionsById[id]

    fun parentOfSection(id: String): SectionData? = parents[id]

    fun parentOfDocument(id: String): SectionData? = documentParents[id]

    fun parentOfResource(id: String): SectionData? = resourceParents[id]

    fun parentOf(item: WikiItem): SectionData? = when (item) {
        is WikiItem.Section -> parentOfSection(item.id)
        is WikiItem.Document -> parentOfDocument(item.id)
        is WikiItem.Resource -> parentOfResource(item.id)
    }

    /** the sections from the root down to [section], both included */
    fun ancestorsAndSelf(section: SectionData): List<SectionData> {
        val result = mutableListOf(section)
        var current = parents[section.id]
        while (current != null) {
            result.add(0, current)
            current = parents[current.id]
        }
        return result
    }

    /** the path of a section below the root, like `a/b`, empty for the root */
    fun pathOf(section: SectionData): String =
        ancestorsAndSelf(section).drop(1).joinToString("/") { it.name }

    /** the path of an item below the root: `a/b`, `a/page.md`, `a/image.png` */
    fun pathOf(item: WikiItem): String {
        val parent = parentOf(item)
        val prefix = parent?.let { pathOf(it) }?.takeIf { it.isNotEmpty() }?.let { "$it/" } ?: ""
        return when (item) {
            is WikiItem.Section -> pathOf(item.section)
            is WikiItem.Document -> prefix + item.name + ".md"
            is WikiItem.Resource -> prefix + item.name
        }
    }

    /**
     * Resolves a link or image target relative to [from], the folder of the current document.
     *
     * @return the target, or null for external links, anchors and targets that don't exist
     */
    fun resolve(from: SectionData, href: String): WikiItem? {
        if (isExternal(href) || href.startsWith("#")) return null
        val path = href.split('?', '#').first()
        if (path.isEmpty()) return null
        var segments = try {
            path.split('/').map { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }
        } catch (e: IllegalArgumentException) {
            return null
        }
        var current = from
        if (path.startsWith("/")) {
            current = root
            segments = segments.drop(1)
        }
        val directoryUrl = segments.lastOrNull() == ""
        if (directoryUrl) segments = segments.dropLast(1)
        segments.forEachIndexed { i, segment ->
            val last = i == segments.lastIndex
            when (segment) {
                ".", "" -> return@forEachIndexed
                ".." -> {
                    current = parents[current.id] ?: return null
                    return@forEachIndexed
                }
            }
            if (last && !directoryUrl) {
                current.documents.firstOrNull { "${it.name}.md" == segment }?.let { return WikiItem.Document(it) }
                current.resources.firstOrNull { it.name == segment }?.let { return WikiItem.Resource(it) }
            }
            val sub = current.subsections.firstOrNull { it.name == segment }
            if (sub != null) {
                current = sub
            } else {
                // MkDocs directory URLs: `page/` is `page.md`
                if (last && directoryUrl) {
                    return current.documents.firstOrNull { it.name == segment }?.let { WikiItem.Document(it) }
                }
                return null
            }
        }
        if (directoryUrl) {
            return current.documents.firstOrNull { it.name == "index" }?.let { WikiItem.Document(it) }
                ?: WikiItem.Section(current)
        }
        return WikiItem.Section(current)
    }

    /**
     * The relative link from a document in [from] to [target], like MkDocs expects it:
     * `../other/page.md`, `image.png`, `sub/` for a folder. Path segments are URL encoded where
     * needed (spaces become `%20`).
     */
    fun relativeLink(from: SectionData, target: WikiItem): String {
        val targetSection = when (target) {
            is WikiItem.Section -> target.section
            else -> parentOf(target) ?: root
        }
        val fromChain = ancestorsAndSelf(from)
        val toChain = ancestorsAndSelf(targetSection)
        var common = 0
        while (common < fromChain.size && common < toChain.size && fromChain[common].id == toChain[common].id) {
            common++
        }
        val up = List(fromChain.size - common) { ".." }
        val down = toChain.drop(common).map { encodeSegment(it.name) }
        val folder = (up + down)
        return when (target) {
            is WikiItem.Section -> if (folder.isEmpty()) "./" else folder.joinToString("/") + "/"
            is WikiItem.Document -> (folder + encodeSegment(target.name + ".md")).joinToString("/")
            is WikiItem.Resource -> (folder + encodeSegment(target.name)).joinToString("/")
        }
    }

    /** all items of the tree, depth first */
    fun allItems(): List<WikiItem> {
        val result = mutableListOf<WikiItem>()
        fun collect(section: SectionData) {
            if (section.id != root.id) result += WikiItem.Section(section)
            section.documents.forEach { result += WikiItem.Document(it) }
            section.resources.forEach { result += WikiItem.Resource(it) }
            section.subsections.forEach { collect(it) }
        }
        collect(root)
        return result
    }

    companion object {
        fun isExternal(href: String): Boolean =
            href.startsWith("//") || "^[a-zA-Z][a-zA-Z0-9+.-]*:".toRegex().containsMatchIn(href)

        /**
         * Encodes the characters of a path segment that would break a markdown link or change
         * its meaning: `%`, space, `(`, `)`, `#`, `?`, `<`, `>`. Other characters, umlauts too,
         * stay readable; MkDocs accepts them as they are.
         */
        fun encodeSegment(segment: String): String = buildString {
            segment.forEach {
                when (it) {
                    '%' -> append("%25")
                    ' ' -> append("%20")
                    '(' -> append("%28")
                    ')' -> append("%29")
                    '#' -> append("%23")
                    '?' -> append("%3F")
                    '<' -> append("%3C")
                    '>' -> append("%3E")
                    else -> append(it)
                }
            }
        }
    }
}
