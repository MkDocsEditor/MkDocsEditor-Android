package de.markusressel.mkdocseditor.extensions.common.android

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.core.text.toSpanned
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aghajari.compose.text.asAnnotatedString
import de.markusressel.mkdocseditor.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


/**
 * Returns true if the current device is considered a tablet
 */
fun Context.isTablet(): Boolean {
    return resources.getBoolean(R.bool.is_tablet)
}

fun androidx.fragment.app.Fragment.context(): Context {
    return this.context as Context
}

fun ViewModel.launch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
) = viewModelScope.launch(context, start, block)

/**
 * Load a string resource.
 *
 * @param id the resource identifier
 * @return the string data associated with the resource
 */
@Composable
@ReadOnlyComposable
fun textResource(@StringRes id: Int): AnnotatedString {
    val resources = LocalContext.current.resources
    return resources.getText(id).toSpanned().asAnnotatedString().annotatedString
}
