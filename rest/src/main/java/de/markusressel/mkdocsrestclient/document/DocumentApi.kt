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

import com.github.kittinunf.fuel.core.Response
import io.reactivex.Single

/**
 * Created by Markus on 03.06.2018.
 */
interface DocumentApi {

    /**
     * Get a Document description
     */
    fun getDocument(id: String): Single<DocumentModel>

    /**
     * Get the actual content data of a Document
     */
    fun getDocumentContent(id: String): Single<String>

    /**
     * Create a new document without any content
     */
    fun createDocument(parentId: String, name: String): Single<DocumentModel>

    /**
     * Update the content of a document
     */
    fun updateDocumentContent(id: String, newContent: String): Single<String>

    /**
     * Delete an existing document
     */
    fun deleteDocument(id: String): Single<Pair<Response, ByteArray>>

}