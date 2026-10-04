package dev.adapt.control

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.adapt.control.core.ai.*
import dev.adapt.control.core.data.*
import dev.adapt.control.core.protocol.*
import dev.adapt.control.feature.AppUi
import dev.adapt.control.core.designsystem.AdaptTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class AppInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val graph get()=(compose.activity.application as AdaptApplication).graph
    private fun finishSetup() {
        runBlocking { graph.settings.finishOnboarding() }
        compose.waitUntil(5000) { graph.ready.value && graph.preferences.value.onboarded }
        compose.waitForIdle()
    }
    @Test fun navigationRecoveryAndLocalNote() {
        finishSetup()
        compose.onAllNodesWithText("ADAPT 660")[0].assertExists()
        compose.onNodeWithText("Actions").performClick()
        compose.onNodeWithText("Pairing / Recovery").assertExists()
        compose.onNodeWithText("Locked").assertExists()
        compose.onNodeWithText("Single Press").performClick()
        compose.onNodeWithText("Voice Note").performClick()
        compose.waitUntil(5000) { graph.preferences.value.mappings[0]==2 }
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithContentDescription("Voice Note").performScrollTo().performClick()
        compose.onNodeWithText("Your transcript or a typed note…").performTextInput("Instrumented local note")
        compose.onNodeWithText("Save note").performScrollTo().performClick()
        compose.waitUntil(5000) { graph.repository.notes.value.any { it.rawTranscript=="Instrumented local note" } }
        compose.onNodeWithText("Search notes").performScrollTo().performTextInput("Instrumented")
        compose.onAllNodesWithText("Instrumented local note").assertCountEquals(1)
        runBlocking { graph.map(1,1); graph.repository.notes.value.filter { it.rawTranscript=="Instrumented local note" }.forEach { graph.repository.deleteNote(it.id) } }
    }
    @Test fun privateFilesAndKeystoreRoundTrip() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val isolated=context.createDeviceProtectedStorageContext()
        // Separate temporary directory/context wrapper avoids overwriting the app's real library.
        val dir=File(context.cacheDir,"repository-test-${System.nanoTime()}").apply { mkdirs() }
        val wrapper=object : android.content.ContextWrapper(context) { override fun getFilesDir()=dir }
        runBlocking {
            val first=LocalRepository(wrapper)
            first.save(VoiceNote(title="Local",rawTranscript="No cloud needed",category="Idea",durationMs=1200,tags="test",project="ADAPT"))
            first.save(StudySession(topic="QA",mode="Quiz",questions="q",answerQuality="partial",mistakes="m",weakTopics="w",transcript="t",score=0,total=1))
            val reopened=LocalRepository(wrapper); reopened.load()
            assertEquals("No cloud needed",reopened.notes.value.single().rawTranscript)
            assertEquals("ADAPT",reopened.notes.value.single().project)
            assertEquals(1,reopened.studies.value.single().total)
            reopened.deleteNote(reopened.notes.value.single().id)
            val third=LocalRepository(wrapper); third.load(); assertTrue(third.notes.value.isEmpty())
        }
        val vault=SecretVault(context); vault.put("instrumented_test","private-pairing-token")
        assertEquals("private-pairing-token",vault.get("instrumented_test"))
        val raw=context.getSharedPreferences("protected_secrets",Context.MODE_PRIVATE).getString("instrumented_test","")!!
        assertFalse(raw.contains("private-pairing-token")); vault.put("instrumented_test","")
        dir.deleteRecursively()
    }
    @Test fun missingGeminiConfigurationEndsWithoutMicAndCanRetry() {
        if(com.google.firebase.FirebaseApp.getApps(compose.activity).isNotEmpty()) return
        runBlocking(Dispatchers.Main) {
            graph.gemini.start(); assertEquals(VoiceState.Error,graph.gemini.status.value.state)
            assertTrue(graph.gemini.status.value.error!!.contains("configuration"))
            graph.gemini.end(); assertEquals(VoiceState.Idle,graph.gemini.status.value.state)
            graph.gemini.start(); assertEquals(VoiceState.Error,graph.gemini.status.value.state)
            graph.gemini.end()
        }
    }
    @Test fun simulatorCodecAndButtonAction() {
        finishSetup()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val tokenFile=File(context.filesDir,"integration-token.txt")
        Assume.assumeTrue("Push the ignored simulator token into app files for integration",tokenFile.exists())
        graph.vault.put("bridge_token",tokenFile.readText().trim())
        runBlocking(Dispatchers.Main) {
            graph.transport.connect()
            withTimeout(5000) { while(graph.transport.state.value.firmware=="Unknown") delay(50) }
            assertEquals("Firmware simulator",graph.transport.state.value.transport)
            val observed=async { withTimeout(5000) { graph.transport.frames.first { it.type==4 } } }
            delay(50); graph.transport.gesture(2)
            val event=Acp.button(observed.await()); assertEquals(2,event.gesture); assertEquals(2,event.action)
            graph.transport.send(Frame(11,0,66,"test".toByteArray()))
            graph.transport.disconnect()
        }
    }
    @Test fun foregroundRecordingStopsWithValidAudioFile() {
        finishSetup()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName,android.Manifest.permission.RECORD_AUDIO)
        runBlocking(Dispatchers.Main) {
            graph.allowPhone.value=true
            graph.notes.reset(true)
            graph.arm()
            withTimeout(5000) { while(!graph.armed.value) delay(50) }
            graph.notes.start(true,false)
            delay(1200)
            graph.notes.stop()
            val path=graph.notes.draft.value.audioPath!!
            val bytes=File(path).readBytes()
            assertTrue(bytes.size>44)
            assertEquals("RIFF",bytes.copyOfRange(0,4).toString(Charsets.US_ASCII))
            graph.saveNote()
            val saved=graph.repository.notes.value.first()
            assertEquals(path,saved.audioPath)
            graph.playNote(path)
            delay(1500)
            graph.repository.deleteNote(saved.id); File(path).delete()
            graph.disarm(); graph.allowPhone.value=false
        }
    }
    @Test fun pcPinnedPairingAndActionAcrossHttps() {
        finishSetup()
        val configFile=File(compose.activity.filesDir,"pc-integration.json")
        Assume.assumeTrue("Run the host integration fixture and push private config",configFile.exists())
        val config=JSONObject(configFile.readText())
        graph.vault.put("pc_url",config.getString("url")); graph.vault.put("pc_pin",config.getString("pin"))
        runBlocking {
            graph.pc.pair(config.getString("code"))
            assertTrue(graph.vault.get("pc_token").isNotBlank())
            graph.pc.execute("play_pause")
            graph.vault.put("pc_pin","0".repeat(64))
            var rejected=false
            try { graph.pc.execute("mute") } catch(e: Exception) { rejected=true }
            assertTrue("Certificate mismatch must reject an action",rejected)
        }
        graph.vault.put("pc_token",""); graph.vault.put("pc_url",""); graph.vault.put("pc_pin","")
    }
}
