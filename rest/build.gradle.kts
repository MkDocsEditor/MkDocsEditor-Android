import com.android.build.api.dsl.LibraryExtension

plugins {
    id("mkdocseditor.android.library")
    id("mkdocseditor.android.hilt")
    id("com.google.devtools.ksp")
}

extensions.getByType<LibraryExtension>().apply {
    namespace = "de.markusressel.mkdocsrestclient"
}

dependencies {
    // moshi
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)
    implementation(libs.moshi.adapters)

    //for JVM
    api(libs.fuel)
    //for Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.core)
    api(libs.fuel.coroutines)
    //for moshi support
    implementation(libs.fuel.moshi)
    //for Android
    implementation(libs.fuel.android)

    // okhttp (for websockets)
    implementation(libs.okhttp)

    api(libs.markusressel.commons.core)
    api(libs.markusressel.commons.logging)
    api(libs.markusressel.commons.android.core)
    api(libs.markusressel.commons.android.material)

    api(libs.automerge)

//    testimplementation("junit:junit:4.12")
//    androidTestImplementation("androidx.test:runner:1.1.1-alpha01")
//    androidTestImplementation("androidx.test.espresso:espresso-core:3.1.1-alpha01")
}
