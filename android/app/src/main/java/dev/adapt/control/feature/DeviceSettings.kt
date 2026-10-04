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
        HeadsetHero(highlight=region,onRegion={region=it})
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Purple button","Touch surface","ANC switch","Bluetooth control","USB","Audio jack").forEach { label ->
                FilterChip(region==label,{ region=label },{ Text(label) })
            }
        }
        Text(when(region) { "Purple button" -> "Your shortcuts, just a press away."; "Touch surface" -> "Music and calls at your fingertips."; "ANC switch" -> "Find your quiet."; "Bluetooth control" -> "Stay connected, wirelessly."; "USB" -> "Charge up for what comes next."; else -> "A direct connection to your sound." },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Group { DetailRow("Connection",if(device.connected) "Connected" else "Disconnected",device.transport); TextButton(onClick=associate) { Text("Associate ADAPT 660") } }
        Group { DetailRow("Battery",device.battery?.let { "$it%" } ?: "—"); DetailRow("Audio",if(audio.startsWith("ADAPT")) "Headset" else "Inactive") }
        Group {
            DetailRow("Noise control",listOf("—","Off","On","Adaptive").getOrElse(device.anc){"—"})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("Off","On","Adaptive").forEachIndexed { i,name -> FilterChip(device.anc==i+1,{ graph.run { graph.transport.debug("anc ${i+1}") } },{ Text(name) },enabled=device.connected) }
            }
        }
        Group { DetailRow("Model","ADAPT 660"); DetailRow("Firmware",if(device.connected) device.firmware else "—") }
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
    val timings by graph.timings.collectAsStateWithLifecycle()
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
            Row(verticalAlignment=Alignment.CenterVertically) { Text("Hands-free mode",Modifier.weight(1f)); AdaptSwitch(armed,{ if(it) arm() else graph.disarm() }) }
            Text("Enable here before putting your phone away. For the best experience, allow unrestricted battery use.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))) }) { Text("App & battery settings") }
        }
        SectionLabel("Purple Button")
        Group {
            Text("Gesture timing",style=MaterialTheme.typography.titleMedium)
            if(timings.isEmpty()) Text("Connect your headset to adjust press timing.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            listOf("Short press maximum","Double press window","Long press threshold","Protected recovery threshold","Debounce").forEachIndexed { i,label ->
                timings[i+1]?.let { ms -> SmallField("$label · ms",ms.toString()) { value -> graph.run { graph.timing(i+1,value.toLongOrNull() ?: error("Enter milliseconds")) } } }
            }
            Text("Firmware validates the timing combination. Protected recovery cannot be remapped or disabled.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionLabel("AI Provider")
        Group {
            ChoiceRow("Provider",prefs.provider,listOf("Gemini Live","System assistant")) { graph.run { graph.settings.set("provider",it) } }
            Text("Choose the voice experience that suits you.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionLabel("Voice")
        Group {
            Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Allow phone audio"); Text("Use your phone when the headset is away",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }; AdaptSwitch(phone,{ graph.allowPhone.value=it }) }
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
                SmallField("Gemini Live model",prefs.model) { graph.run { graph.settings.set("model",it) } }
                Text("Start the Phase 2 bridge on the PC, then use adb reverse tcp:6600 tcp:6600. The token stays in Android Keystore-protected storage.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(bridgeToken,{ bridgeToken=it },label={Text("Simulator token")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
                Button(onClick={ graph.run { if(bridgeToken.isNotBlank()) { graph.vault.put("bridge_token",bridgeToken.trim()); bridgeToken="" }; graph.connect() } },shape=CircleShape) { Text("Connect simulator") }
            }
            TextButton(onClick={ graph.run { graph.settings.set("provider","Gemini Live"); graph.message.value="Gemini Live selected" } }) { Text("Use integrated Gemini") }
        }
        SectionLabel("About")
        Group { DetailRow("ADAPT Control","0.3.0"); Text("Made for your ideas, your sound, your day. Notes and study history stay on your phone. Live AI shares session audio with your selected provider.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun Onboarding(graph: AppGraph,arm: () -> Unit,associate: () -> Unit,modifier: Modifier) {
    PageContent(modifier) {
        Text("ADAPT / CONTROL",style=MaterialTheme.typography.labelMedium,letterSpacing=2.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        HeadsetHero(highlight="Purple button")
        Text("Your headset.\nYour possibilities.",style=MaterialTheme.typography.headlineLarge)
        Text("A thoughtful home for your sound, your ideas and everything that comes next.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Group {
            listOf(Triple(Icons.Outlined.GraphicEq,"AI Voice","A conversation, just a press away."),
                Triple(Icons.Outlined.MicNone,"Voice Notes","Catch an idea before it slips away."),
                Triple(Icons.Outlined.AutoStories,"Study Companion","Make a little progress, anywhere.")).forEach { (icon,title,caption) ->
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(24.dp))
                    Column { Text(title,style=MaterialTheme.typography.titleSmall); Text(caption,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        Button(onClick={ graph.run { graph.settings.finishOnboarding() } },modifier=Modifier.fillMaxWidth().height(56.dp),shape=CircleShape) { Text("Get started",style=MaterialTheme.typography.titleMedium) }
        Text("Make the purple button yours.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.align(Alignment.CenterHorizontally))
    }
}
@Composable private fun PageContent(modifier: Modifier,content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(18.dp),content=content)
}
