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
                            val selected=destination==route
                            Column(Modifier.clip(RoundedCornerShape(18.dp)).clickable { nav.navigate(route) { launchSingleTop=true; popUpTo("home") { saveState=true }; restoreState=true } }
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(top=22.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
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
    val haptic=LocalHapticFeedback.current
    Page("ADAPT Control") {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(6.dp).background(if(device.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,CircleShape))
            Text(if(device.connected) "Connected to firmware simulator" else "Ready when you are",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
            HeadsetHero()
            Text("ADAPT 660",style=MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(if(device.connected) "${device.battery?.let { "$it% battery" } ?: "Battery unknown"}   ·   ${listOf("ANC unknown","ANC off","ANC on","Adaptive ANC").getOrElse(device.anc){"ANC unknown"}}" else "Connect to make it yours",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp)); Pill(if(device.connected) device.transport else "Disconnected",device.connected)
        }
        Group(Modifier.clickable { navigate("actions") }) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(46.dp).background(MaterialTheme.colorScheme.primary.copy(alpha=.12f),RoundedCornerShape(17.dp)),contentAlignment=Alignment.Center) {
                    Box(Modifier.width(11.dp).height(24.dp).background(MaterialTheme.colorScheme.primary,CircleShape))
                }
                Column(Modifier.weight(1f)) {
                    Text("PURPLE BUTTON",style=MaterialTheme.typography.labelSmall,letterSpacing=1.4.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(actionCatalog.firstOrNull { it.id==prefs.mappings[0] }?.title ?: "Custom action",style=MaterialTheme.typography.titleLarge)
                    Text("One press. A little more possibility.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("›",fontSize=28.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            listOf(Triple("AI Voice","voice",Icons.Outlined.GraphicEq),Triple("Voice Note","notes",Icons.Outlined.MicNone),Triple("Study","study",Icons.Outlined.AutoStories)).forEach { (title,route,icon) ->
                Surface(Modifier.weight(1f).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); navigate(route) },shape=RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(vertical=18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        Icon(icon,title,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(23.dp)); Text(title,style=MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if(!armed) Group {
            Text("Ready from your pocket",style=MaterialTheme.typography.titleMedium)
            Text("Enable hands-free mode while the app is open. A persistent notification lets you stop it at any time.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick=arm,shape=CircleShape) { Text("Enable hands-free") }
        }
        SectionLabel("Recent activity")
        Group {
            if(activity.isEmpty()) Text("Your next idea starts here.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            activity.take(3).forEach { entry -> DetailRow(entry.label,java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(entry.date))) }
        }
        if(device.connected) Text("Firmware ${device.firmware} · ACP 0.1",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.align(Alignment.CenterHorizontally))
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
