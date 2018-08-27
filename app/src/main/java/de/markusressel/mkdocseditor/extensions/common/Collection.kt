package de.markusressel.mkdocseditor.extensions.common

/**
 * Filters the list by the given type
 */
inline fun <reified T : Any> Collection<*>.filterByExpectedType(): Collection<T> {
    return this
            .filter { it is T }
            .map { it as T }
}