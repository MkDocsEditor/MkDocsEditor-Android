package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.UserPasswordAuthConfigEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendAuthConfigPersistenceManager @Inject constructor(
) : PersistenceManagerBase<UserPasswordAuthConfigEntity>(UserPasswordAuthConfigEntity::class)