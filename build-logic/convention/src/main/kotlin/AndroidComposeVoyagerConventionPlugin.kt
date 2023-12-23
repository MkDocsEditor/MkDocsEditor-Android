import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidComposeVoyagerConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                // Navigator
                "implementation"(libs.findLibrary("voyager.navigator").get())

                // Screen Model
                "implementation"(libs.findLibrary("voyager.screenModel").get())

                // BottomSheetNavigator
                "implementation"(libs.findLibrary("voyager.bottomSheetNavigator").get())

                // TabNavigator
                "implementation"(libs.findLibrary("voyager.tabNavigator").get())

                // Transitions
                "implementation"(libs.findLibrary("voyager.transitions").get())

                // Android

                // Koin integration
//                "implementation"(libs.findLibrary("voyager.koin").get())

                // Hilt integration
                "implementation"(libs.findLibrary("voyager.hilt").get())

                // LiveData integration
//                "implementation"(libs.findLibrary("voyager.livedata").get())

                // Desktop + Android

                // Kodein integration
//                "implementation"(libs.findLibrary("voyager.kodein").get())

                // RxJava integration
//                "implementation"(libs.findLibrary("voyager.rxjava").get())
            }
        }
    }

}