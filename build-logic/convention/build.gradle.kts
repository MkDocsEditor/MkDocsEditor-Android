import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

group = "de.markusressel.mkdocseditor.buildlogic"
version = "0.1.0"

java {
    // Up to Java 11 APIs are available through desugaring
    // https://developer.android.com/studio/write/java11-minimal-support-table
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplicationCompose") {
            id = "mkdocseditor.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidApplication") {
            id = "mkdocseditor.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidHilt") {
            id = "mkdocseditor.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "mkdocseditor.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "mkdocseditor.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryPublishing") {
            id = "mkdocseditor.android.library.publishing"
            implementationClass = "AndroidLibraryPublishingConventionPlugin"
        }
        register("androidLogging") {
            id = "mkdocseditor.android.logging"
            implementationClass = "AndroidLoggingConventionPlugin"
        }
        register("androidTest") {
            id = "mkdocseditor.android.test"
            implementationClass = "AndroidTestConventionPlugin"
        }
        register("androidFlavors") {
            id = "mkdocseditor.android.application.flavors"
            implementationClass = "AndroidApplicationFlavorsConventionPlugin"
        }
    }
}
