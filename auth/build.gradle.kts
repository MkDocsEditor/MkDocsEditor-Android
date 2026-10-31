import com.android.build.api.dsl.LibraryExtension

plugins {
    id("mkdocseditor.android.library")
    id("mkdocseditor.android.hilt")
    id("com.google.devtools.ksp")
}

extensions.configure<LibraryExtension> {
    namespace = "de.markusressel.mkdocseditor.auth"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)

    implementation(libs.kotlinx.coroutines.core)
    api(libs.okhttp)

    testImplementation(libs.junit4)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}

// optional live check against a running backend (see LiveAuthTest)
tasks.withType<Test>().configureEach {
    listOf("mkdocs.backend.url", "mkdocs.backend.users").forEach { name ->
        providers.gradleProperty(name).orNull?.let { systemProperty(name, it) }
    }
}
