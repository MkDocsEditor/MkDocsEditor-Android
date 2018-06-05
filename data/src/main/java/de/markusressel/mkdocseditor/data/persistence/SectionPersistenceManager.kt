package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SectionPersistenceManager @Inject constructor() : PersistenceManagerBase<SectionEntity>(SectionEntity::class)