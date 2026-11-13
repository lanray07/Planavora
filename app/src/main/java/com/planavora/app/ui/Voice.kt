package com.planavora.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource as s
import androidx.compose.ui.unit.dp
import com.planavora.app.R
import com.planavora.app.data.EventRow
import com.planavora.app.domain.*
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoicePage(event: EventRow, details: EventDetails, rows: List<Item>, money: Money, spoken: Boolean,
              navigate: (Section) -> Unit, draftTask: (String) -> Unit, draftExpense: (String, Long) -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    var transcript by rememberSaveable { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Int?>(null) }
    var reply by rememberSaveable { mutableStateOf("") }
    var destination by remember { mutableStateOf<Section?>(null) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var speaker by remember { mutableStateOf<TextToSpeech?>(null) }
    var ready by remember { mutableStateOf(false) }
    val balance = stringArrayResource(R.array.voice_balance)
    val rsvp = stringArrayResource(R.array.voice_rsvp)
    val tasks = stringArrayResource(R.array.voice_tasks)
    val payments = stringArrayResource(R.array.voice_payments)
    val start = stringArrayResource(R.array.voice_start)
    val addTask = stringArrayResource(R.array.voice_add_task)
    val addExpense = stringArrayResource(R.array.voice_add_expense)
    DisposableEffect(locale, spoken) {
        val tts = if (spoken) TextToSpeech(context) { result -> ready = result == TextToSpeech.SUCCESS } else null
        speaker = tts
        onDispose {
            tts?.stop()
            tts?.shutdown()
            ready = false
        }
    }
    DisposableEffect(Unit) {
        onDispose { recognizer?.destroy() }
    }
    fun listen() {
        if (!available || listening) return
        runCatching {
            val service = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
            service.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { status = R.string.transcribing }
                override fun onError(error: Int) {
                    listening = false
                    status = if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) R.string.mic_denied else R.string.voice_error
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    val result = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (result.isNullOrBlank()) status = R.string.voice_error
                    else { transcript = result.take(4000); status = null }
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            listening = true
            status = R.string.listening
            service.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1))
        }.onFailure { listening = false; status = R.string.voice_error }
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) listen() else status = R.string.mic_denied
    }
    fun say(text: String) {
        reply = text
        if (spoken) {
            val engine = speaker
            if (ready && engine != null && engine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE) {
                if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, event.id) == TextToSpeech.ERROR) status = R.string.spoken_unavailable
            } else status = R.string.spoken_unavailable
        }
    }
    fun interpret() {
        val value = transcript.trim().trimEnd('?', '.', '!', '؟', '。')
        val normalized = value.lowercase(locale)
        val totals = budget(details, rows)
        val number = NumberFormat.getIntegerInstance(locale)
        reply = ""
        destination = null
        status = null
        val task = addTask.firstOrNull { normalized.startsWith(it.lowercase(locale) + " ") }
        val expense = addExpense.firstOrNull { normalized.startsWith(it.lowercase(locale) + " ") }
        when {
            balance.any { normalized == it.lowercase(locale) } -> say(context.getString(R.string.choice_value, context.getString(R.string.remaining), money.display(totals.remaining)))
            rsvp.any { normalized == it.lowercase(locale) } -> {
                destination = Section.GUEST
                val waiting = rows.count { (it.detail as? Details.Guest)?.let { g -> g.invited && g.rsvp in listOf(Rsvp.PENDING, Rsvp.MAYBE) } == true }
                say(context.getString(R.string.choice_value, context.getString(R.string.pending_rsvps), number.format(waiting)))
            }
            tasks.any { normalized == it.lowercase(locale) } -> {
                destination = Section.TASK
                say(context.getString(R.string.choice_value, context.getString(R.string.outstanding_tasks),
                    number.format(rows.count { (it.detail as? Details.Task)?.complete == false })))
            }
            payments.any { normalized == it.lowercase(locale) } -> {
                destination = Section.PAYMENT
                say(context.getString(R.string.choice_value, context.getString(R.string.vendor_balance), money.display(totals.vendorBalance)))
            }
            start.any { normalized == it.lowercase(locale) } -> {
                val time = localTime(details.time, locale)
                say(if (time.isBlank()) context.getString(R.string.no_event_time) else time)
            }
            task != null -> draftTask(value.drop(task.length).trim())
            expense != null -> {
                val input = value.drop(expense.length).trim().split(Regex("\\s+"), limit = 2)
                val amount = money.parse(input.firstOrNull().orEmpty())
                if (amount == null || amount == 0L || input.size != 2) status = R.string.voice_unrecognised
                else draftExpense(input.last(), amount)
            }
            else -> status = R.string.voice_unrecognised
        }
    }
    Page {
        Panel {
            Text(s(R.string.voice), style = MaterialTheme.typography.headlineSmall)
            Text(s(R.string.voice_privacy))
            if (!available) Text(s(R.string.voice_unavailable))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = available, onClick = {
                    if (listening) recognizer?.stopListening()
                    else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
                    else microphone.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text(s(if (listening) R.string.stop_listening else R.string.push_to_talk)) }
            }
            Field(transcript, R.string.command, single = false) { transcript = it }
            Button(enabled = transcript.isNotBlank() && !listening, onClick = ::interpret) { Text(s(R.string.review_command)) }
            status?.let { Text(s(it)) }
            if (reply.isNotBlank()) Text(reply)
            destination?.let { section -> OutlinedButton(onClick = { navigate(section) }) { Text(s(section.label)) } }
        }
        Panel {
            Text(s(R.string.example_commands), style = MaterialTheme.typography.titleMedium)
            listOf(balance, rsvp, tasks, payments, start).mapNotNull { it.firstOrNull() }.forEach { example ->
                TextButton(onClick = { transcript = example }) { Text(example) }
            }
        }
    }
}
