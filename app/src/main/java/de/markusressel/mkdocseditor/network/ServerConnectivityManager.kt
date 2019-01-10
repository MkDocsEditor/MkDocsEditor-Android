package de.markusressel.mkdocseditor.network

import de.markusressel.mkdocsrestclient.MkDocsRestClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerConnectivityManager @Inject constructor(
        val restClient: MkDocsRestClient) {

}