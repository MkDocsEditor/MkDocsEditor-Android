package de.markusressel.mkdocseditor.extensions.common

import java.util.*

/**
 * Returns a random number within the range including first and last value
 */
fun ClosedRange<Int>.random() = Random().nextInt(endInclusive - start) + start