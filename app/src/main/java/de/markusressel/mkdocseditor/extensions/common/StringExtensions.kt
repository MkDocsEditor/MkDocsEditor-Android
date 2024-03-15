package de.markusressel.mkdocseditor.extensions.common

fun String.safeSubstring(startIndex: Int): String = substring(startIndex.coerceIn(0, length))

fun String.safeSubstring(startIndex: Int, endIndex: Int): String =
    substring(startIndex.coerceIn(0, length), endIndex.coerceIn(0, length))