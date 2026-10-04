package dev.adapt.control.feature.actions

import android.app.KeyguardManager
import android.content.*
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.provider.AlarmClock
import android.view.KeyEvent
import dev.adapt.control.core.data.*
import dev.adapt.control.core.protocol.ButtonEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

enum class ActionTarget { AI, PHONE, PC, BOTH, UTILITY, CUSTOM }
data class ActionDefinition(val id: Int,val title: String,val target: ActionTarget)
val actionCatalog=listOf(ActionDefinition(1,"AI Voice",ActionTarget.AI),ActionDefinition(2,"Voice Note",ActionTarget.UTILITY),
    ActionDefinition(3,"Study Companion",ActionTarget.AI),ActionDefinition(4,"Phone action",ActionTarget.PHONE),
    ActionDefinition(5,"PC action",ActionTarget.PC),ActionDefinition(6,"Focus · phone + PC",ActionTarget.BOTH))
interface ActionProvider { suspend fun execute(action: String) }
data class ActionStep(val label: String,val provider: ActionProvider,val action: String)
data class ActionOutcome(val label: String,val succeeded: Boolean)
class CombinedActionEngine {
    suspend fun execute(steps: List<ActionStep>): List<ActionOutcome> {
        require(steps.size in 1..8)
        return steps.map { step ->
            try { step.provider.execute(step.action); ActionOutcome(step.label,true) }
            catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { ActionOutcome(step.label,false) }
        }
    }
}

class PhoneActionProvider(private val context: Context,private val preferences: () -> Preferences,private val visible: () -> Boolean) : ActionProvider {
    private var torch=false
    override suspend fun execute(action: String) {
        val audio=context.getSystemService(AudioManager::class.java)
        when(action) {
            "Play / Pause","Next track" -> {
                val key=if(action=="Next track") KeyEvent.KEYCODE_MEDIA_NEXT else KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,key)); audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,key))
            }
            "Flashlight" -> {
                val camera=context.getSystemService(CameraManager::class.java)
                val id=camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)==true }
                    ?: error("No flashlight available")
                camera.setTorchMode(id,!torch); torch=!torch
            }
            "Open URL","Open app","Timer" -> {
                require(visible() && !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) { "Open ADAPT Control and unlock the phone to launch another app" }
                val intent=when(action) {
                    "Open URL" -> { val uri=Uri.parse(preferences().selectedUrl); require(uri.scheme in listOf("https","http") && !uri.host.isNullOrBlank()); Intent(Intent.ACTION_VIEW,uri) }
                    "Open app" -> context.packageManager.getLaunchIntentForPackage(preferences().selectedApp) ?: error("Selected app is not available")
                    else -> Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH,25*60).putExtra(AlarmClock.EXTRA_MESSAGE,"ADAPT Focus").putExtra(AlarmClock.EXTRA_SKIP_UI,false)
                }
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            else -> error("Unsupported phone action")
        }
    }
}

class PcActionProvider(private val vault: SecretVault) : ActionProvider {
    private suspend fun request(path: String,body: JSONObject,authorize: Boolean): JSONObject = withContext(Dispatchers.IO) {
        val base=URL(vault.get("pc_url")); require(base.protocol=="https" && base.userInfo==null && base.path in listOf("","/")) { "Use a PC HTTPS origin" }
        val pin=vault.get("pc_pin").replace(":","").lowercase(); require(pin.matches(Regex("[0-9a-f]{64}"))) { "Enter the PC certificate fingerprint" }
        val trust=object : X509TrustManager {
            override fun getAcceptedIssuers()=emptyArray<X509Certificate>()
            override fun checkClientTrusted(chain: Array<X509Certificate>,authType: String): Unit = error("Client certificates unsupported")
            override fun checkServerTrusted(chain: Array<X509Certificate>,authType: String) {
                require(chain.isNotEmpty()); chain[0].checkValidity()
                val hash=MessageDigest.getInstance("SHA-256").digest(chain[0].encoded).joinToString("") { "%02x".format(it) }
                require(MessageDigest.isEqual(hash.toByteArray(),pin.toByteArray())) { "PC certificate does not match pairing fingerprint" }
            }
        }
        val ssl=SSLContext.getInstance("TLS").apply { init(null,arrayOf(trust),SecureRandom()) }
        val conn=URL(base,path).openConnection() as HttpsURLConnection
        try {
            conn.sslSocketFactory=ssl.socketFactory
            // The exact certificate pin, compared inside the trust manager, is the peer identity.
            conn.hostnameVerifier=HostnameVerifier { host,_ -> host==base.host }
            conn.connectTimeout=5000; conn.readTimeout=5000; conn.instanceFollowRedirects=false
            conn.requestMethod="POST"; conn.doOutput=true; conn.setRequestProperty("Content-Type","application/json")
            if(authorize) conn.setRequestProperty("Authorization","Bearer ${vault.get("pc_token")}")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            require(conn.responseCode==200) { "PC rejected the request (${conn.responseCode})" }
            val bytes=conn.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream()
                val chunk=ByteArray(1024)
                while(output.size()<=8192) {
                    val count=input.read(chunk,0,minOf(chunk.size,8193-output.size()))
                    if(count<0) break
                    output.write(chunk,0,count)
                }
                output.toByteArray()
            }; require(bytes.size<=8192)
            JSONObject(bytes.toString(Charsets.UTF_8))
        } finally { conn.disconnect() }
    }
    suspend fun pair(code: String) {
        require(code.matches(Regex("[0-9]{8}")))
        val result=request("/pair",JSONObject().put("code",code),false)
        vault.put("pc_token",result.getString("token"))
    }
    override suspend fun execute(action: String) {
        require(action in listOf("play_pause","volume_up","volume_down","mute","open_app","lock"))
        require(vault.get("pc_token").isNotEmpty()) { "Pair your PC in Settings first" }
        request("/action",JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("action",action),true)
    }
}

/** Deduplicates only within a connection. Recovery remains a local firmware operation. */
class ButtonRouter(private val invoke: suspend (Int) -> Unit) {
    private val seen=LinkedHashSet<String>()
    fun newConnection() { seen.clear() }
    suspend fun accept(event: ButtonEvent) {
        require(event.gesture in 1..3)
        val key="${event.sequence}:${event.timestamp}:${event.action}"
        if(!seen.add(key)) return
        if(seen.size>128) seen.remove(seen.first())
        invoke(event.action)
    }
}
