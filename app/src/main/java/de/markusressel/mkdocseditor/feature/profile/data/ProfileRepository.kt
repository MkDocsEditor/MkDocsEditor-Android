package de.markusressel.mkdocseditor.feature.profile.data

import de.markusressel.mkdocseditor.feature.profile.domain.model.ProfileData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ProfileRepository @Inject constructor() {
    fun getProfiles(): List<ProfileData> {
        return listOf(
            ProfileData(
                name = "Profile 1",
                active = true,
            ),
            ProfileData(
                name = "Profile 2",
                active = false
            ),
            ProfileData(
                name = "Profile 3",
                active = false
            ),
        )
    }
}