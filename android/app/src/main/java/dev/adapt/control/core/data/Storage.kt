package dev.adapt.control.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import org.json.JSONObject
import org.json.JSONArray
import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.UUID

val Context.preferences by preferencesDataStore("adapt_preferences")
data class Preferences(val theme: String="System", val provider: String="Gemini Live", val onboarded: Boolean=false,
    val topic: String="Software QA", val mode: String="Quiz", val phoneAction: String="Play / Pause",
    val pcAction: String="play_pause", val selectedUrl: String="https://developer.android.com", val selectedApp: String="",
    val model: String="gemini-3.1-flash-live-preview", val mappings: List<Int> = listOf(1,2,3))
class SettingsStore(private val context: Context) {
    val flow = context.preferences.data.map { p -> Preferences(
        p[stringPreferencesKey("theme")] ?: "System",p[stringPreferencesKey("provider")] ?: "Gemini Live",
        p[booleanPreferencesKey("onboarded")] ?: false,p[stringPreferencesKey("topic")] ?: "Software QA",
        p[stringPreferencesKey("mode")] ?: "Quiz",p[stringPreferencesKey("phoneAction")] ?: "Play / Pause",
        p[stringPreferencesKey("pcAction")] ?: "play_pause",p[stringPreferencesKey("url")] ?: "https://developer.android.com",
        p[stringPreferencesKey("app")] ?: "",p[stringPreferencesKey("model")] ?: "gemini-3.1-flash-live-preview",
        (1..3).map { p[intPreferencesKey("mapping$it")] ?: it }) }
    suspend fun set(key: String,value: String) { context.preferences.edit { it[stringPreferencesKey(key)]=value } }
    suspend fun finishOnboarding() { context.preferences.edit { it[booleanPreferencesKey("onboarded")]=true } }
    suspend fun mapping(gesture: Int,action: Int) {
        require(gesture in 1..3 && (action in 1..6 || action in 16..23))
        context.preferences.edit { it[intPreferencesKey("mapping$gesture")]=action }
    }
}

/** AES-GCM ciphertext stays in private preferences; encryption key never leaves Android Keystore. */
class SecretVault(context: Context) {
    private val prefs=context.getSharedPreferences("protected_secrets",Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("adapt.v1",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("adapt.v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun put(name: String,value: String) {
        if(value.isEmpty()) { prefs.edit().remove(name).apply(); return }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key())
        cipher.updateAAD(name.toByteArray())
        prefs.edit().putString(name,Base64.encodeToString(cipher.iv+cipher.doFinal(value.toByteArray()),Base64.NO_WRAP)).apply()
    }
    @Synchronized fun get(name: String): String {
        val encoded=prefs.getString(name,null) ?: return ""
        return try {
            val bytes=Base64.decode(encoded,Base64.NO_WRAP)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
            cipher.updateAAD(name.toByteArray())
            cipher.doFinal(bytes.copyOfRange(12,bytes.size)).toString(Charsets.UTF_8)
        } catch(e: Exception) { "" }
    }
}

data class VoiceNote(val id: String=UUID.randomUUID().toString(), val title: String,
    val rawTranscript: String, val cleanedTranscript: String?=null, val category: String="Note",
    val createdTime: Long=System.currentTimeMillis(), val durationMs: Long=0, val tags: String="", val project: String?=null,
    val audioPath: String?=null)
data class StudySession(val id: String=UUID.randomUUID().toString(), val topic: String,
    val mode: String, val questions: String, val answerQuality: String, val mistakes: String,
    val weakTopics: String, val transcript: String, val score: Int, val total: Int, val date: Long=System.currentTimeMillis())
data class ActivityEntry(val id: String=UUID.randomUUID().toString(), val label: String,
    val date: Long=System.currentTimeMillis())
/** Private atomic JSON files, per the user's no-database requirement. No remote store. */
class LocalRepository(context: Context) {
    private val file=AtomicFile(File(context.filesDir,"library.json"))
    val notes=MutableStateFlow<List<VoiceNote>>(emptyList())
    val studies=MutableStateFlow<List<StudySession>>(emptyList())
    val activity=MutableStateFlow<List<ActivityEntry>>(emptyList())
    private val lock=kotlinx.coroutines.sync.Mutex()
    suspend fun load() = withContext(Dispatchers.IO) {
        lock.lock()
        try {
            if(!file.baseFile.exists()) return@withContext
            val j=JSONObject(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
            notes.value=(j.optJSONArray("notes") ?: JSONArray()).objects().map { n -> VoiceNote(n.getString("id"),n.getString("title"),n.getString("raw"),
                n.optString("cleaned").takeIf { it.isNotEmpty() },n.getString("category"),n.getLong("date"),n.getLong("duration"),n.optString("tags"),
                n.optString("project").takeIf { it.isNotEmpty() },n.optString("audio").takeIf { it.isNotEmpty() }) }
            studies.value=(j.optJSONArray("studies") ?: JSONArray()).objects().map { n -> StudySession(n.getString("id"),n.getString("topic"),n.getString("mode"),
                n.getString("questions"),n.getString("quality"),n.getString("mistakes"),n.getString("weak"),n.getString("transcript"),n.getInt("score"),n.getInt("total"),n.getLong("date")) }
            activity.value=(j.optJSONArray("activity") ?: JSONArray()).objects().map { ActivityEntry(it.getString("id"),it.getString("label"),it.getLong("date")) }
        } finally { lock.unlock() }
    }
    private fun JSONArray.objects()=(0 until length()).map { getJSONObject(it) }
    private suspend fun persist(change: () -> Unit) = withContext(Dispatchers.IO) {
        lock.lock()
        try {
            val oldNotes=notes.value; val oldStudies=studies.value; val oldActivity=activity.value
            change()
            val j=JSONObject().put("version",1).put("notes",JSONArray(notes.value.map { n -> JSONObject().put("id",n.id).put("title",n.title)
                .put("raw",n.rawTranscript).put("cleaned",n.cleanedTranscript ?: "").put("category",n.category).put("date",n.createdTime)
                .put("duration",n.durationMs).put("tags",n.tags).put("project",n.project ?: "").put("audio",n.audioPath ?: "") }))
                .put("studies",JSONArray(studies.value.map { n -> JSONObject().put("id",n.id).put("topic",n.topic).put("mode",n.mode)
                    .put("questions",n.questions).put("quality",n.answerQuality).put("mistakes",n.mistakes).put("weak",n.weakTopics)
                    .put("transcript",n.transcript).put("score",n.score).put("total",n.total).put("date",n.date) }))
                .put("activity",JSONArray(activity.value.map { JSONObject().put("id",it.id).put("label",it.label).put("date",it.date) }))
            val output=file.startWrite()
            try { output.write(j.toString().toByteArray()); file.finishWrite(output) }
            catch(e: Exception) { file.failWrite(output); notes.value=oldNotes; studies.value=oldStudies; activity.value=oldActivity; throw e }
        } finally { lock.unlock() }
    }
    suspend fun save(note: VoiceNote) = persist { notes.value=listOf(note)+notes.value }
    suspend fun save(session: StudySession) = persist { studies.value=listOf(session)+studies.value }
    suspend fun log(label: String) = persist { activity.value=(listOf(ActivityEntry(label=label))+activity.value).take(12) }
    suspend fun deleteNote(id: String) = persist { notes.value=notes.value.filter { it.id != id } }
}

interface CourseSource { suspend fun contextFor(topic: String): String }
class NoCourseSource : CourseSource { override suspend fun contextFor(topic: String)="" }
