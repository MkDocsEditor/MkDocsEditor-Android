import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

group = "de.markusressel.mkdocseditor.buildlogic"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

java {
    // Up to Java 11 APIs are available through desugaring
    // https://developer.android.com/studio/write/java11-minimal-support-table
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.objectbox.gradle.plugin)
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
        register("androidComposeVoyager") {
            id = "mkdocseditor.android.compose.voyager"
            implementationClass = "AndroidComposeVoyagerConventionPlugin"
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
//        register("objectbox") {
//            id = "io.objectbox"
//            implementationClass = "AndroidApplicationFlavorsConventionPlugin"
//        }
    }
}
