plugins {
    id("mkdocseditor.android.library")
    id("mkdocseditor.android.hilt")
    id(libs.plugins.io.objectbox.get().pluginId)
    id("com.google.devtools.ksp")
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
    api(libs.markusressel.kutepreferences.core) {
        isChanging = true
    }
    api(libs.markusressel.kutepreferences.ui) {
        isChanging = true
    }
    implementation(libs.markusressel.typedpreferences)
    implementation(libs.gson)

    // ObjectBox
    api(libs.objectbox.android)
    api(libs.objectbox.kotlin)
    ksp(libs.objectbox.processor)
    compileOnly(libs.objectbox.gradle.plugin)

    // Store
//    api(libs.store4)
    api(libs.store5)
    api(libs.atomicfu)


//    testimplementation("junit:junit:4.13.2")
//    androidTestImplementation("androidx.test:runner:1.5.2")
//    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
