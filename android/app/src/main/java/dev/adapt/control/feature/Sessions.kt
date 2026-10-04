package dev.adapt.control.feature

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.adapt.control.AppGraph
import dev.adapt.control.core.ai.*
import dev.adapt.control.core.designsystem.*
import dev.adapt.control.feature.study.StudyRules
import kotlin.math.*

@Composable fun VoiceOrb(status: VoiceStatus) {
    val level by animateFloatAsState(status.level,animationSpec=tween(120),label="Measured speech energy")
    val scale by animateFloatAsState(if(status.state in listOf(VoiceState.Listening,VoiceState.Speaking)) 1.03f else .94f,spring(stiffness=Spring.StiffnessLow),label="Voice orb scale")
    Canvas(Modifier.fillMaxWidth().height(260.dp)) {
        val center=Offset(size.width/2,size.height/2); val radius=83.dp.toPx()*scale
        drawCircle(Brush.radialGradient(listOf(Purple.copy(alpha=.07f),Color.Transparent),center,radius*1.35f),radius*1.35f,center)
        drawCircle(Brush.linearGradient(listOf(Color(0xffaa96e6),Purple,Color(0xff4d6cb1)),center-Offset(radius,radius),center+Offset(radius,radius)),radius,center)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha=.26f),Color.Transparent),center-Offset(radius*.25f,radius*.35f),radius*.75f),radius,center)
        for(i in -8..8) {
            val height=(4f+level*160f*(1f-abs(i)/10f))*density
            val x=center.x+i*7.dp.toPx()
            drawLine(Color.White.copy(alpha=.8f),Offset(x,center.y-height/2),Offset(x,center.y+height/2),3.dp.toPx(),StrokeCap.Round)
        }
    }
}
@Composable fun Waveform(level: Float,active: Boolean) {
    val energy by animateFloatAsState(if(active) level else 0f,tween(100),label="Microphone waveform")
    val color=MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(84.dp)) {
        val n=40
        for(i in 0 until n) {
            val height=4.dp.toPx()+energy*size.height*(.25f+.75f*abs(sin(i*1.67f)))
            val x=size.width*(i+.5f)/n
            drawLine(color.copy(alpha=if(active) .8f else .3f),Offset(x,(size.height-height)/2),Offset(x,(size.height+height)/2),3.dp.toPx(),StrokeCap.Round)
        }
    }
}
@Composable fun VoiceScreen(graph: AppGraph,systemAssistant: () -> Unit) {
    val status by graph.gemini.status.collectAsStateWithLifecycle()
    val route by graph.route.label.collectAsStateWithLifecycle()
    val transcript by graph.transcripts.collectAsStateWithLifecycle()
    val prefs by graph.preferences.collectAsStateWithLifecycle()
    Page("AI Voice","A conversation, without reaching for your phone.") {
        VoiceOrb(status)
        Text(status.state.name,style=MaterialTheme.typography.headlineSmall,modifier=Modifier.align(Alignment.CenterHorizontally))
        Text(route,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.align(Alignment.CenterHorizontally))
        status.error?.let { Group { Text(it,style=MaterialTheme.typography.bodyMedium) } }
        Button(onClick={ if(status.state in listOf(VoiceState.Idle,VoiceState.Error)) graph.activate(1) else graph.run { graph.stopSession() } },shape=CircleShape,modifier=Modifier.fillMaxWidth().height(56.dp)) {
            Text(if(status.state==VoiceState.Error) "Reconnect" else if(status.state==VoiceState.Idle) "Start Gemini Live" else "End conversation")
        }
        if(prefs.provider=="System assistant") Group {
            Text("Android chooses your configured assistant. Its voice experience is managed outside ADAPT Control.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick=systemAssistant) { Text("Open system assistant") }
        }
        if(transcript.isNotEmpty()) { SectionLabel("Conversation"); Group { transcript.takeLast(12).forEach { line ->
            Text(line.speaker,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
            Text(line.text,style=MaterialTheme.typography.bodyLarge)
        } } }
    }
}
@Composable fun NotesScreen(graph: AppGraph) {
    val draft by graph.notes.draft.collectAsStateWithLifecycle()
    val notes by graph.repository.notes.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }
    Page("Voice Notes","Catch the idea. Keep the moment.") {
        Group {
            Row(verticalAlignment=Alignment.CenterVertically) { Text(if(draft.recording) "Listening…" else "Instant voice note",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f)); Pill("${draft.durationMs/1000}s",draft.recording) }
            Waveform(draft.level,draft.recording)
            Button(onClick={ if(draft.recording) graph.run { graph.notes.stop() } else graph.activate(2) },shape=CircleShape) { Icon(Icons.Outlined.MicNone,null); Spacer(Modifier.width(6.dp)); Text(if(draft.recording) "Stop recording" else "Record a note") }
            OutlinedTextField(draft.transcript,{ graph.notes.edit(it) },placeholder={ Text("Your transcript or a typed note…") },minLines=3,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("Note","Idea","Task","Reminder","Project").forEach { category -> FilterChip(draft.category==category,{ graph.notes.category(category) },{ Text(category) }) }
            }
            draft.message?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            if(draft.audioPath!=null && !draft.recording) Row {
                TextButton(onClick={ graph.run { graph.playNote(draft.audioPath!!) } }) { Text("Play") }
                TextButton(onClick={ graph.notes.transcribe() }) { Text("Transcribe offline") }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={ graph.run { graph.saveNote() } },enabled=!draft.recording && (draft.audioPath!=null || draft.transcript.isNotBlank()),shape=CircleShape) { Text("Save note") }
                TextButton(onClick={ graph.run { graph.notes.stop(); graph.notes.reset(true) } }) { Text("Discard") }
            }
            Text("Physical double press saves automatically after silence or another double press. Basic audio notes need no cloud AI.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(search,{ search=it },label={ Text("Search notes") },leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=CircleShape)
        val filtered=notes.filter { (it.title+" "+it.rawTranscript+" "+it.category+" "+it.tags).contains(search,true) }
        if(filtered.isEmpty()) Text(if(search.isBlank()) "Your notes will appear here." else "No matching notes.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        filtered.forEach { note ->
            Group {
                DetailRow(note.title,note.category,java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT).format(java.util.Date(note.createdTime)),click={ expanded=if(expanded==note.id) null else note.id })
                if(expanded==note.id) {
                    Text(note.cleanedTranscript ?: note.rawTranscript.ifBlank { "Audio note · no transcript" },style=MaterialTheme.typography.bodyLarge)
                    Row { note.audioPath?.let { TextButton(onClick={ graph.run { graph.playNote(it) } }) { Text("Play recording") } }
                        TextButton(onClick={ graph.run { graph.repository.deleteNote(note.id); note.audioPath?.let { java.io.File(it).delete() } } }) { Text("Delete") } }
                }
            }
        }
    }
}
@Composable fun StudyScreen(graph: AppGraph) {
    val prefs by graph.preferences.collectAsStateWithLifecycle()
    val progress by graph.study.collectAsStateWithLifecycle()
    val status by graph.gemini.status.collectAsStateWithLifecycle()
    val sessions by graph.repository.studies.collectAsStateWithLifecycle()
    val transcript by graph.transcripts.collectAsStateWithLifecycle()
    Page("Study Companion","Understand more. Say it out loud.") {
        Group {
            SmallField("Topic",prefs.topic) { graph.run { graph.settings.set("topic",it) } }
            ChoiceRow("Mode",prefs.mode,listOf("Explain","Quiz","Rapid Fire","Exam","Review Weak Topics")) { graph.run { graph.settings.set("mode",it) } }
        }
        Group {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Pill(if(progress.active) progress.mode else "Voice-first",true); Text("${progress.score} / ${progress.total}",color=MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(progress.question,style=MaterialTheme.typography.headlineSmall)
            Waveform(status.level,progress.active)
            if(progress.active) Text(status.state.name,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            Button(onClick={ graph.activate(3) },shape=CircleShape,modifier=Modifier.fillMaxWidth()) { Text(if(progress.active) "Finish & save session" else "Start spoken study") }
            status.error?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if(progress.active && transcript.isNotEmpty()) { SectionLabel("Session transcript"); Group { transcript.takeLast(6).forEach { Text("${it.speaker}: ${StudyRules.display(it.text)}",style=MaterialTheme.typography.bodyMedium) } } }
        SectionLabel("Study history")
        if(sessions.isEmpty()) Group { Text("A little practice, every day.",color=MaterialTheme.colorScheme.onSurfaceVariant) }
        sessions.take(10).forEach { session -> Group {
            DetailRow(session.topic,"${session.score}/${session.total}",session.mode)
            if(session.weakTopics.isNotBlank()) Text("Revisit: ${session.weakTopics.replace('\n',',')}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            var show by remember(session.id) { mutableStateOf(false) }
            TextButton(onClick={show=!show}) { Text(if(show) "Hide transcript" else "View transcript") }
            if(show) Text(session.transcript,style=MaterialTheme.typography.bodyMedium)
        } }
    }
}
