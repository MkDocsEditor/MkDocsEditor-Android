package de.markusressel.mkdocseditor.feature.filebrowser.data

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
 * Navigates the wiki tree by paths, the way MkDocs resolves links between files. Resolution
 * and link paths follow `docs/references.md` of the backend (see [LinkPaths]).
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

    private val itemsByPath: Map<String, WikiItem> by lazy {
        allItems().associateBy { pathOf(it) } + ("" to WikiItem.Section(root))
    }

    /** the index document of a section: `index.md`, else `README.md` */
    fun indexOf(section: SectionData): DocumentData? =
        section.documents.firstOrNull { it.name == "index" } ?: section.documents.firstOrNull { it.name == "README" }

    /** the item with this path below the docs folder (`""` is the root section) */
    fun itemAt(path: String): WikiItem? = itemsByPath[path]

    /**
     * Resolves a link or image destination written in a document of the folder [from], as
     * specified in `docs/references.md` of the backend: a section resolves to its index
     * document, or to itself without one.
     *
     * @return the target, or null for external links, anchors and broken destinations
     */
    fun resolve(from: SectionData, href: String): WikiItem? {
        val path = LinkPaths.resolvePath(documentPathIn(from), href) ?: return null
        return when (val item = itemsByPath[path]) {
            is WikiItem.Section -> indexOf(item.section)?.let { WikiItem.Document(it) } ?: item
            else -> item
        }
    }

    /**
     * The link destination from the document at [fromPath] to [target], computed with the
     * link path algorithm of the backend ([LinkPaths.linkPath])
     *
     * @param anchor optional heading id, without `#`
     */
    fun linkPath(fromPath: String, target: WikiItem, anchor: String = ""): String {
        val linkTarget = when (target) {
            is WikiItem.Section -> LinkPaths.Target(
                LinkPaths.TargetType.SECTION,
                pathOf(target),
                indexOf(target.section)?.let { pathOf(WikiItem.Document(it)) } ?: "",
            )
            is WikiItem.Document -> LinkPaths.Target(LinkPaths.TargetType.DOCUMENT, pathOf(target))
            is WikiItem.Resource -> LinkPaths.Target(LinkPaths.TargetType.RESOURCE, pathOf(target))
        }
        return LinkPaths.linkPath(fromPath, linkTarget, anchor)
    }

    /** the link destination from a document in the folder [from] to [target] */
    fun relativeLink(from: SectionData, target: WikiItem): String = linkPath(documentPathIn(from), target)

    /** a document path in [section] that no item has, for links that only need the folder */
    private fun documentPathIn(section: SectionData) = pathOf(section) + "/"

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
        fun isExternal(href: String): Boolean = LinkPaths.isExternal(href)

        /** percent-encodes a path segment like the link path algorithm does */
        fun encodeSegment(segment: String): String = LinkPaths.encode(segment)
    }
}
