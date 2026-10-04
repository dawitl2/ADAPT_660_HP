package dev.adapt.control
import dev.adapt.control.core.protocol.*
import dev.adapt.control.feature.actions.*
import dev.adapt.control.feature.study.*
import dev.adapt.control.feature.notes.wavHeader
import dev.adapt.control.core.ai.StudyFeedback
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
class ActionStudyTest {
    @Test fun actionRoutingDeduplicatesAndReconnectResets() = runTest {
        val actions=mutableListOf<Int>(); val router=ButtonRouter { actions.add(it) }; val e=ButtonEvent(2,2,500,11)
        router.accept(e); router.accept(e); assertEquals(listOf(2),actions)
        router.newConnection(); router.accept(e); assertEquals(listOf(2,2),actions)
    }
    @Test fun recoveryIsRejected() = runTest {
        val router=ButtonRouter { error("Must not execute") }
        try { router.accept(ButtonEvent(4,1,0,0)); fail() } catch(e: IllegalArgumentException) { }
    }
    @Test fun recordingHeaderIsPlayablePcm() {
        val h=wavHeader(32000); assertEquals(44,h.size); assertEquals("RIFF",h.copyOfRange(0,4).toString(Charsets.US_ASCII))
        assertEquals("WAVE",h.copyOfRange(8,12).toString(Charsets.US_ASCII))
    }
    @Test fun spokenStudyFeedbackRejectsStaleAndUnaskedAnswers() {
        val answer=StudyFeedback("evaluation","Define a unit test","correct","","")
        assertEquals(0,StudyRules.applyFeedback(StudyProgress(),answer).total)
        val question=StudyRules.applyFeedback(StudyProgress(),answer.copy(phase="question",quality="unanswered"))
        assertEquals(0,StudyRules.applyFeedback(question,answer.copy(question="Another question")).total)
        val result=StudyRules.applyFeedback(question,answer)
        assertEquals(1,result.score); assertEquals(1,result.total)
        assertEquals(1,StudyRules.applyFeedback(result,answer).total)
    }
    @Test fun combinedActionsReportPartialFailureAndContinue() = runTest {
        val calls=mutableListOf<String>()
        val unavailable=object : ActionProvider { override suspend fun execute(action: String) { error("Unavailable") } }
        val working=object : ActionProvider { override suspend fun execute(action: String) { calls.add(action) } }
        val result=CombinedActionEngine().execute(listOf(ActionStep("Phone",unavailable,"study"),ActionStep("PC",working,"open_app")))
        assertFalse(result[0].succeeded); assertTrue(result[1].succeeded); assertEquals(listOf("open_app"),calls)
    }
}
