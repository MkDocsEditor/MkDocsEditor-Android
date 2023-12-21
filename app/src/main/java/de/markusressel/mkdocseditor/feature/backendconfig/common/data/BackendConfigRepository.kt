package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import de.markusressel.mkdocseditor.data.persistence.BackendConfigPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity
import de.markusressel.mkdocseditor.network.OfflineModeManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BackendConfigRepository @Inject constructor(
    private val offlineModeManager: OfflineModeManager,
    private val backendConfigPersistenceManager: BackendConfigPersistenceManager,
) {

    fun getAll(): MutableList<BackendConfigEntity> =
        backendConfigPersistenceManager.standardOperation().all

}