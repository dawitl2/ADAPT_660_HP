package dev.adapt.control.feature.study

import dev.adapt.control.core.data.*

data class StudyProgress(val topic: String="Software QA",val mode: String="Quiz",val question: String="Choose a topic. Learn out loud.",
    val score: Int=0,val total: Int=0,val mistakes: List<String> = emptyList(),val weakTopics: List<String> = emptyList(),
    val questions: List<String> = emptyList(),val quality: List<String> = emptyList(),val active: Boolean=false)
/** Machine-readable tags are validated before score updates; free text remains visible as transcript. */
object StudyRules {
    fun instruction(topic: String,mode: String,courseContext: String="") = """
        You are an ADAPT Control study tutor. Topic: ${topic.take(240)}. Mode: $mode.
        Speak concisely. Explain mode: teach one concept and invite questions.
        Quiz: ask one question at a time, wait for a spoken answer, evaluate it, give one short correction, then ask the next question.
        Rapid Fire: short questions. Exam: withhold explanations until the end. Review Weak Topics: revisit prior mistakes.
        Include [QUESTION: question text] in your output transcript for each new question.
        Include [RESULT: correct] or [RESULT: partial] or [RESULT: incorrect] once per evaluated answer.
        For mistakes include [WEAK: topic] and [MISTAKE: brief correction]. Do not count the initial greeting as an answer.
        Scores are practice feedback, not a certified grade. Do not claim access to unavailable course documents.
        Course context: ${courseContext.take(12000)}
    """.trimIndent()
    fun parse(progress: StudyProgress,turn: String): StudyProgress {
        var next=progress
        Regex("\\[QUESTION: (.{1,1000}?)\\]",RegexOption.DOT_MATCHES_ALL).findAll(turn).forEach {
            val q=it.groupValues[1].trim(); next=next.copy(question=q,questions=next.questions+q)
        }
        Regex("\\[RESULT: (correct|partial|incorrect)\\]").findAll(turn).forEach {
            if(next.total < next.questions.size) {
                val result=it.groupValues[1]
                next=next.copy(total=next.total+1,score=next.score+if(result=="correct") 1 else 0,quality=next.quality+result)
            }
        }
        val weak=Regex("\\[WEAK: (.{1,200}?)\\]").findAll(turn).map { it.groupValues[1] }.toList()
        val mistakes=Regex("\\[MISTAKE: (.{1,500}?)\\]").findAll(turn).map { it.groupValues[1] }.toList()
        return next.copy(weakTopics=(next.weakTopics+weak).distinct(),mistakes=next.mistakes+mistakes)
    }
    fun display(text: String)=text.replace(Regex("\\[(QUESTION|RESULT|WEAK|MISTAKE): .*?\\]",RegexOption.DOT_MATCHES_ALL),"").trim()
}
