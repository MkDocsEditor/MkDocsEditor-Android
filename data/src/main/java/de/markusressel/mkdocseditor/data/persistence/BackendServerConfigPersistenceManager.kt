package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.FixedDatabase
import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.BackendServerConfigEntity
import io.objectbox.BoxStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendServerConfigPersistenceManager @Inject constructor(
    boxStore: BoxStore,
) : PersistenceManagerBase<BackendServerConfigEntity>(FixedDatabase(boxStore), BackendServerConfigEntity::class)
