package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.BackendServerConfigEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendServerConfigPersistenceManager @Inject constructor(
) : PersistenceManagerBase<BackendServerConfigEntity>(BackendServerConfigEntity::class)