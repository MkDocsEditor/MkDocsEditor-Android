import de.markusressel.mkdocseditor.kotlinOptions

plugins {
    id("mkdocseditor.android.application")
    id("mkdocseditor.android.application.compose")
    id("mkdocseditor.android.compose.voyager")
    id("mkdocseditor.android.application.flavors")
    id("mkdocseditor.android.hilt")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    id("com.mikepenz.aboutlibraries.plugin")
}

android {
    namespace = "de.markusressel.mkdocseditor"

    defaultConfig {
        applicationId = "de.markusressel.mkdocseditor"

        versionCode = 1
        versionName = "0.1.0"

        setProperty("archivesBaseName", "MkDocsEditor_v${versionName}_(${versionCode})")
    }

    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi"
        )
    }
}


dependencies {
    implementation(project(":data"))

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.kotlin.stdlib.jdk8)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.browser)

    // Jetpack Compose
    implementation(libs.android.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material3.window.size.clazz)

//    implementation("androidx.compose.ui:ui-desktop:1.6.0-alpha07")
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.ui.viewbinding)

    implementation(libs.androidx.window)
    //    androidTestImplementation("androidx.window:window-testing:$windowmanager_version")


    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.compose)

    // Emoji
    implementation(libs.androidx.emoji)
    implementation(libs.androidx.emoji.bundled)

    compileOnly(libs.javax.annotation)

    // Hilt WorkManager integration
    implementation(libs.hilt.ext.work)
    // Hilt Compose integration
    implementation(libs.androidx.hilt.navigation.compose)

    // SingleLiveEvent similar implementation
    implementation(libs.liveevent)

    // MikePenz stuff
    //the core iconcis library (without any widgets)
    implementation(libs.mikepenz.iconics.core)
    //this adds all ui view widgets (IconicsButton, IconicsImageView, ...)
    implementation(libs.mikepenz.iconics.views)
    implementation(libs.mikepenz.iconics.compose)
    implementation(libs.mikepenz.iconics.typeface.api)

    // fonts
    implementation(libs.mikepenz.iconics.typeface.community.material)
    implementation(libs.mikepenz.iconics.typeface.google.material)
    implementation(libs.mikepenz.iconics.typeface.material)

    // Android KTX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.collection.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.common.java8)

    implementation(libs.androidx.paging.runtime.ktx)
    // optional - Jetpack Compose integration
    implementation(libs.androidx.paging.compose)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    // optional - Test helpers
    androidTestImplementation(libs.androidx.work.testing)

    // RxBus (event bus)
    implementation(libs.rxkotlin)
    implementation(libs.rxbus)

    // Permissions
    implementation(libs.dexter)

    // Dialogs
    implementation(libs.vanpra.compose.material.dialogs)
    implementation(libs.vanpra.compose.material.dialogs.datetime)

    // Code editor
    // not sure why but we need to explicitly specify this dependency even though it is
    // marked as "api" within the KodeEditor library
    implementation(libs.zoomlayout)

    implementation(libs.markusressel.kodeeditor) {
        isChanging = true
    }

    // Syntax Highlighting
    implementation(libs.markusressel.kodehighlighter.core) {
        isChanging = true
    }
    implementation(libs.markusressel.kodehighlighter.markdown) {
        isChanging = true
    }

    // Tutorial messages
    //implementation("com.github.markusressel:TutorialTooltip:v2.0.0")

    // AboutLibraries ("About" screen)
    implementation(libs.mikepenz.aboutlibraries.core)
    implementation(libs.mikepenz.aboutlibraries.compose)

    implementation(libs.mikepenz.aboutlibraries)

    // Spanned Text to AnnotatedString
    implementation(libs.aghajari.annotatedtext)

    // WebView
    implementation(libs.androidx.webkit)

//    testimplementation("junit:junit:4.13.2")
//    androidTestImplementation("androidx.test:runner:1.5.2")
//    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Hilt instrumentation tests
//    androidTestImplementation("com.google.dagger:hilt-android-testing:$hiltVersion")
//    kspAndroidTest "com.google.dagger:hilt-compiler:$hiltVersion")
    // Hilt local unit tests
//    testimplementation("com.google.dagger:hilt-android-testing:$hiltVersion")
//    kaptTest "com.google.dagger:hilt-compiler:$hiltVersion")
}
