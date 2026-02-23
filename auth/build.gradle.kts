plugins {
    id("mkdocseditor.android.library")
    id("mkdocseditor.android.hilt")
}

android {
    namespace = "de.markusressel.mkdocseditor.auth"
}

dependencies {
    implementation(libs.appauth)
}