package dev.adapt.control

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.launch

/** Armed from a visible activity. Idle holds eligibility, never captures audio continuously. */
class ControlService : Service() {
    private val graph get()=(application as AdaptApplication).graph
    override fun onBind(intent: Intent?)=null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action=="stop") { graph.disarm(); stopSelf(); return START_NOT_STICKY }
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("handsfree","Hands-free control",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop=PendingIntent.getService(this,1,Intent(this,ControlService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(this,"handsfree").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ADAPT Control is ready").setContentText("Button listening enabled · microphone opens only for a requested session")
            .setContentIntent(open).setOngoing(true).addAction(0,"Stop",stop).build()
        try {
            startForeground(660,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            graph.armed.value=true
        } catch(e: Exception) { graph.message.value="Hands-free mode needs microphone permission and a visible app to start"; stopSelf() }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        graph.armed.value=false
        graph.scope.launch { graph.stopSession(); graph.notes.stop(); graph.transport.disconnect() }
        super.onDestroy()
    }
}
