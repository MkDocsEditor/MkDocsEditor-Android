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
import com.github.kittinunf.result.Result

/**
 * Created by Markus on 03.06.2018.
 */
interface DocumentApi {

    /**
     * Get a Document description
     */
    suspend fun getDocument(id: String): Result<DocumentModel, FuelError>

    /**
     * Get the actual content data of a Document
     */
    suspend fun getDocumentContent(id: String): Result<String, FuelError>

    /**
     * Create a new document without any content
     */
    suspend fun createDocument(parentId: String, name: String): Result<DocumentModel, FuelError>

    /**
     * Rename a document
     *
     * @param id document id
     * @param name new document name
     */
    suspend fun renameDocument(id: String, name: String): Result<DocumentModel, FuelError>

    /**
     * Delete an existing document
     */
    suspend fun deleteDocument(id: String): Result<String, FuelError>

}