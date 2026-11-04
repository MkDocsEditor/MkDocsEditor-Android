package de.markusressel.mkdocseditor.feature.backendconfig.common.data

/** Where [ScopeDirectories] remembers its choices */
interface ScopeTable {
    operator fun get(key: String): String?
    operator fun set(key: String, value: String)
    fun remove(key: String)
    fun keys(): Set<String>
}

/**
 * Decides where the wiki data of a server and account lives. The first pair ever seen gets [EXISTING], the storage from
 * before data was kept per server, so an update moves nothing. Signing out keeps the last account's data, because it
 * stays readable offline, and a first sign-in keeps what was stored before it.
 */
class ScopeDirectories(private val table: ScopeTable) {

    /** @param account the signed-in account, null if there is none right now */
    fun directoryOf(backendId: Long, account: String?): String {
        val last = table[lastKey(backendId)]
        val name = account ?: last ?: NO_ACCOUNT
        if (account != null && last == NO_ACCOUNT) {
            table[directoryKey(backendId, NO_ACCOUNT)]?.let { table[directoryKey(backendId, account)] = it }
            table.remove(directoryKey(backendId, NO_ACCOUNT))
        }
        table[lastKey(backendId)] = name
        return table[directoryKey(backendId, name)] ?: newDirectory().also { table[directoryKey(backendId, name)] = it }
    }

    /** @return the directories of a server */
    fun directoriesOf(backendId: Long): List<String> = keysOf(backendId).mapNotNull { table[it] }

    /** @return the directories of all servers */
    fun all(): Set<String> = table.keys().filter { it.startsWith(DIRECTORY_PREFIX) }.mapNotNull { table[it] }.toSet()

    /** Forgets a removed server. @return the directories it used */
    fun forget(backendId: Long): List<String> {
        table.remove(lastKey(backendId))
        return keysOf(backendId).mapNotNull { key -> table[key].also { table.remove(key) } }
    }

    private fun keysOf(backendId: Long) = table.keys().filter { it.startsWith(directoryKey(backendId, "")) }

    private fun newDirectory(): String {
        if (table[EXISTING_CLAIMED] == null) {
            table[EXISTING_CLAIMED] = "true"
            return EXISTING
        }
        val next = table[NEXT]?.toInt() ?: 1
        table[NEXT] = (next + 1).toString()
        return "s$next"
    }

    private fun lastKey(backendId: Long) = "last.$backendId"
    private fun directoryKey(backendId: Long, account: String) = "$DIRECTORY_PREFIX$backendId.$account"

    companion object {
        const val EXISTING = ""
        private const val NO_ACCOUNT = ""
        private const val EXISTING_CLAIMED = "existing"
        private const val NEXT = "next"
        private const val DIRECTORY_PREFIX = "directory."
    }
}
