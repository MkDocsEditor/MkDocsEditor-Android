pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}


dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // jcenter() // Warning: this repository is going to shut down soon
        maven(url = "https://jitpack.io")
    }

    pluginManagement {
        resolutionStrategy {
            eachPlugin {
                // workaround for non-standard group on objectbox plugin
                if (requested.id.id.startsWith("io.objectbox")) {
                    useModule("io.objectbox:objectbox-gradle-plugin:${requested.version}")
                }
            }
        }
    }
}


rootProject.name = "MkDocsEditor"
include(":app")
include(":rest")
include(":data")
include(":auth")
