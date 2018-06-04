/*
 * DataMunch by Markus Ressel
 * Copyright (c) 2018.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.markusressel.mkdocseditor.view

import android.os.Bundle
import android.os.Parcelable
import java.io.Serializable
import kotlin.reflect.KProperty

/**
 * Created by Markus on 21.02.2018.
 */
open class InstanceStateProvider<T>(private val savable: Bundle) {

    private var cache: T? = null

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        cache = value
        if (value == null) {
            savable
                    .remove(property.name)
            return
        }
        when (value) {
            is Int -> savable.putInt(property.name, value)
            is Long -> savable.putLong(property.name, value)
            is Float -> savable.putFloat(property.name, value)
            is String -> savable.putString(property.name, value)
            is Bundle -> savable.putBundle(property.name, value)
            is Serializable -> savable.putSerializable(property.name, value)
            is Parcelable -> savable.putParcelable(property.name, value)
        }
    }

    protected fun getAndCache(key: String): T? = cache
            ?: (savable.get(key) as T?).apply { cache = this }

    class Nullable<T>(savable: Bundle) : InstanceStateProvider<T>(savable) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): T? = getAndCache(property.name)
    }

    class NotNull<T>(savable: Bundle, private val defaultValue: T) : InstanceStateProvider<T>(savable) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): T = getAndCache(property.name)
                ?: defaultValue
    }
}