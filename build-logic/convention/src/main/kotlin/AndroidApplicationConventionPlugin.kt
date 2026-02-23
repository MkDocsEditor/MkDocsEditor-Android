import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import de.markusressel.mkdocseditor.TARGET_SDK
import de.markusressel.mkdocseditor.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.io.File

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("mkdocseditor.android.logging")
            }

            group = "de.markusressel.mkdocseditor"

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)

                defaultConfig {
                    targetSdk = TARGET_SDK
                }

                buildTypes {
                    release {
                        isMinifyEnabled = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro"
                        )
                    }
                    debug {
                        applicationIdSuffix = ".debug"
                        isMinifyEnabled = false
                        isDebuggable = true
                        signingConfig = signingConfigs.getByName("debug").apply {
                            storeFile = File("${projectDir.parent}/.ci/debug.keystore")
                        }
                    }
                }

                buildFeatures {
                    buildConfig = true
                }

                packaging {
                    resources {
                        excludes.addAll(
                            listOf(
                                "LICENSE.txt",
                                "META-INF/DEPENDENCIES",
                                "META-INF/ASL2.0",
                                "META-INF/NOTICE",
                                "META-INF/LICENSE"
                            )
                        )
                        pickFirsts.addAll(
                            listOf(
                                "META-INF/library-core_release.kotlin_module",
                                "META-INF/core_release.kotlin_module",
                                "META-INF/library_release.kotlin_module"
                            )
                        )
                    }
                }
            }
            extensions.configure<ApplicationAndroidComponentsExtension> {
            }
        }
    }

}