package dev.adapt.control.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import dev.adapt.control.R

@Composable fun HeadsetHero(modifier: Modifier=Modifier) {
    val background=MaterialTheme.colorScheme.background
    val light=background.luminance()>.5f
    Image(painterResource(R.drawable.adapt_headset_photo),"ADAPT 660 headset photo",
        modifier.fillMaxWidth().height(180.dp).drawWithContent { clipRect(top=10.dp.toPx()) { this@drawWithContent.drawContent() } },contentScale=ContentScale.Fit,
        colorFilter=if(light) ColorFilter.tint(background,BlendMode.Modulate) else null)
}
