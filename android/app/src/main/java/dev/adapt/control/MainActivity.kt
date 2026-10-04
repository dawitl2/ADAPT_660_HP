package dev.adapt.control

import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import dev.adapt.control.core.device.associateHeadset
import dev.adapt.control.feature.AppUi

class MainActivity : ComponentActivity() {
    private val graph get()=(application as AdaptApplication).graph
    private var afterPermission: (() -> Unit)?=null
    private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if(result.values.all { it }) afterPermission?.invoke() else graph.message.value="Permission declined. Enable it when you want this feature."
        afterPermission=null
    }
    private val association=registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        graph.message.value=if(it.resultCode==RESULT_OK) "Companion setup completed. Pair headset audio in Android settings." else "Companion setup cancelled"
    }
    private fun ask(requested: List<String>,then: () -> Unit) {
        val missing=requested.filter { ContextCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED }
        if(missing.isEmpty()) then() else { afterPermission=then; permissions.launch(missing.toTypedArray()) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppUi(graph,
            arm={ ask(listOf(Manifest.permission.RECORD_AUDIO)+if(Build.VERSION.SDK_INT>=33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()) { graph.arm() } },
            associate={ ask(if(Build.VERSION.SDK_INT>=31) listOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN) else emptyList()) {
                runCatching { associateHeadset(this,{ association.launch(IntentSenderRequest.Builder(it).build()) },{ graph.message.value=it }) }
                    .onFailure { graph.message.value="Companion association could not start" }
            } },
            systemAssistant={ runCatching { graph.system.launchFromActivity(this) }.onFailure { graph.message.value="No configured assistant accepts Android voice commands" } },
            flashlightPermission={ ask(listOf(Manifest.permission.CAMERA)) { graph.activate(4) } }) }
    }
    override fun onResume() { super.onResume(); graph.visible=true }
    override fun onPause() { graph.visible=false; super.onPause() }
}
