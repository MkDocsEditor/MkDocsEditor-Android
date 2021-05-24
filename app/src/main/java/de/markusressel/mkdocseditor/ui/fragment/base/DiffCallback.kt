/*
 * Copyright (C) 2018 Markus Ressel
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.markusressel.mkdocseditor.ui.fragment.base

import androidx.recyclerview.widget.DiffUtil
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem

/**
 * Generic callback used to compare list items
 */
class DiffCallback<T : IdentifiableListItem>(
    private val oldListItems: List<T>,
    private val newListItems: List<T>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int {
        return oldListItems
            .size
    }

    override fun getNewListSize(): Int {
        return newListItems
            .size
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldListItems[oldItemPosition]
        val newItem = newListItems[newItemPosition]

        return oldItem.javaClass == newItem.javaClass && oldItem.getItemId() == newItem.getItemId()
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldListItems[oldItemPosition]
        val newItem = newListItems[newItemPosition]

        return oldItem == newItem
    }

    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
        // Implement method if you're going to use ItemAnimator
        return super
            .getChangePayload(oldItemPosition, newItemPosition)
    }

}
