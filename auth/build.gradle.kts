import com.android.build.api.dsl.LibraryExtension

plugins {
    id("mkdocseditor.android.library")
    id("mkdocseditor.android.hilt")
}

extensions.configure<LibraryExtension> {
    namespace = "de.markusressel.mkdocseditor.auth"
}

dependencies {
    implementation(libs.appauth)
}