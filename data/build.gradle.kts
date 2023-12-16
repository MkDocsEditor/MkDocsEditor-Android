plugins {
    id("mkdocseditor.android.library")
    id("mkdocseditor.android.hilt")
    id("mkdocseditor.android.library.publishing")
    id("com.google.devtools.ksp")
    id(libs.plugins.io.objectbox.get().pluginId)
}

android {
    namespace = "de.markusressel.mkdocseditor.data"
}

dependencies {
    api(project(":rest"))

    // Android(Architecture Components
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)

    // Preferences
    api(libs.markusressel.kutepreferences.core)
    api(libs.markusressel.kutepreferences.ui)
    implementation(libs.markusressel.typedpreferences)
    implementation(libs.gson)

    // ObjectBox
    api(libs.objectbox.android)
    api(libs.objectbox.kotlin)
    ksp(libs.objectbox.processor)
    compileOnly(libs.objectbox.gradle.plugin)

    // Store
    api(libs.store4)

//    testimplementation("junit:junit:4.13.2")
//    androidTestImplementation("androidx.test:runner:1.5.2")
//    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
