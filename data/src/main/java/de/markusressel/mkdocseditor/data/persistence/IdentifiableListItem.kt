package de.markusressel.mkdocseditor.data.persistence

interface IdentifiableListItem {

    /**
     * This id (and the class of an item) is used to check if two
     * elements in two lists are essentially the same item (altough not the same object).
     * This is used in @see ListFragmentBase when the contents of the list are updated.
     */
    fun getItemId(): String

}