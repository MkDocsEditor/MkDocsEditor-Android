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

    testImplementation(libs.junit4)
}
