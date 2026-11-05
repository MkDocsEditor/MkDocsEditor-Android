package de.markusressel.mkdocseditor.feature.about.data

import android.content.Context
import de.markusressel.mkdocseditor.BuildConfig
import de.markusressel.mkdocseditor.R

/**
 * The version of this build, as the user tells it in a bug report. The version code is the number of the CI run, which
 * also names its releases (`build-<n>`, `release-<n>`); the build type tells those two apart.
 *
 * Read from the package, because the version code is only set in the merged manifest, not in [BuildConfig].
 */
internal fun Context.appVersionText(): String {
    val info = packageManager.getPackageInfo(packageName, 0)
    return getString(R.string.about_version, info.versionName.orEmpty(), info.longVersionCode, BuildConfig.BUILD_TYPE)
}
