package dev.adapt.control.feature.study

import dev.adapt.control.core.data.*
import dev.adapt.control.core.ai.StudyFeedback

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
        Call record_study with phase=question, question=the new question, quality=unanswered, mistake="", weak_topic="" for each question.
        After an answer call record_study with phase=evaluation, the same question, quality=correct/partial/incorrect, and short mistake/weak_topic fields.
        Do not speak tool fields or metadata. Do not count the greeting as an answer. Speak the question naturally.
        Scores are practice feedback, not a certified grade. Do not claim access to unavailable course documents.
        Course context: ${courseContext.take(12000)}
    """.trimIndent()
    fun applyFeedback(progress: StudyProgress,feedback: StudyFeedback): StudyProgress {
        if(feedback.phase=="question") return progress.copy(question=feedback.question,questions=progress.questions+feedback.question)
        if(feedback.phase!="evaluation" || feedback.quality !in listOf("correct","partial","incorrect") || progress.total>=progress.questions.size || feedback.question!=progress.question) return progress
        return progress.copy(score=progress.score+if(feedback.quality=="correct") 1 else 0,total=progress.total+1,
            quality=progress.quality+feedback.quality,mistakes=progress.mistakes+listOf(feedback.mistake).filter { it.isNotBlank() },
            weakTopics=(progress.weakTopics+listOf(feedback.weakTopic).filter { it.isNotBlank() }).distinct())
    }
    fun display(text: String)=text.replace(Regex("\\[(QUESTION|RESULT|WEAK|MISTAKE): .*?\\]",RegexOption.DOT_MATCHES_ALL),"").trim()
}
