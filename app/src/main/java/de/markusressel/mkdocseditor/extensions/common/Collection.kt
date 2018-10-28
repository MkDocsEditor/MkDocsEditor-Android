package de.markusressel.mkdocseditor.extensions.common

/**
 * Filters the list by the given type
 */
inline fun <reified T : Any> Collection<*>.filterByExpectedType(): Collection<T> {
    return this
            .filter { it is T }
            .map { it as T }
}

/**
 * Filters the list by the given type
 */
inline fun <reified T : Any> List<*>.filterByExpectedType(): List<T> {
    return this
            .asSequence()
            .filter { it is T }
            .map { it as T }
            .toList()
}