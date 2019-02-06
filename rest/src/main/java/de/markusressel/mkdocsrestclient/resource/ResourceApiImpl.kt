/*
 * Copyright (c) 2018 Markus Ressel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons
 * to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package de.markusressel.mkdocsrestclient.resource

import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.fuel.core.Response
import de.markusressel.mkdocsrestclient.RequestManager
import io.reactivex.Single

/**
 * Created by Markus on 03.06.2018.
 */
class ResourceApiImpl(private val requestManager: RequestManager) : ResourceApi {

    override fun getResource(id: String): Single<ResourceModel> {
        return requestManager
                .doRequest("/resource/$id/", Method.GET, ResourceModel.SingleDeserializer())
    }

    override fun getResourceContent(id: String): Single<ByteArray> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun uploadResource(parentId: String): Single<ResourceModel> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun deleteResource(id: String): Single<Pair<Response, ByteArray>> {
        return requestManager.doRequest("/document/$id/", Method.DELETE)
    }

}