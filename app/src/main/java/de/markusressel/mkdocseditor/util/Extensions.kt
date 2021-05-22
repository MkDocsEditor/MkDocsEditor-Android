package de.markusressel.mkdocseditor.util

import androidx.annotation.MainThread
import androidx.arch.core.util.Function
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.paging.PagedList

/**
 * Helper function to use switchMap with a PagedList
 */
@MainThread
fun <X, Y> switchMapPaged(
    source: LiveData<X>,
    switchMapFunction: Function<X, LiveData<PagedList<Y>>>
): MediatorLiveData<PagedList<Y>> {
    val result = MediatorLiveData<PagedList<Y>>()
    result.addSource(source, object : androidx.lifecycle.Observer<X> {
        var mSource: LiveData<PagedList<Y>>? = null

        override fun onChanged(x: X?) {
            val newLiveData = switchMapFunction.apply(x)
            if (mSource === newLiveData) {
                return
            }
            if (mSource != null) {
                result.removeSource(mSource!!)
            }
            mSource = newLiveData
            if (mSource != null) {
                result.addSource(mSource!!) { y -> result.setValue(y) }
            }
        }
    })
    return result
}