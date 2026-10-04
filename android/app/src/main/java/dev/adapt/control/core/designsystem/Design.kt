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
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.shadow

val Purple=Color(0xff7956d6)
@Composable fun AdaptTheme(theme: String="System",content: @Composable () -> Unit) {
    val dark=theme=="Dark" || (theme=="System" && isSystemInDarkTheme())
    val view=LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window,view).apply {
                isAppearanceLightStatusBars=!dark
                isAppearanceLightNavigationBars=!dark
            }
        }
    }
    val scheme=if(dark) darkColorScheme(primary=Color(0xff0a84ff),background=Color(0xff000000),surface=Color(0xff1c1c1e),
        surfaceVariant=Color(0xff2c2c2e),onSurface=Color(0xfff5f5f7),onSurfaceVariant=Color(0xff98989f))
    else lightColorScheme(primary=Color(0xff007aff),background=Color(0xfff2f2f7),surface=Color.White,surfaceVariant=Color(0xffe3e3e8),
        onSurface=Color(0xff1c1c1e),onSurfaceVariant=Color(0xff73737a))
    MaterialTheme(colorScheme=scheme,typography=Typography(
        headlineLarge=MaterialTheme.typography.headlineLarge.copy(fontWeight=FontWeight.SemiBold,letterSpacing=(-1).sp),
        titleLarge=MaterialTheme.typography.titleLarge.copy(fontWeight=FontWeight.SemiBold),
        titleMedium=MaterialTheme.typography.titleMedium.copy(fontSize=17.sp,fontWeight=FontWeight.Medium),
        bodyLarge=MaterialTheme.typography.bodyLarge.copy(fontSize=17.sp,lineHeight=23.sp),
        bodyMedium=MaterialTheme.typography.bodyMedium.copy(fontSize=15.sp),
        labelMedium=MaterialTheme.typography.labelMedium.copy(fontSize=12.sp)),content=content)
}
@Composable fun Group(modifier: Modifier=Modifier,content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
@Composable fun SectionLabel(label: String) {
    Text(label.uppercase(),style=MaterialTheme.typography.labelMedium,letterSpacing=.35.sp,
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
@Composable fun AdaptSwitch(checked: Boolean,onCheckedChange: (Boolean) -> Unit) {
    Box(Modifier.width(51.dp).height(48.dp).toggleable(checked,role=Role.Switch,onValueChange=onCheckedChange),contentAlignment=Alignment.Center) {
        Box(Modifier.width(51.dp).height(31.dp).background(if(checked) Color(0xff34c759) else MaterialTheme.colorScheme.surfaceVariant,CircleShape)) {
            Box(Modifier.align(if(checked) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal=2.dp)
                .size(27.dp).shadow(2.dp,CircleShape).background(Color.White,CircleShape))
        }
    }
}
