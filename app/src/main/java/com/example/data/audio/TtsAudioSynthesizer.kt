package com.example.data.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale

class TtsAudioSynthesizer(context: Context) {
    private val appContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private val initDeferred = CompletableDeferred<Boolean>()

    init {
        textToSpeech = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                initDeferred.complete(true)
            } else {
                Log.e(TAG, "Failed to initialize Android TextToSpeech, status: $status")
                initDeferred.complete(false)
            }
        }
    }

    suspend fun awaitInitialization(): Boolean {
        return withTimeoutOrNull(5000) {
            initDeferred.await()
        } ?: false
    }

    /**
     * Synthesizes slide verbatim text directly into an audio WAV/OGG file.
     * Guaranteed: ONLY the text passed into it is spoken.
     */
    suspend fun synthesizeTextToFile(
        text: String,
        outputFile: File,
        languageCode: String,
        speechRate: Float = 1.0f,
        speechPitch: Float = 1.0f
    ): Boolean = withContext(Dispatchers.IO) {
        val ready = awaitInitialization()
        val tts = textToSpeech
        if (!ready || tts == null) {
            Log.e(TAG, "TTS not ready for file synthesis")
            return@withContext false
        }

        val locale = getLocaleForCode(languageCode)
        val langResult = tts.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Locale $locale not fully supported, falling back to default")
            tts.setLanguage(Locale.getDefault())
        }

        tts.setSpeechRate(speechRate.coerceIn(0.7f, 1.5f))
        tts.setPitch(speechPitch.coerceIn(0.7f, 1.4f))

        val utteranceId = "utterance_${System.currentTimeMillis()}"
        val completionDeferred = CompletableDeferred<Boolean>()

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS audio synthesis started: $utteranceId")
            }

            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    completionDeferred.complete(true)
                }
            }

            override fun onError(id: String?) {
                Log.e(TAG, "TTS error on utterance: $id")
                if (id == utteranceId) {
                    completionDeferred.complete(false)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?, errorCode: Int) {
                Log.e(TAG, "TTS error $errorCode on utterance: $id")
                if (id == utteranceId) {
                    completionDeferred.complete(false)
                }
            }
        })

        val params = Bundle()
        val result = tts.synthesizeToFile(text, params, outputFile, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "synthesizeToFile returned failure code: $result")
            return@withContext false
        }

        // Wait up to 30 seconds for synthesis to complete
        val success = withTimeoutOrNull(30_000) {
            completionDeferred.await()
        } ?: false

        return@withContext success && outputFile.exists() && outputFile.length() > 0
    }

    /**
     * Speaks text immediately for live preview.
     */
    suspend fun speakText(
        text: String,
        languageCode: String,
        speechRate: Float = 1.0f,
        speechPitch: Float = 1.0f,
        onDone: () -> Unit = {}
    ) = withContext(Dispatchers.Main) {
        val ready = awaitInitialization()
        val tts = textToSpeech ?: return@withContext
        if (!ready) return@withContext

        val locale = getLocaleForCode(languageCode)
        tts.setLanguage(locale)
        tts.setSpeechRate(speechRate)
        tts.setPitch(speechPitch)

        val utteranceId = "speak_${System.currentTimeMillis()}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) onDone()
            }
            override fun onError(id: String?) {
                if (id == utteranceId) onDone()
            }
        })

        val params = Bundle()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stopSpeaking() {
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }

    fun shutdown() {
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }

    private fun getLocaleForCode(code: String): Locale {
        return when (code.lowercase()) {
            "ar" -> Locale.forLanguageTag("ar-SA")
            "en" -> Locale.ENGLISH
            "fr" -> Locale.FRENCH
            "es" -> Locale.forLanguageTag("es-ES")
            "de" -> Locale.GERMAN
            "tr" -> Locale.forLanguageTag("tr-TR")
            "ur" -> Locale.forLanguageTag("ur-PK")
            "it" -> Locale.ITALIAN
            "ru" -> Locale.forLanguageTag("ru-RU")
            "zh" -> Locale.CHINESE
            else -> Locale.forLanguageTag(code)
        }
    }

    companion object {
        private const val TAG = "TtsSynthesizer"
    }
}
