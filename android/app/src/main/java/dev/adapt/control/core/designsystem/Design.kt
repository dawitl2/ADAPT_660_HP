package dev.adapt.control.core.designsystem

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Purple=Color(0xff7956d6)
@Composable fun AdaptTheme(theme: String="System",content: @Composable () -> Unit) {
    val dark=theme=="Dark" || (theme=="System" && isSystemInDarkTheme())
    val scheme=if(dark) darkColorScheme(primary=Color(0xffb39aee),background=Color(0xff111214),surface=Color(0xff202125),
        surfaceVariant=Color(0xff2c2d32),onSurface=Color(0xfff3f2f6),onSurfaceVariant=Color(0xffa4a3ae))
    else lightColorScheme(primary=Purple,background=Color(0xfff5f5f7),surface=Color.White,surfaceVariant=Color(0xffeeeef2),
        onSurface=Color(0xff202126),onSurfaceVariant=Color(0xff85858f))
    MaterialTheme(colorScheme=scheme,typography=Typography(
        headlineLarge=MaterialTheme.typography.headlineLarge.copy(fontWeight=FontWeight.SemiBold,letterSpacing=(-1).sp),
        titleLarge=MaterialTheme.typography.titleLarge.copy(fontWeight=FontWeight.SemiBold),
        bodyLarge=MaterialTheme.typography.bodyLarge.copy(lineHeight=25.sp)),content=content)
}
@Composable fun Group(modifier: Modifier=Modifier,content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
    }
}
@Composable fun SectionLabel(label: String) {
    Text(label.uppercase(),style=MaterialTheme.typography.labelMedium,letterSpacing=1.5.sp,
        color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(start=4.dp,top=8.dp))
}
@Composable fun DetailRow(title: String,value: String,subtitle: String?=null,click: (() -> Unit)?=null) {
    Row(Modifier.fillMaxWidth().then(if(click!=null) Modifier.clickable(onClick=click) else Modifier).heightIn(min=48.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title,style=MaterialTheme.typography.bodyLarge)
            if(subtitle!=null) Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(click!=null) Text("›",fontSize=24.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun Pill(text: String,accent: Boolean=false) {
    Surface(shape=CircleShape,color=if(accent) MaterialTheme.colorScheme.primary.copy(alpha=.1f) else MaterialTheme.colorScheme.surfaceVariant) {
        Text(text,modifier=Modifier.padding(horizontal=12.dp,vertical=7.dp),style=MaterialTheme.typography.labelMedium,
            color=if(accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
