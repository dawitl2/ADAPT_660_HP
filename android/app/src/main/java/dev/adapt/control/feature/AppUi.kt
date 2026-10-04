package dev.adapt.control.feature

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import dev.adapt.control.AppGraph
import dev.adapt.control.core.designsystem.*
import dev.adapt.control.feature.actions.*

@Composable fun AppUi(graph: AppGraph,arm: () -> Unit,associate: () -> Unit,systemAssistant: () -> Unit,flashlightPermission: () -> Unit) {
    val prefs by graph.preferences.collectAsStateWithLifecycle()
    val loaded by graph.ready.collectAsStateWithLifecycle()
    val notice by graph.message.collectAsStateWithLifecycle()
    val nav=rememberNavController()
    val back by nav.currentBackStackEntryAsState()
    val destination=back?.destination?.route ?: "home"
    val snackbar=remember { SnackbarHostState() }
    AdaptTheme(prefs.theme) {
        LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); if(graph.message.value==it) graph.message.value=null } }
        Scaffold(containerColor=MaterialTheme.colorScheme.background,snackbarHost={ SnackbarHost(snackbar) },bottomBar={
            if(prefs.onboarded) {
                Surface(color=MaterialTheme.colorScheme.background) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=24.dp,vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                        listOf("home" to Icons.Outlined.Home,"actions" to Icons.Outlined.TouchApp,"device" to Icons.Outlined.Headphones,"settings" to Icons.Outlined.Tune).forEach { (route,icon) ->
                            val selected=destination==route || (route=="home" && destination in listOf("voice","notes","study"))
                            Column(Modifier.clip(RoundedCornerShape(18.dp)).clickable { nav.navigate(route) { launchSingleTop=true; popUpTo("home") { saveState=true }; restoreState=route!="home" } }
                                .padding(horizontal=16.dp,vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                Icon(icon,route.replaceFirstChar { it.uppercase() },tint=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(23.dp))
                                Spacer(Modifier.height(5.dp)); Text(route.replaceFirstChar { it.uppercase() },style=MaterialTheme.typography.labelSmall,
                                    color=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }) { padding ->
            if(!loaded) Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
            else if(!prefs.onboarded) Onboarding(graph,arm,associate,Modifier.padding(padding))
            else NavHost(nav,"home",Modifier.padding(padding),enterTransition={ fadeIn() },exitTransition={ fadeOut() }) {
                composable("home") { HomeScreen(graph,{ nav.navigate(it) },arm) }
                composable("actions") { ActionsScreen(graph,flashlightPermission) }
                composable("device") { DeviceScreen(graph,associate) }
                composable("settings") { SettingsScreen(graph,arm,associate) }
                composable("voice") { VoiceScreen(graph,systemAssistant) }
                composable("notes") { NotesScreen(graph) }
                composable("study") { StudyScreen(graph) }
            }
        }
    }
}
@Composable fun Page(title: String,subtitle: String?=null,content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp).padding(top=18.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text(title,style=MaterialTheme.typography.headlineLarge)
        if(subtitle!=null) Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}
@Composable fun HomeScreen(graph: AppGraph,navigate: (String) -> Unit,arm: () -> Unit) {
    val device by graph.transport.state.collectAsStateWithLifecycle()
    val armed by graph.armed.collectAsStateWithLifecycle()
    val prefs by graph.preferences.collectAsStateWithLifecycle()
    val activity by graph.repository.activity.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth().height(52.dp),contentAlignment=Alignment.Center) {
            Text("ADAPT 660",style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.SemiBold))
            TextButton(onClick={navigate("settings")},modifier=Modifier.align(Alignment.CenterStart).offset(x=(-12).dp)) {
                Icon(Icons.Outlined.ChevronLeft,null,modifier=Modifier.size(24.dp)); Text("Settings",fontSize=17.sp)
            }
        }
        Column(Modifier.fillMaxWidth().padding(top=10.dp,bottom=18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            HeadsetHero()
            Icon(if(device.battery==null) Icons.Outlined.BatteryUnknown else Icons.Outlined.BatteryFull,
                "Headset battery",modifier=Modifier.size(26.dp),tint=if(device.battery==null) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xff34c759))
            Text(device.battery?.let { "$it%" } ?: "—",fontSize=22.sp,color=MaterialTheme.colorScheme.onSurface)
        }
        Group(Modifier.clickable { navigate("device") }) { DetailRow("Name","ADAPT 660",click={navigate("device")}) }
        SectionLabel("Noise control")
        Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.padding(3.dp),verticalAlignment=Alignment.CenterVertically) {
                listOf(Triple("Off",Icons.Outlined.VolumeOff,1),Triple("Noise Cancellation",Icons.Outlined.GraphicEq,2),Triple("Adaptive",Icons.Outlined.Hearing,3)).forEach { (name,icon,value) ->
                    val selected=device.anc==value
                    Surface(Modifier.weight(1f),shape=RoundedCornerShape(10.dp),color=if(selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation=if(selected) 2.dp else 0.dp) {
                        Column(Modifier.clickable(enabled=device.connected) { graph.run { graph.transport.debug("anc $value") } }.height(83.dp).padding(horizontal=5.dp),
                            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                            Icon(icon,name,modifier=Modifier.size(27.dp),tint=MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(8.dp))
                            Text(name,fontSize=11.sp,lineHeight=13.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
        SectionLabel("Purple button")
        Group {
            listOf("Single Press","Double Press","Long Press").forEachIndexed { index,label ->
                if(index>0) HorizontalDivider(color=MaterialTheme.colorScheme.surfaceVariant,thickness=.5.dp)
                DetailRow(label,when(prefs.mappings[index]) {1 -> "AI Voice";2 -> "Voice Note";3 -> "Study Companion";else -> actionCatalog.firstOrNull {it.id==prefs.mappings[index]}?.title ?: "Custom"},click={navigate("actions")})
            }
        }
        Group {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).background(Purple,RoundedCornerShape(7.dp)),contentAlignment=Alignment.Center) {
                    Icon(Icons.Outlined.Headphones,null,tint=Color.White,modifier=Modifier.size(20.dp))
                }
                Text("Hands-free",Modifier.weight(1f).padding(start=12.dp),style=MaterialTheme.typography.bodyLarge)
                AdaptSwitch(armed,{if(it) arm() else graph.disarm()})
            }
        }
        SectionLabel("Your everyday companions")
        Group {
            listOf(Triple("AI Voice","voice",Icons.Outlined.GraphicEq),Triple("Voice Note","notes",Icons.Outlined.MicNone),Triple("Study","study",Icons.Outlined.AutoStories)).forEachIndexed { index,(title,route,icon) ->
                if(index>0) HorizontalDivider(color=MaterialTheme.colorScheme.surfaceVariant,thickness=.5.dp)
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable { navigate(route) },verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(icon,title,tint=if(route=="voice") Purple else MaterialTheme.colorScheme.primary,modifier=Modifier.size(25.dp))
                    Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                    Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(20.dp))
                }
            }
        }
        Text(if(device.connected) "Connected" else "Not connected",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.align(Alignment.CenterHorizontally))
        if(activity.isNotEmpty()) {
            SectionLabel("Recent activity")
            Group { activity.take(3).forEach { DetailRow(it.label,java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it.date))) } }
        }
    }
}
@Composable fun ActionsScreen(graph: AppGraph,flashlightPermission: () -> Unit) {
    val prefs by graph.preferences.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<Int?>(null) }
    Page("Make it yours","A single button. Your everyday shortcuts.") {
        listOf("Single Press","Double Press","Long Press").forEachIndexed { index,title ->
            Group(Modifier.clickable { picker=index+1 }) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(listOf("●","● ●","━")[index],color=MaterialTheme.colorScheme.primary,fontSize=20.sp,modifier=Modifier.width(52.dp))
                    Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.titleMedium); Text(actionCatalog.firstOrNull { it.id==prefs.mappings[index] }?.title ?: "Custom",color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    Icon(Icons.Outlined.ChevronRight,"Change mapping",tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Group {
            DetailRow("Very Long Press","Locked", "Pairing / Recovery")
            Text("Recovery stays on the headset. This protected gesture cannot be remapped by the app.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionLabel("Phone action")
        Group {
            ChoiceRow("Action",prefs.phoneAction,listOf("Play / Pause","Next track","Open app","Open URL","Flashlight","Timer")) { graph.run { graph.settings.set("phoneAction",it) } }
            SmallField("App package",prefs.selectedApp) { graph.run { graph.settings.set("app",it) } }
            SmallField("URL",prefs.selectedUrl) { graph.run { graph.settings.set("url",it) } }
            TextButton(onClick={ if(prefs.phoneAction=="Flashlight") flashlightPermission() else graph.activate(4) }) { Text("Test phone action") }
        }
        SectionLabel("PC action")
        Group { ChoiceRow("Action",prefs.pcAction,listOf("play_pause","volume_up","volume_down","mute","open_app","lock")) { graph.run { graph.settings.set("pcAction",it) } }; TextButton(onClick={ graph.activate(5) }) { Text("Test PC action") } }
    }
    picker?.let { gesture ->
        AlertDialog(onDismissRequest={ picker=null },title={ Text("Choose an action") },text={
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                ActionTarget.entries.forEach { category ->
                    val options=actionCatalog.filter { it.target==category }
                    Text(category.name,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(options.isEmpty()) Text("Additional actions coming later",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    options.forEach { action -> TextButton(onClick={ graph.run { graph.map(gesture,action.id) }; picker=null }) { Text(action.title) } }
                }
            }
        },confirmButton={ TextButton(onClick={ picker=null }) { Text("Cancel") } })
    }
}
@Composable fun ChoiceRow(title: String,current: String,choices: List<String>,changed: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    DetailRow(title,current,click={ open=true })
    if(open) AlertDialog(onDismissRequest={ open=false },title={ Text(title) },text={
        Column { choices.forEach { value -> TextButton(onClick={ changed(value); open=false },modifier=Modifier.fillMaxWidth()) { Text(value) } } }
    },confirmButton={ TextButton(onClick={ open=false }) { Text("Done") } })
}
@Composable fun SmallField(label: String,value: String,secret: Boolean=false,onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(text,{ text=it },label={ Text(label) },singleLine=true,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth(),
        visualTransformation=if(secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        trailingIcon={ if(text!=value) IconButton(onClick={ onSave(text) }) { Icon(Icons.Outlined.Check,"Save $label") } })
}
