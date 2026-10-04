package dev.adapt.control.feature.notes

import android.content.*
import android.media.*
import android.os.*
import android.speech.*
import dev.adapt.control.core.audio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class NoteDraft(val recording: Boolean=false,val transcript: String="",val title: String="",val category: String="Note",
    val durationMs: Long=0,val level: Float=0f,val audioPath: String?=null,val message: String?=null)
class NoteRecorder(private val context: Context,private val route: AudioRoute,private val scope: CoroutineScope,
    private val finished: suspend (Boolean) -> Unit) {
    val draft=MutableStateFlow(NoteDraft())
    private var job: Job?=null
    private var record: AudioRecord?=null
    private var recognition: SpeechRecognizer?=null
    private var descriptor: ParcelFileDescriptor?=null
    private var recognitionTimeout: Job?=null
    private var auto=false
    suspend fun start(allowPhone: Boolean,autoSave: Boolean) {
        check(!draft.value.recording)
        require(draft.value.audioPath==null) { "Save or discard the current note first" }
        route.acquire(allowPhone)
        auto=autoSave
        val path=File(context.filesDir,"notes").apply { mkdirs() }
        val file=File(path,"${java.util.UUID.randomUUID()}.wav")
        draft.value=NoteDraft(recording=true,audioPath=file.absolutePath)
        val rec=recorder(context,route); record=rec
        job=scope.launch(Dispatchers.IO) {
            var count=0; var speech=false; var quiet=0; var endedNormally=false
            try {
                RandomAccessFile(file,"rw").use { output ->
                    output.write(ByteArray(44)); rec.startRecording(); val b=ByteArray(2048)
                    try { while(isActive && count<16000*2*120) {
                        val n=rec.read(b,0,b.size,AudioRecord.READ_BLOCKING); check(n>0)
                        output.write(b,0,n); count+=n
                        val level=waveform(b,n)
                        if(level>.025f) { speech=true; quiet=0 } else quiet+=n
                        draft.value=draft.value.copy(level=level,durationMs=count*1000L/32000)
                        if((speech && quiet>=32000*2) || (!speech && count>=32000*15)) break
                    } } finally { output.seek(0); output.write(wavHeader(count)) }
                }
                endedNormally=true
            } catch(e: Exception) { if(e !is CancellationException) draft.value=draft.value.copy(message="Recording ended; check microphone access") }
            finally {
                runCatching { rec.stop() }; rec.release(); record=null; route.release()
                draft.value=draft.value.copy(recording=false,level=0f,title="Voice note",durationMs=count*1000L/32000)
                withContext(NonCancellable+Dispatchers.Main) { finished(auto && endedNormally) }
            }
        }
    }
    suspend fun stop() { record?.let { runCatching { it.stop() } }; job?.cancelAndJoin(); job=null }
    fun edit(text: String) { draft.value=draft.value.copy(transcript=text,title=text.trim().lineSequence().firstOrNull()?.take(64).orEmpty().ifBlank { "Voice note" }) }
    fun category(category: String) { require(category in listOf("Note","Idea","Task","Reminder","Project")); draft.value=draft.value.copy(category=category) }
    fun reset(delete: Boolean) { cancelTranscription(); if(delete) draft.value.audioPath?.let { File(it).delete() }; draft.value=NoteDraft() }
    fun cancelTranscription() { recognitionTimeout?.cancel(); recognition?.destroy(); recognition=null; descriptor?.close(); descriptor=null }
    /** Only Android's on-device recognizer is used. Audio remains savable without a language model. */
    fun transcribe() {
        check(Looper.myLooper()==Looper.getMainLooper())
        if(Build.VERSION.SDK_INT<33 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            draft.value=draft.value.copy(message="Offline transcription is unavailable. Keep the recording or type a transcript."); return
        }
        cancelTranscription()
        val file=draft.value.audioPath?.let(::File) ?: return
        val pipe=ParcelFileDescriptor.createPipe(); descriptor=pipe[0]
        val recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(context); recognition=recognizer
        draft.value=draft.value.copy(message="Transcribing on this phone…")
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                edit(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                draft.value=draft.value.copy(message="Transcribed on-device"); cancelTranscription()
            }
            override fun onError(error: Int) { draft.value=draft.value.copy(message="Offline transcription unavailable ($error). Recording is preserved."); cancelTranscription() }
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int,params: Bundle?) = Unit
        })
        val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE,pipe[0]).putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT,1)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,AudioFormat.ENCODING_PCM_16BIT).putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,16000)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
        recognizer.startListening(intent)
        scope.launch(Dispatchers.IO) {
            runCatching { ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { out -> file.inputStream().use { input -> input.skip(44); input.copyTo(out) } } }
        }
        recognitionTimeout=scope.launch { delay(20000); draft.value=draft.value.copy(message="Transcription timed out. Recording is preserved."); cancelTranscription() }
    }
}
fun wavHeader(size: Int): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
    put("RIFF".toByteArray()); putInt(size+36); put("WAVEfmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
    putInt(16000); putInt(32000); putShort(2); putShort(16); put("data".toByteArray()); putInt(size)
}.array()
