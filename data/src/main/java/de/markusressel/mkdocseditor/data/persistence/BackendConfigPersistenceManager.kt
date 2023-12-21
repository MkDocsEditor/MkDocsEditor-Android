package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendConfigPersistenceManager @Inject constructor(
    private val backendServerConfigPersistenceManager: BackendServerConfigPersistenceManager
) : PersistenceManagerBase<BackendConfigEntity>(BackendConfigEntity::class)