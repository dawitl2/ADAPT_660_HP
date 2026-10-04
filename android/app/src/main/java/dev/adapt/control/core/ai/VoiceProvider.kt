package dev.adapt.control.core.ai

import android.content.*
import android.media.*
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.*
import dev.adapt.control.core.audio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*

enum class VoiceState { Idle, Connecting, Listening, Thinking, Speaking, Error }
data class VoiceStatus(val state: VoiceState=VoiceState.Idle,val error: String?=null,val level: Float=0f)
data class Transcript(val speaker: String,val text: String)
data class StudyFeedback(val phase: String,val question: String,val quality: String,val mistake: String,val weakTopic: String)
interface VoiceAssistantProvider {
    val status: StateFlow<VoiceStatus>
    val transcript: SharedFlow<Transcript>
    suspend fun start(instruction: String="")
    suspend fun send(text: String)
    suspend fun end()
}

/** Manual PCM allows observable states and verified preferred headset devices on capture/playback. */
@OptIn(PublicPreviewAPI::class)
class GeminiLiveProvider(private val context: Context,private val route: AudioRoute,private val scope: CoroutineScope,
    private val model: () -> String,private val allowPhone: () -> Boolean) : VoiceAssistantProvider {
    private val _status=MutableStateFlow(VoiceStatus())
    override val status=_status.asStateFlow()
    private val _transcript=MutableSharedFlow<Transcript>(extraBufferCapacity=128)
    override val transcript=_transcript.asSharedFlow()
    private var session: LiveSession?=null
    private var job: Job?=null
    private var record: AudioRecord?=null
    private var playback: AudioTrack?=null
    private var generation=0
    val turns=MutableSharedFlow<String>(extraBufferCapacity=32)
    val feedback=MutableSharedFlow<StudyFeedback>(extraBufferCapacity=32)
    override suspend fun start(instruction: String) {
        end()
        val currentGeneration=++generation
        _status.value=VoiceStatus(VoiceState.Connecting)
        try {
            require(FirebaseApp.getApps(context).isNotEmpty()) { "Gemini needs local Firebase AI Logic configuration. Command transport and local notes need no database." }
            route.acquire(allowPhone())
            val live=Firebase.ai(backend=GenerativeBackend.googleAI()).liveModel(modelName=model(),generationConfig=liveGenerationConfig {
                responseModality=ResponseModality.AUDIO
                inputAudioTranscription=AudioTranscriptionConfig(); outputAudioTranscription=AudioTranscriptionConfig()
            },systemInstruction=content { text(instruction.ifEmpty { "You are a concise, helpful voice assistant for ADAPT Control. Never claim to execute phone or PC actions." }) },
                tools=if(instruction.isNotBlank()) listOf(Tool.functionDeclarations(listOf(FunctionDeclaration("record_study","Record a study question or evaluated answer without speaking metadata.",mapOf(
                    "phase" to Schema.enumeration(listOf("question","evaluation")), "question" to Schema.string(),
                    "quality" to Schema.enumeration(listOf("unanswered","correct","partial","incorrect")),
                    "mistake" to Schema.string(),"weak_topic" to Schema.string()))))) else emptyList())
            val connected=withTimeout(25000) { live.connect() }
            if(currentGeneration!=generation) { connected.close(); return }
            session=connected
            val rec=recorder(context,route); record=rec
            val track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(8192,AudioTrack.getMinBufferSize(24000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT))).build()
            playback=track; route.output()?.let(track::setPreferredDevice); track.play()
            job=scope.launch {
                try {
                    coroutineScope {
                        val audio=Channel<ByteArray>(64)
                        launch(Dispatchers.IO) {
                            for(chunk in audio) {
                                _status.value=_status.value.copy(state=VoiceState.Speaking)
                                var offset=0
                                while(offset<chunk.size && isActive) {
                                    val n=track.write(chunk,offset,chunk.size-offset,AudioTrack.WRITE_BLOCKING)
                                    check(n>0) { "Audio playback failed" }; offset+=n
                                }
                                // Playback is streamed, and the server turn marker settles listening state.
                            }
                        }
                        launch(Dispatchers.IO) {
                            rec.startRecording(); val bytes=ByteArray(2048); var lastSpeech=System.currentTimeMillis()
                            while(isActive) {
                                val n=rec.read(bytes,0,bytes.size,AudioRecord.READ_BLOCKING); check(n>0) { "Microphone unavailable" }
                                val level=waveform(bytes,n); val now=System.currentTimeMillis()
                                val state=if(level>.025f) { lastSpeech=now; VoiceState.Listening }
                                    else if(_status.value.state==VoiceState.Listening && now-lastSpeech>900) VoiceState.Thinking else _status.value.state
                                _status.value=VoiceStatus(state,level=level)
                                connected.sendAudioRealtime(InlineData(bytes.copyOf(n),"audio/pcm;rate=16000"))
                            }
                        }
                        var modelText=""
                        val toolIds=HashSet<String>()
                        connected.receive().collect { message ->
                            when(message) {
                                is LiveServerContent -> {
                                    message.inputTranscription?.text?.let { _transcript.emit(Transcript("You",it)) }
                                    message.outputTranscription?.text?.let { modelText+=it; _transcript.emit(Transcript("Gemini",it)) }
                                    if(message.interrupted) { modelText=""; track.pause(); track.flush(); track.play(); while(audio.tryReceive().isSuccess) {} }
                                    for(part in message.content?.parts.orEmpty().filterIsInstance<InlineDataPart>()) if(part.mimeType.startsWith("audio/pcm")) audio.send(part.inlineData)
                                    if(message.turnComplete) { turns.emit(modelText); modelText=""; _status.value=_status.value.copy(state=VoiceState.Listening) }
                                }
                                is LiveServerGoAway -> error("Gemini session is ending. Reconnect to continue.")
                                is LiveServerToolCall -> {
                                    val responses=message.functionCalls.map { call ->
                                        val valid=runCatching {
                                            require(instruction.isNotBlank() && call.name=="record_study")
                                            fun arg(key: String)=call.args[key]?.jsonPrimitive?.content ?: error("Missing field")
                                            val f=StudyFeedback(arg("phase"),arg("question"),arg("quality"),arg("mistake"),arg("weak_topic"))
                                            require(f.phase in listOf("question","evaluation") && f.quality in listOf("unanswered","correct","partial","incorrect"))
                                            require(f.question.length in 1..1000 && f.mistake.length<=500 && f.weakTopic.length<=200)
                                            val id=call.id ?: "${f.phase}:${f.question}:${f.quality}"
                                            if(toolIds.add(id)) feedback.emit(f)
                                        }.isSuccess
                                        FunctionResponsePart(call.name,buildJsonObject { put("recorded",valid) },call.id)
                                    }
                                    connected.sendFunctionResponse(responses)
                                }
                            }
                        }
                        error("Gemini connection ended. Reconnect to continue.")
                    }
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) { cleanup(); _status.value=VoiceStatus(VoiceState.Error,"Voice connection ended. Check network and AI configuration, then reconnect.") }
            }
            _status.value=VoiceStatus(VoiceState.Listening)
            if(instruction.isNotBlank()) connected.send("Start this study session now. Ask the first question.")
        } catch(e: CancellationException) { cleanup(); throw e }
        catch(e: Exception) { cleanup(); _status.value=VoiceStatus(VoiceState.Error, e.message?.takeIf { it.startsWith("Gemini needs") || it.startsWith("Connect ADAPT") } ?: "Unable to start voice. Check audio permission, network and AI configuration.") }
    }
    override suspend fun send(text: String) { session?.send(text) ?: error("Start a voice session first") }
    private suspend fun cleanup() {
        record?.let { runCatching { it.stop() }; it.release() }; record=null
        playback?.let { runCatching { it.stop() }; it.release() }; playback=null
        withContext(NonCancellable) { runCatching { session?.close() } }; session=null
        route.release()
    }
    override suspend fun end() { generation++; record?.let { runCatching { it.stop() } }; job?.cancelAndJoin(); job=null; cleanup(); _status.value=VoiceStatus() }
}

/** Android chooses the installed assistant. No package spoofing or coordinate automation. */
class SystemAssistantProvider(private val context: Context) : VoiceAssistantProvider {
    override val status=MutableStateFlow(VoiceStatus()).asStateFlow()
    override val transcript=MutableSharedFlow<Transcript>().asSharedFlow()
    override suspend fun start(instruction: String) {
        error("Launch the configured assistant from the visible app. Android does not guarantee third-party locked-screen Gemini Live activation.")
    }
    fun launchFromActivity(activity: android.app.Activity) { activity.startActivity(Intent(Intent.ACTION_VOICE_COMMAND)) }
    override suspend fun send(text: String): Unit = error("The system assistant owns its conversation")
    override suspend fun end() = Unit
}
class FutureOpenAIRealtimeProvider : VoiceAssistantProvider {
    override val status=MutableStateFlow(VoiceStatus()).asStateFlow()
    override val transcript=MutableSharedFlow<Transcript>().asSharedFlow()
    override suspend fun start(instruction: String): Unit = error("OpenAI realtime provider is not implemented")
    override suspend fun send(text: String): Unit = error("Provider is not implemented")
    override suspend fun end() = Unit
}
