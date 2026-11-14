package de.markusressel.mkdocsrestclient

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.result.Result
import de.markusressel.mkdocsrestclient.dashboard.DashboardApi
import de.markusressel.mkdocsrestclient.dashboard.DashboardApiImpl
import de.markusressel.mkdocsrestclient.document.DocumentApi
import de.markusressel.mkdocsrestclient.document.DocumentApiImpl
import de.markusressel.mkdocsrestclient.history.HistoryApi
import de.markusressel.mkdocsrestclient.history.HistoryApiImpl
import de.markusressel.mkdocsrestclient.mkdocs.MkDocsApi
import de.markusressel.mkdocsrestclient.mkdocs.MkDocsApiImpl
import de.markusressel.mkdocsrestclient.qr.QrCodeApi
import de.markusressel.mkdocsrestclient.qr.QrCodeApiImpl
import de.markusressel.mkdocsrestclient.reference.ReferenceApi
import de.markusressel.mkdocsrestclient.reference.ReferenceApiImpl
import de.markusressel.mkdocsrestclient.resource.ResourceApi
import de.markusressel.mkdocsrestclient.resource.ResourceApiImpl
import de.markusressel.mkdocsrestclient.section.SectionApi
import de.markusressel.mkdocsrestclient.section.SectionApiImpl
import de.markusressel.mkdocsrestclient.section.SectionModel

/**
 * Convenience delegation class for easy access to all api methods
 *
 * Created by Markus on 03.06.2018.
 */
class MkDocsRestClient constructor(
    private val requestManager: RequestManager = RequestManager(),
    mkDocsApi: MkDocsApi = MkDocsApiImpl(requestManager),
    sectionApi: SectionApi = SectionApiImpl(requestManager),
    documentApi: DocumentApi = DocumentApiImpl(requestManager),
    resourceApi: ResourceApi = ResourceApiImpl(requestManager),
    referenceApi: ReferenceApi = ReferenceApiImpl(requestManager),
    dashboardApi: DashboardApi = DashboardApiImpl(requestManager),
    qrCodeApi: QrCodeApi = QrCodeApiImpl(requestManager),
    historyApi: HistoryApi = HistoryApiImpl(requestManager),
) : IMkDocsRestClient,
    MkDocsApi by mkDocsApi,
    SectionApi by sectionApi,
    DocumentApi by documentApi,
    ResourceApi by resourceApi,
    ReferenceApi by referenceApi,
    DashboardApi by dashboardApi,
    QrCodeApi by qrCodeApi,
    HistoryApi by historyApi {

    override suspend fun isHostAlive(): Result<String, FuelError> {
        return requestManager.doStatusRequest("/alive/", Method.GET)
    }

    override suspend fun getItemTree(): Result<SectionModel, FuelError> {
        return getSectionTree()
    }

    override fun enableLogging() {
        requestManager.addLogger()
    }

    override fun disableLogging() {
        requestManager.removeLogger()
    }

}