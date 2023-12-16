buildscript {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }

    dependencies {
        classpath("io.objectbox:objectbox-gradle-plugin:${libs.versions.objectboxVersion}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.mikepenz.aboutlibraries) apply false
//    alias(libs.plugins.objectbox) apply false
}
