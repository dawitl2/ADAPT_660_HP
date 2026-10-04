package dev.adapt.control
import dev.adapt.control.core.protocol.*
import dev.adapt.control.feature.actions.*
import dev.adapt.control.feature.study.*
import dev.adapt.control.feature.notes.wavHeader
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
    @Test fun studyScoresRequireQuestionAndBoundedResult() {
        assertEquals(0,StudyRules.parse(StudyProgress(),"[RESULT: correct]").total)
        val p=StudyRules.parse(StudyProgress(),"[QUESTION: Define a unit test.] [RESULT: partial] [WEAK: Isolation] [MISTAKE: Avoid external dependencies.]")
        assertEquals(1,p.total); assertEquals(0,p.score); assertEquals(listOf("Isolation"),p.weakTopics)
        assertEquals(1,StudyRules.parse(p,"[RESULT: correct]").total)
    }
    @Test fun recordingHeaderIsPlayablePcm() {
        val h=wavHeader(32000); assertEquals(44,h.size); assertEquals("RIFF",h.copyOfRange(0,4).toString(Charsets.US_ASCII))
        assertEquals("WAVE",h.copyOfRange(8,12).toString(Charsets.US_ASCII))
    }
}
