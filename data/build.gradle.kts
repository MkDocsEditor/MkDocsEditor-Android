import com.android.build.api.dsl.LibraryExtension

plugins {
    id("mkdocseditor.android.library")
    id("mkdocseditor.android.hilt")
    id(libs.plugins.io.objectbox.get().pluginId)
    id("org.jetbrains.kotlin.kapt")
    id("com.google.devtools.ksp")
}

extensions.configure<LibraryExtension> {
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
    // KSP is NOT supported atm, see: https://github.com/objectbox/objectbox-java/issues/1075
    kapt(libs.objectbox.processor)
    compileOnly(libs.objectbox.gradle.plugin)

    // Store
//    api(libs.store4)
    api(libs.store5)
    api(libs.atomicfu)


//    testimplementation("junit:junit:4.13.2")
//    androidTestImplementation("androidx.test:runner:1.5.2")
//    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
