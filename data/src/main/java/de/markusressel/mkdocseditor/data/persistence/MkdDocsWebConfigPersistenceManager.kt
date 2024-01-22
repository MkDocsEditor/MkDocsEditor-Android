package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.MkDocsWebConfigEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MkdDocsWebConfigPersistenceManager @Inject constructor(
) : PersistenceManagerBase<MkDocsWebConfigEntity>(MkDocsWebConfigEntity::class)