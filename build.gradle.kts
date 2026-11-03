buildscript {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.mikepenz.aboutlibraries) apply false
    alias(libs.plugins.io.objectbox) apply false
}

// KSP runs inside the Gradle daemon and keeps static caches shared by all of its runs, which it clears when a run
// ends. A release run that overlapped with the debug run of the same module generated nothing and still succeeded, so
// the release build then failed in R8 on missing Hilt classes. Only one KSP task may run at a time.
abstract class KspSerializer : BuildService<BuildServiceParameters.None>

val kspSerializer = gradle.sharedServices.registerIfAbsent("kspSerializer", KspSerializer::class) {
    maxParallelUsages.set(1)
}

subprojects {
    tasks.withType<com.google.devtools.ksp.gradle.KspAATask>().configureEach {
        usesService(kspSerializer)
    }
}
