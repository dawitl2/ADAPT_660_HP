package dev.adapt.control.core.device

import android.app.Activity
import android.companion.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import java.util.regex.Pattern
import dev.adapt.control.AdaptApplication

@Suppress("DEPRECATION")
fun associateHeadset(activity: Activity,onChooser: (IntentSender) -> Unit,onResult: (String) -> Unit) {
    if(!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) { onResult("Companion association unavailable on this device"); return }
    val manager=activity.getSystemService(CompanionDeviceManager::class.java)
    val filter=BluetoothDeviceFilter.Builder().setNamePattern(Pattern.compile(".*(?:ADAPT|Adapt).*660.*")).build()
    val request=AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(false).build()
    manager.associate(request,object : CompanionDeviceManager.Callback() {
        override fun onDeviceFound(chooserLauncher: IntentSender) { onChooser(chooserLauncher) }
        override fun onFailure(error: CharSequence?) { onResult(error?.toString() ?: "Association failed") }
        override fun onAssociationCreated(info: AssociationInfo) {
            if(Build.VERSION.SDK_INT>=33) info.deviceMacAddress?.toString()?.let { manager.startObservingDevicePresence(it) }
            onResult("Headset associated. Audio pairing and custom firmware transport are separate.")
        }
    },Handler(Looper.getMainLooper()))
}
class HeadsetPresenceService : CompanionDeviceService() {
    override fun onDeviceAppeared(address: String) { (application as AdaptApplication).graph.event("Companion headset present") }
    override fun onDeviceDisappeared(address: String) {
        val graph=(application as AdaptApplication).graph
        graph.event("Companion headset absent")
        graph.run { graph.stopSession(); graph.notes.stop() }
    }
}
