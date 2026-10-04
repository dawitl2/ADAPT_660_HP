package dev.adapt.control

import android.app.Application
import android.content.*
import android.media.MediaPlayer
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import dev.adapt.control.core.ai.*
import dev.adapt.control.core.audio.*
import dev.adapt.control.core.data.*
import dev.adapt.control.core.device.*
import dev.adapt.control.core.protocol.*
import dev.adapt.control.feature.actions.*
import dev.adapt.control.feature.notes.*
import dev.adapt.control.feature.study.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AdaptApplication : Application() {
    lateinit var graph: AppGraph
    override fun onCreate() {
        super.onCreate()
        if(FirebaseApp.getApps(this).isNotEmpty()) configureAppCheck()
        graph=AppGraph(this)
    }
}
class AppGraph(val context: Context) {
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    val settings=SettingsStore(context)
    val preferences=settings.flow.stateIn(scope,SharingStarted.Eagerly,Preferences())
    val vault=SecretVault(context)
    val repository=LocalRepository(context)
    val ready=MutableStateFlow(false)
    val armed=MutableStateFlow(false)
    val allowPhone=MutableStateFlow(false)
    var visible=false
    val message=MutableStateFlow<String?>(null)
    val events=MutableStateFlow<List<String>>(emptyList())
    val transcripts=MutableStateFlow<List<Transcript>>(emptyList())
    val study=MutableStateFlow(StudyProgress())
    val timings=MutableStateFlow<Map<Int,Long>>(emptyMap())
    val route: AudioRoute=AudioRoute(context) { reason -> scope.launch { stopSession(); notes.stop(); message.value=reason } }
    val gemini=GeminiLiveProvider(context,route,scope,{ preferences.value.model },{ allowPhone.value })
    val system=SystemAssistantProvider(context)
    val pc=PcActionProvider(vault)
    val phone=PhoneActionProvider(context,{ preferences.value },{ visible })
    val combined=CombinedActionEngine()
    val transport=SimulatorTransport({ vault.get("bridge_token") },scope)
    val router=ButtonRouter { action -> if(armed.value) execute(action,true) else message.value="Enable hands-free mode while ADAPT Control is open" }
    val notes: NoteRecorder=NoteRecorder(context,route,scope) { auto -> if(auto) saveNote() }
    private val actions=Mutex()
    private val noteSaving=Mutex()
    private var sessionTimeout: Job?=null
    private var playback: MediaPlayer?=null
    private var sessionStarted=0L
    init {
        scope.launch { try { repository.load(); ready.value=true } catch(e: Exception) { message.value="Local library could not be read. Existing files are preserved." } }
        scope.launch { transport.frames.collect { f ->
            event("ACP type=${f.type} seq=${f.sequence}")
            when(f.type) {
                4 -> runCatching { router.accept(Acp.button(f)) }.onFailure { message.value="Action could not run. Review setup and permissions." }
                13 -> message.value="Firmware rejected command (${f.payload[0].toInt() and 255})"
                12 -> message.value="Diagnostic ping received"
                7,6 -> if(f.flags==1) {
                    val key=f.payload[0].toInt() and 255
                    val value=java.nio.ByteBuffer.wrap(f.payload,1,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
                    if(key in 1..5) timings.value=timings.value+(key to value)
                }
            }
        } }
        scope.launch {
            transport.state.map { it.connected && it.radioLink != 1 }.distinctUntilChanged().collect { connected ->
                router.newConnection(); event(if(connected) "CONNECTED · simulator" else "DISCONNECTED · control transport")
                if(!connected) { if(sessionStarted!=0L) stopSession(); notes.stop() }
            }
        }
        scope.launch { gemini.transcript.collect { line ->
            transcripts.value=(transcripts.value+line).takeLast(500)
        } }
        scope.launch { gemini.feedback.collect { feedback -> if(study.value.active) study.value=StudyRules.applyFeedback(study.value,feedback) } }
        scope.launch { gemini.status.map { it.state }.distinctUntilChanged().collect { state ->
            if(state==VoiceState.Error) { sessionTimeout?.cancel(); message.value=gemini.status.value.error }
        } }
    }
    fun event(text: String) { events.value=(listOf(text)+events.value).take(100) }
    fun run(block: suspend () -> Unit) { scope.launch { try { block() } catch(e: CancellationException) { throw e }
        catch(e: Exception) { message.value=e.message?.takeIf { !it.contains("Bearer") }?.take(220) ?: "Action failed" } } }
    fun arm() {
        try { ContextCompat.startForegroundService(context,Intent(context,ControlService::class.java).setAction("arm")) }
        catch(e: Exception) { message.value="Open the app and grant microphone permission to enable hands-free mode" }
    }
    fun disarm() { context.stopService(Intent(context,ControlService::class.java)); armed.value=false; run { stopSession(); transport.disconnect() } }
    suspend fun connect() {
        transport.connect(); applyMappings()
        for(key in 1..5) transport.send(Frame(7,0,200+key,byteArrayOf(key.toByte())))
        repository.log("Firmware simulator connected")
    }
    suspend fun timing(key: Int,value: Long) {
        require(key in 1..5 && value in 1..60000) { "Timing must be 1–60000 milliseconds" }
        val payload=java.nio.ByteBuffer.allocate(5).order(java.nio.ByteOrder.LITTLE_ENDIAN).put(key.toByte()).putInt(value.toInt()).array()
        transport.send(Frame(6,0,300+key,payload))
        // Update only after the firmware's successful response; invalid combinations retain prior values.
    }
    private suspend fun applyMappings() { preferences.value.mappings.forEachIndexed { index,action ->
        transport.send(Frame(5,0,index+1,byteArrayOf((index+1).toByte(),action.toByte(),0)))
    } }
    suspend fun map(gesture: Int,action: Int) {
        require(gesture in 1..3)
        if(transport.state.value.connected) transport.send(Frame(5,0,100+gesture,byteArrayOf(gesture.toByte(),action.toByte(),0)))
        settings.mapping(gesture,action)
    }
    fun activate(action: Int) {
        if(!armed.value) { message.value="Enable hands-free mode first; microphone permission is requested only then"; return }
        run { execute(action,false) }
    }
    suspend fun execute(action: Int,physical: Boolean) = actions.withLock {
        require(ready.value) { "Local library is still loading" }
        event("ACTION $action · ${if(physical) "button" else "app"}")
        when(action) {
            1 -> { if(sessionStarted!=0L && !study.value.active) stopSession() else startVoice(false) }
            2 -> { if(notes.draft.value.recording) { notes.stop(); if(physical) saveNote() }
                else { stopSession(); notes.start(allowPhone.value,physical); repository.log("Voice note recording started") } }
            3 -> { if(study.value.active) stopSession() else startVoice(true) }
            4 -> phone.execute(preferences.value.phoneAction)
            5 -> pc.execute(preferences.value.pcAction)
            6 -> {
                val studyAction=object : ActionProvider { override suspend fun execute(action: String) {
                    require(action=="study"); startVoice(true); check(gemini.status.value.state==VoiceState.Listening)
                } }
                val results=combined.execute(listOf(ActionStep("Phone study",studyAction,"study"),ActionStep("PC app",pc,"open_app")))
                message.value=results.joinToString(" · ") { "${it.label}: ${if(it.succeeded) "started" else "unavailable"}" }
                repository.log("Focus · ${results.count { it.succeeded }}/${results.size} actions started")
            }
            else -> error("Configure a supported action first")
        }
    }
    private suspend fun startVoice(studyMode: Boolean) {
        stopSession(); notes.stop(); playback?.release(); playback=null
        require(preferences.value.provider=="Gemini Live" || studyMode) { "System assistant launch is available from the visible Voice screen; hands-free Gemini uses the integrated provider" }
        transcripts.value=emptyList()
        val prefs=preferences.value
        study.value=StudyProgress(topic=prefs.topic,mode=prefs.mode,active=studyMode)
        val history=repository.studies.value.take(5).flatMap { it.weakTopics.split('\n') }.filter { it.isNotBlank() }.distinct().joinToString(", ")
        val prompt=if(studyMode) StudyRules.instruction(prefs.topic,prefs.mode,if(prefs.mode=="Review Weak Topics") "Prior weak topics: $history" else "") else ""
        gemini.start(prompt)
        if(gemini.status.value.state==VoiceState.Error) { study.value=study.value.copy(active=false); return }
        sessionStarted=System.currentTimeMillis()
        repository.log(if(studyMode) "Study — ${prefs.topic}" else "Gemini session started")
        sessionTimeout=scope.launch { delay(15*60*1000); withContext(NonCancellable) { stopSession(); message.value="Session ended after 15 minutes. Press again to continue." } }
    }
    suspend fun stopSession() {
        sessionTimeout?.cancel(); sessionTimeout=null
        gemini.end()
        if(study.value.active && sessionStarted!=0L) {
            val s=study.value
            repository.save(StudySession(topic=s.topic,mode=s.mode,questions=s.questions.joinToString("\n"),answerQuality=s.quality.joinToString("\n"),
                mistakes=s.mistakes.joinToString("\n"),weakTopics=s.weakTopics.joinToString("\n"),transcript=transcripts.value.joinToString("\n") { "${it.speaker}: ${it.text}" },score=s.score,total=s.total))
        }
        if(sessionStarted!=0L) repository.log("${if(study.value.active) "Study" else "Gemini"} session — ${(System.currentTimeMillis()-sessionStarted)/1000}s")
        sessionStarted=0; study.value=study.value.copy(active=false)
    }
    suspend fun saveNote(): Unit = noteSaving.withLock {
        if(notes.draft.value.recording) notes.stop()
        val d=notes.draft.value
        require(d.audioPath!=null || d.transcript.isNotBlank()) { "Record or type a note first" }
        repository.save(VoiceNote(title=d.title.ifBlank { "Voice note" },rawTranscript=d.transcript,category=d.category,durationMs=d.durationMs,audioPath=d.audioPath))
        notes.reset(false); repository.log("Voice note saved"); message.value="Voice note saved on this phone"
    }
    fun playNote(path: String) {
        require(!notes.draft.value.recording && sessionStarted==0L)
        playback?.release()
        playback=MediaPlayer().apply { setDataSource(path); prepare(); setOnCompletionListener { it.release(); playback=null }; start() }
    }
}
