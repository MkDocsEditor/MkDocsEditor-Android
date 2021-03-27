package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentPersistenceManager @Inject constructor()
    : PersistenceManagerBase<DocumentEntity>(DocumentEntity::class)