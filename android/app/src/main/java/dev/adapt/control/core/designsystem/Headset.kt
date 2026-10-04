package dev.adapt.control.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*

/** Original vector artwork based on the supplied physical reference. No vendor logos or photos. */
@Composable fun HeadsetHero(modifier: Modifier=Modifier,highlight: String="") {
    Canvas(modifier.fillMaxWidth().height(256.dp).semantics { contentDescription="Original black ADAPT 660 illustration" }) {
        val scale=minOf(size.width/360f,size.height/300f)
        translate((size.width-360*scale)/2,(size.height-300*scale)/2) { scale(scale,scale,Offset.Zero) {
            drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha=.12f),Color.Transparent),Offset(188f,280f),150f),Offset(68f,258f),Size(240f,32f))
            val band=Path().apply { moveTo(89f,193f); cubicTo(63f,17f,204f,-37f,256f,82f); cubicTo(272f,119f,268f,157f,263f,198f) }
            drawPath(band,Color(0xff111214),style=Stroke(29f,cap=StrokeCap.Round))
            drawPath(band,Brush.linearGradient(listOf(Color(0xff61636a),Color(0xff1b1c20),Color(0xff37383e)),Offset(60f,25f),Offset(275f,200f)),style=Stroke(22f,cap=StrokeCap.Round))
            val inner=Path().apply { moveTo(100f,185f); cubicTo(78f,53f,184f,-12f,238f,91f) }
            drawPath(inner,Color(0xff0c0d10),style=Stroke(10f,cap=StrokeCap.Round))
            rotate(17f,Offset(95f,213f)) {
                drawRoundRect(Color(0xff121318),Offset(49f,152f),Size(79f,124f),CornerRadius(36f))
                drawOval(Brush.linearGradient(listOf(Color(0xff48494e),Color(0xff17181b))),Offset(57f,156f),Size(68f,116f))
                drawOval(Color(0xff0d0e11),Offset(60f,166f),Size(48f,95f))
                drawOval(Color(0xff25262a),Offset(62f,171f),Size(40f,83f))
            }
            val arm=Path().apply { moveTo(259f,144f); cubicTo(284f,165f,294f,181f,287f,219f) }
            drawPath(arm,Color(0xff111217),style=Stroke(12f,cap=StrokeCap.Round))
            drawPath(arm,Color(0xff47484e),style=Stroke(5f,cap=StrokeCap.Round))
            rotate(24f,Offset(226f,219f)) {
                drawRoundRect(Color(0xff0c0d10),Offset(166f,151f),Size(117f,143f),CornerRadius(53f))
                drawRoundRect(Brush.linearGradient(listOf(Color(0xff57595f),Color(0xff222328),Color(0xff34353b))),Offset(178f,149f),Size(107f,143f),CornerRadius(50f))
                drawRoundRect(Color(0xff101115),Offset(188f,161f),Size(86f,119f),CornerRadius(41f))
                drawRoundRect(Brush.linearGradient(listOf(Color(0xff393a40),Color(0xff24252a))),Offset(192f,165f),Size(78f,111f),CornerRadius(38f))
                if(highlight=="Touch surface") drawRoundRect(Purple.copy(alpha=.24f),Offset(194f,167f),Size(74f,107f),CornerRadius(36f))
                drawRoundRect(if(highlight=="Purple button") Purple else Color(0xff8260ca),Offset(271f,226f),Size(7f,18f),CornerRadius(3f))
                drawRoundRect(if(highlight=="ANC switch") Purple else Color(0xff08090b),Offset(270f,204f),Size(6f,13f),CornerRadius(2f))
                drawCircle(if(highlight=="Audio jack") Purple else Color(0xff050507),3f,Offset(231f,284f))
                drawRoundRect(if(highlight=="USB") Purple else Color(0xff07080b),Offset(245f,276f),Size(14f,4f),CornerRadius(2f))
                drawCircle(if(highlight=="Bluetooth control") Purple else Color(0xff16171c),3f,Offset(261f,262f))
            }
        } }
    }
}
