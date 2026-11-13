package com.planavora.app

import android.content.Context
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class VoiceCapabilityTest {
    @Test fun checksInstalledLanguagesAndCompletesAnEnglishUtterance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val initialized = CountDownLatch(1)
        val initStatus = AtomicInteger(TextToSpeech.ERROR)
        val recognizer = SpeechRecognizer.isRecognitionAvailable(context)
        println("PLANAVORA_VOICE recognition_service=$recognizer")
        val voice = TextToSpeech(context) { status -> initStatus.set(status); initialized.countDown() }
        try {
            assertTrue("Speech engine initialization timed out", initialized.await(15, TimeUnit.SECONDS))
            assertEquals(TextToSpeech.SUCCESS, initStatus.get())
            val tags = listOf("en-GB", "en-US", "en-AU", "es-ES", "es-419", "fr-FR", "fr-CA", "de-DE",
                "it-IT", "pt-BR", "pt-PT", "nl-NL", "pl-PL", "sv-SE", "ja-JP", "hi-IN", "ar", "tr-TR", "id-ID")
            for (tag in tags) {
                val status = voice.isLanguageAvailable(Locale.forLanguageTag(tag))
                println("PLANAVORA_VOICE tts_locale=$tag availability=$status")
            }
            val language = voice.setLanguage(Locale.UK)
            assertTrue("The installed engine could not use British English", language >= TextToSpeech.LANG_AVAILABLE)
            val playback = CountDownLatch(1)
            val result = AtomicInteger(TextToSpeech.ERROR)
            voice.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) { result.set(TextToSpeech.SUCCESS); playback.countDown() }
                @Deprecated("Android callback") override fun onError(id: String?) { playback.countDown() }
            })
            assertEquals(TextToSpeech.SUCCESS, voice.speak("Your event budget is ready.", TextToSpeech.QUEUE_FLUSH, null, "planavora-qa"))
            assertTrue("No playback callback", playback.await(15, TimeUnit.SECONDS))
            assertEquals(TextToSpeech.SUCCESS, result.get())
        } finally { voice.shutdown() }
    }
}
