package de.markusressel.mkdocsrestclient.sync.text

/**
 * Three-way merge of plain texts: moves the local edits (from [base] to [local])
 * onto [server], a text that was edited independently from the same [base].
 *
 * Used when the server started a new document lineage (a new epoch) and the client
 * still holds unsynced edits made against the old one.
 *
 * @return splices (applied in order) that apply the local edits to [server]
 */
fun rebaseSplices(base: String, local: String, server: String): List<TextSplice> {
    val localSplices = TextDiff.diff(base, local)
    if (localSplices.isEmpty()) {
        return emptyList()
    }
    val serverSplices = TextDiff.diff(base, server)

    var localDelta = 0
    var resultDelta = 0
    return localSplices.map { splice ->
        // position of this splice in the base text
        val baseStart = splice.index - localDelta
        val baseEnd = baseStart + splice.deleteCount
        localDelta += splice.insert.length - splice.deleteCount

        // the same range in the server text
        val start = transformIndex(baseStart, serverSplices)
        val end = maxOf(start, transformIndex(baseEnd, serverSplices))

        TextSplice(index = start + resultDelta, deleteCount = end - start, insert = splice.insert).also {
            resultDelta += it.insert.length - it.deleteCount
        }
    }
}
