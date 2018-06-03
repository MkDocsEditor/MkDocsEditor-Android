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

package de.markusressel.mkdocsrestclient.document

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.result.Result
import com.github.salomonbrys.kotson.jsonObject
import de.markusressel.mkdocsrestclient.RequestManager
import io.reactivex.Single

/**
 * Created by Markus on 03.06.2018.
 */
class DocumentApiImpl(private val requestManager: RequestManager) : DocumentApi {

    override fun getDocument(id: String): Single<DocumentModel> {
        return requestManager
                .doRequest("/document/$id/", Method.GET, DocumentModel.SingleDeserializer())
    }

    override fun getDocumentContent(id: String): Single<String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createDocument(parentId: String, name: String): Single<DocumentModel> {
        val data = jsonObject(
                "parent" to parentId,
                "name" to name
        )
        return requestManager
                .doJsonRequest("/document/", Method.POST, data,
                        DocumentModel.SingleDeserializer())
    }

    override fun updateDocumentContent(id: String, newContent: String): Single<String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun deleteDocument(id: String): Single<Pair<Response, Result<ByteArray, FuelError>>> {
        return requestManager
                .doRequest("/document/$id/", Method.DELETE)
    }

}