package dev.adapt.control.feature

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.adapt.control.AppGraph
import dev.adapt.control.core.designsystem.*
import dev.adapt.control.core.protocol.Frame
import kotlinx.coroutines.delay

@Composable fun DeviceScreen(graph: AppGraph,associate: () -> Unit) {
    val device by graph.transport.state.collectAsStateWithLifecycle()
    val logs by graph.events.collectAsStateWithLifecycle()
    val audio by graph.route.label.collectAsStateWithLifecycle()
    var advanced by remember { mutableStateOf(false) }
    var region by remember { mutableStateOf("Purple button") }
    Page("Your headset","Every control, thoughtfully connected.") {
        HeadsetHero(highlight=region)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Purple button","Touch surface","ANC switch","Bluetooth control","USB","Audio jack").forEach { label ->
                FilterChip(region==label,{ region=label },{ Text(label) })
            }
        }
        Text(when(region) { "Purple button" -> "Short · double · long. Recovery is protected."; "Touch surface" -> "Vendor touch controls remain behind the firmware HAL."; else -> "${region}: physical pinout and custom firmware support are UNKNOWN." },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Group { DetailRow("Connection",if(device.connected) "Connected" else "Disconnected",device.transport); TextButton(onClick=associate) { Text("Associate ADAPT 660") } }
        Group { DetailRow("Battery",device.battery?.let { "$it%" } ?: "Unknown"); DetailRow("Audio",if(audio.startsWith("ADAPT")) "Headset" else "Inactive",audio) }
        Group {
            DetailRow("Noise control",listOf("Unknown","Off","On","Adaptive").getOrElse(device.anc){"Unknown"})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("Off","On","Adaptive").forEachIndexed { i,name -> FilterChip(device.anc==i+1,{ graph.run { graph.transport.debug("anc ${i+1}") } },{ Text(name) },enabled=device.connected) }
            }
            Text("Controls apply to the firmware simulator until a real transport is verified.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Group { DetailRow("Firmware",device.firmware); DetailRow("Protocol","ACP 0.1"); DetailRow("Physical target","UNKNOWN") }
        Group {
            DetailRow("Engineering Mode",if(advanced) "Open" else "Advanced",click={ advanced=!advanced })
            if(advanced) {
                Text("Safe simulator controls",style=MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={ graph.run { graph.transport.send(Frame(11,0,660,"adapt".toByteArray())) } },enabled=device.connected) { Text("Ping") }
                    TextButton(onClick={ graph.run { graph.transport.debug("bt disconnect") } },enabled=device.connected) { Text("Radio disconnect") }
                    TextButton(onClick={ graph.run { graph.transport.disconnect(); graph.stopSession(); graph.notes.stop() } }) { Text("Disconnect") }
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("Single","Double","Long","Recovery").forEachIndexed { i,label -> TextButton(onClick={ graph.run { graph.transport.gesture(i+1) } },enabled=device.connected) { Text(label) } }
                }
                Text("Events contain types and sequence numbers, never audio, transcripts or secrets.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                logs.take(15).forEach { Text(it,style=MaterialTheme.typography.labelSmall,fontFamily=FontFamily.Monospace) }
                val context=LocalContext.current
                TextButton(onClick={ context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,logs.joinToString("\n")),"Export sanitized diagnostics")) }) { Text("Export sanitized logs") }
            }
        }
    }
}
@Composable fun SettingsScreen(graph: AppGraph,arm: () -> Unit,associate: () -> Unit) {
    val prefs by graph.preferences.collectAsStateWithLifecycle()
    val armed by graph.armed.collectAsStateWithLifecycle()
    val phone by graph.allowPhone.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var bridgeToken by remember { mutableStateOf("") }
    var pcUrl by remember { mutableStateOf(graph.vault.get("pc_url")) }
    var pcPin by remember { mutableStateOf(graph.vault.get("pc_pin")) }
    var pairCode by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    Page("Settings","A quieter way to take control.") {
        SectionLabel("Headset")
        Group {
            DetailRow("Associate companion","Connect",click=associate)
            DetailRow("Bluetooth audio","Android settings",click={ context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) })
            Row(verticalAlignment=Alignment.CenterVertically) { Text("Hands-free mode",Modifier.weight(1f)); Switch(armed,{ if(it) arm() else graph.disarm() }) }
            Text("Start while visible. After reboot or stopping the service, enable it again. Samsung: Settings → Apps → ADAPT Control → Battery → Unrestricted; add to Never sleeping apps.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))) }) { Text("App & battery settings") }
        }
        SectionLabel("AI Provider")
        Group {
            ChoiceRow("Provider",prefs.provider,listOf("Gemini Live","System assistant")) { graph.run { graph.settings.set("provider",it) } }
            Text("Gemini Live uses the integrated voice provider. System assistant opens Android’s configured assistant from a visible screen; its Gemini Live and lock-screen behavior is controlled by Android.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            SmallField("Gemini Live model",prefs.model) { graph.run { graph.settings.set("model",it) } }
        }
        SectionLabel("Voice")
        Group {
            Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Allow phone audio"); Text("For testing without the headset",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(phone,{ graph.allowPhone.value=it }) }
            Text("No continuous recording. Losing the selected headset or audio focus ends capture.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionLabel("Study")
        Group { SmallField("Default topic",prefs.topic) { graph.run { graph.settings.set("topic",it) } }; ChoiceRow("Mode",prefs.mode,listOf("Explain","Quiz","Rapid Fire","Exam","Review Weak Topics")) { graph.run { graph.settings.set("mode",it) } } }
        SectionLabel("PC Companion")
        Group {
            Text(if(graph.vault.get("pc_token").isEmpty()) "Pair securely over your local network" else "PC paired · certificate pinned",style=MaterialTheme.typography.titleMedium)
            OutlinedTextField(pcUrl,{pcUrl=it},label={ Text("HTTPS address") },singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp))
            OutlinedTextField(pcPin,{pcPin=it},label={ Text("Certificate SHA-256 fingerprint") },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp))
            OutlinedTextField(pairCode,{pairCode=it},label={ Text("8-digit pairing code") },singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
            Button(onClick={ graph.run { graph.vault.put("pc_url",pcUrl.trim()); graph.vault.put("pc_pin",pcPin.trim()); graph.pc.pair(pairCode.trim()); pairCode=""; graph.message.value="PC paired securely" } },shape=CircleShape) { Text("Pair PC") }
            TextButton(onClick={ graph.vault.put("pc_token",""); graph.message.value="Phone pairing removed. Revoke the PC token on the PC too." }) { Text("Forget PC") }
        }
        SectionLabel("Appearance")
        Group { ChoiceRow("Theme",prefs.theme,listOf("System","Light","Dark")) { graph.run { graph.settings.set("theme",it) } } }
        SectionLabel("Advanced")
        Group {
            DetailRow("Simulator setup",if(advanced) "Open" else "Show",click={advanced=!advanced})
            if(advanced) {
                Text("Start the Phase 2 bridge on the PC, then use adb reverse tcp:6600 tcp:6600. The token stays in Android Keystore-protected storage.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(bridgeToken,{ bridgeToken=it },label={Text("Simulator token")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
                Button(onClick={ graph.run { if(bridgeToken.isNotBlank()) { graph.vault.put("bridge_token",bridgeToken.trim()); bridgeToken="" }; graph.connect() } },shape=CircleShape) { Text("Connect simulator") }
            }
            TextButton(onClick={ graph.run { graph.settings.set("provider","Gemini Live"); graph.message.value="Gemini Live selected" } }) { Text("Use integrated Gemini") }
        }
        SectionLabel("About")
        Group { DetailRow("ADAPT Control","0.3.0"); Text("Independent engineering project. No database service. Settings, notes and study history stay in this app. Live AI sends requested session audio to Google.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun Onboarding(graph: AppGraph,arm: () -> Unit,associate: () -> Unit,modifier: Modifier) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val context=LocalContext.current
    val prefs by graph.preferences.collectAsStateWithLifecycle()
    PageContent(modifier) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("ADAPT / CONTROL",style=MaterialTheme.typography.labelMedium,letterSpacing=2.sp); Pill("${step+1} of 5") }
        HeadsetHero()
        Text(listOf("Your headset.\nYour possibilities.","Make the connection.","Ready from your pocket.","A voice, on your terms.","Meet the purple button.")[step],style=MaterialTheme.typography.headlineLarge)
        Text(listOf("Welcome to ADAPT Control. A thoughtful home for your headset, ideas and everyday actions.",
            "Pair ADAPT 660 audio in Android settings, then associate it here. The firmware simulator supplies button events until custom hardware firmware is ready.",
            "Enable hands-free mode while this screen is visible. Microphone access is used only during a requested voice or note session. A notification gives you an immediate stop control.",
            "Integrated Gemini Live connects through Google’s AI Logic client. No database is required. Add local project configuration to enable live calls. Android’s system assistant is an optional visible-screen route.",
            "Single press: AI Voice. Double press: Voice Note. Long press: Study. Very long press: protected Pairing / Recovery. Test simulator gestures in Device → Engineering Mode.")[step],style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        when(step) {
            1 -> { Button(onClick={ context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },shape=CircleShape) { Text("Bluetooth settings") }; OutlinedButton(onClick=associate,shape=CircleShape) { Text("Associate headset") } }
            2 -> { Button(onClick=arm,shape=CircleShape) { Text("Enable hands-free") }; TextButton(onClick={ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))) }) { Text("Samsung battery settings") } }
            3 -> ChoiceRow("AI provider",prefs.provider,listOf("Gemini Live","System assistant")) { graph.run { graph.settings.set("provider",it) } }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick={ if(step<4) step++ else graph.run { graph.settings.finishOnboarding() } },modifier=Modifier.fillMaxWidth().height(56.dp),shape=CircleShape) { Text(if(step==4) "Start exploring" else "Continue") }
        if(step>0) TextButton(onClick={step--},modifier=Modifier.align(Alignment.CenterHorizontally)) { Text("Back") }
    }
}
@Composable private fun PageContent(modifier: Modifier,content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(18.dp),content=content)
}
