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

package de.markusressel.mkdocseditor.view.fragment.base

import android.os.Bundle
import androidx.fragment.app.Fragment
import de.markusressel.mkdocseditor.view.InstanceStateProvider

/**
 * Created by Markus on 21.02.2018.
 */
abstract class StateFragmentBase : Fragment() {

    private val stateBundle = Bundle()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            stateBundle
                    .putAll(savedInstanceState.getBundle(KEY_BUNDLE))
        }

        super
                .onActivityCreated(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState
                .putBundle(KEY_BUNDLE, stateBundle)

        super
                .onSaveInstanceState(outState)
    }

    /**
     * Bind a nullable property
     */
    protected fun <T> savedInstanceState() = InstanceStateProvider.Nullable<T>(stateBundle)

    /**
     * Bind a non-null property
     */
    protected fun <T> savedInstanceState(defaultValue: T) = InstanceStateProvider.NotNull(stateBundle, defaultValue)

    companion object {
        const val KEY_BUNDLE = "saved_state"
    }

}