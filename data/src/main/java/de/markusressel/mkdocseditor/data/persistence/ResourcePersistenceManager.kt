package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResourcePersistenceManager @Inject constructor() : PersistenceManagerBase<ResourceEntity>(ResourceEntity::class)