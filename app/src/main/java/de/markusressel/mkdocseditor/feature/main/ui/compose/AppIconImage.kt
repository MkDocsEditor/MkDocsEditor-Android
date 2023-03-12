package de.markusressel.mkdocseditor.feature.main.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R


@Composable
internal fun AppIconImage() {
    Column(
        modifier = Modifier
            .background(
                color = Color(0xFF222222),
                shape = RoundedCornerShape(8.dp),
            )
    ) {
        Image(
            modifier = Modifier
                .width(52.dp)
                .padding(8.dp),
            painter = painterResource(id = R.drawable.app_icon_no_padding),
            contentDescription = "",
        )
    }
}
