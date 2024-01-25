package de.markusressel.mkdocseditor.feature.profile.domain

import de.markusressel.mkdocseditor.feature.profile.data.ProfileRepository
import de.markusressel.mkdocseditor.feature.profile.domain.model.ProfileData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class GetProfilesUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(): List<ProfileData> {
        return profileRepository.getProfiles()
    }
}