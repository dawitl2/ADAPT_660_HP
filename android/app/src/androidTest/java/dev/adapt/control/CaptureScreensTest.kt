package dev.adapt.control

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual running Compose pixels. No generated screenshots, seeded battery values or dummy sessions. */
class CaptureScreensTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val graph get()=(compose.activity.application as AdaptApplication).graph
    private fun save(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(context.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        }
    }
    @Test fun captureActualScreensAndThemes() {
        runBlocking { graph.settings.finishOnboarding(); graph.settings.set("theme","Light"); graph.map(1,1) }
        compose.waitUntil(5000) { graph.ready.value && graph.preferences.value.onboarded && graph.preferences.value.theme=="Light" }
        save("home-light")
        compose.onNodeWithText("Actions").performClick(); save("actions-light")
        compose.onNodeWithText("Device").performClick(); save("device-light")
        compose.onNodeWithText("Settings").performClick(); save("settings-light")
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithContentDescription("AI Voice").performClick(); save("voice-light")
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithContentDescription("Voice Note").performClick(); save("notes-light")
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithContentDescription("Study").performClick(); save("study-light")
        compose.onNodeWithText("Home").performClick()
        runBlocking { graph.settings.set("theme","Dark") }
        compose.waitUntil(5000) { graph.preferences.value.theme=="Dark" }; save("home-dark")
        runBlocking { graph.settings.set("theme","Light") }
    }
}
