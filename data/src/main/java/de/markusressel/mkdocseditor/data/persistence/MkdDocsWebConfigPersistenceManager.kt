package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.FixedDatabase
import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.MkDocsWebConfigEntity
import io.objectbox.BoxStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MkdDocsWebConfigPersistenceManager @Inject constructor(
    boxStore: BoxStore,
) : PersistenceManagerBase<MkDocsWebConfigEntity>(FixedDatabase(boxStore), MkDocsWebConfigEntity::class)
