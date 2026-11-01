import com.android.build.api.dsl.LibraryExtension

plugins {
    id("mkdocseditor.android.library")
    id("mkdocseditor.android.hilt")
    id("com.google.devtools.ksp")
}

extensions.configure<LibraryExtension> {
    namespace = "de.markusressel.mkdocseditor.auth"

    // FakeAuthApi and friends, for the tests of this module and of the app
    testFixtures {
        enable = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)

    implementation(libs.kotlinx.coroutines.core)
    api(libs.okhttp)

    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}

// optional live check against a running backend (see LiveAuthTest)
tasks.withType<Test>().configureEach {
    listOf("mkdocs.backend.url", "mkdocs.backend.users", "mkdocs.backend.passkeyOrigin", "mkdocs.backend.admin").forEach { name ->
        providers.gradleProperty(name).orNull?.let { systemProperty(name, it) }
    }
}
