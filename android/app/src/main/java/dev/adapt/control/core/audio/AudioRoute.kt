package dev.adapt.control.core.audio

import android.content.*
import android.media.*
import android.os.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AudioRoute(private val context: Context,private val interrupted: (String) -> Unit) {
    private val manager=context.getSystemService(AudioManager::class.java)
    val label=MutableStateFlow("No active audio session")
    private var selected: AudioDeviceInfo?=null
    private var oldMode=AudioManager.MODE_NORMAL
    private var focused=false
    private var legacySco=false
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener { if(it<=0 && focused) interrupted("Audio interrupted by another app or call") }.build()
    private val callback=object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(devices: Array<out AudioDeviceInfo>) {
            if(devices.any { it.id==selected?.id }) interrupted("Headset disconnected. Microphone closed.")
        }
    }
    private val routeChanged=if(Build.VERSION.SDK_INT>=31) AudioManager.OnCommunicationDeviceChangedListener { device ->
        if(focused && selected!=null && device?.id != selected?.id) interrupted("Communication route changed. Microphone closed.")
    } else null
    suspend fun acquire(allowPhone: Boolean) {
        require(manager.mode != AudioManager.MODE_IN_CALL) { "A phone call is active" }
        oldMode=manager.mode
        check(manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Audio focus unavailable" }
        focused=true
        try {
            manager.mode=AudioManager.MODE_IN_COMMUNICATION
            val devices=if(Build.VERSION.SDK_INT>=31) manager.availableCommunicationDevices else manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
            val headset=devices.firstOrNull { it.type in listOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,AudioDeviceInfo.TYPE_BLE_HEADSET) &&
                (it.productName.toString().contains("ADAPT",true) || it.productName.toString().contains("660")) }
            require(headset!=null || allowPhone) { "Connect ADAPT 660 audio in Android Bluetooth settings, or enable phone audio for testing" }
            selected=headset
            if(headset!=null) {
                if(Build.VERSION.SDK_INT>=31) {
                    check(manager.setCommunicationDevice(headset)) { "Headset audio route rejected" }
                    withTimeout(15000) { while(manager.communicationDevice?.id != headset.id) delay(100) }
                } else {
                    val connected=CompletableDeferred<Unit>()
                    val receiver=object : BroadcastReceiver() {
                        override fun onReceive(c: Context,i: Intent) { if(i.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE,-1)==AudioManager.SCO_AUDIO_STATE_CONNECTED) connected.complete(Unit) }
                    }
                    context.registerReceiver(receiver,IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED))
                    try { manager.startBluetoothSco(); legacySco=true; withTimeout(15000) { connected.await() }; manager.isBluetoothScoOn=true }
                    finally { context.unregisterReceiver(receiver) }
                }
                label.value="ADAPT 660 · headset microphone + speakers"
            } else label.value="Phone audio · testing mode"
            manager.registerAudioDeviceCallback(callback,Handler(Looper.getMainLooper()))
            if(Build.VERSION.SDK_INT>=31 && routeChanged!=null) manager.addOnCommunicationDeviceChangedListener(context.mainExecutor,routeChanged)
        } catch(e: Exception) { release(); throw e }
    }
    fun input(): AudioDeviceInfo? = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
        selected!=null && it.type==selected?.type && it.productName==selected?.productName }
    fun output()=selected
    fun release() {
        focused=false
        manager.unregisterAudioDeviceCallback(callback)
        if(Build.VERSION.SDK_INT>=31) { if(routeChanged!=null) manager.removeOnCommunicationDeviceChangedListener(routeChanged); manager.clearCommunicationDevice() }
        if(legacySco) { manager.stopBluetoothSco(); manager.isBluetoothScoOn=false; legacySco=false }
        manager.abandonAudioFocusRequest(focus)
        if(manager.mode==AudioManager.MODE_IN_COMMUNICATION) manager.mode=oldMode
        selected=null; label.value="No active audio session"
    }
}

fun recorder(context: Context,route: AudioRoute): AudioRecord {
    if(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
        throw SecurityException("Microphone permission is required")
    return AudioRecord.Builder()
    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
    .setBufferSizeInBytes(maxOf(8192,AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)))
    .build().also { check(it.state==AudioRecord.STATE_INITIALIZED); route.input()?.let(it::setPreferredDevice) }
}
fun waveform(bytes: ByteArray,count: Int): Float {
    var sum=0.0
    for(i in 0 until count-1 step 2) { val sample=((bytes[i].toInt() and 255) or (bytes[i+1].toInt() shl 8)).toShort().toDouble(); sum+=sample*sample }
    return (kotlin.math.sqrt(sum/maxOf(1,count/2))/32768.0).toFloat().coerceIn(0f,1f)
}
