package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentContentPersistenceManager @Inject constructor() : PersistenceManagerBase<DocumentContentEntity>(DocumentContentEntity::class)