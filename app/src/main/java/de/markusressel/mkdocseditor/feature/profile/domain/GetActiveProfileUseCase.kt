package de.markusressel.mkdocseditor.feature.profile.domain

import de.markusressel.mkdocseditor.feature.profile.domain.model.ProfileData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class GetActiveProfileUseCase @Inject constructor(
    private val getProfilesUseCase: GetProfilesUseCase
) {
    suspend operator fun invoke(): ProfileData? {
        return getProfilesUseCase().firstOrNull { it.active }
    }
}