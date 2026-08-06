package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.data.models.AppLanguage
import com.example.data.models.VoiceGender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

data class HighlightRange(
    val start: Int = -1,
    val end: Int = -1
)

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _highlightRange = MutableStateFlow(HighlightRange())
    val highlightRange: StateFlow<HighlightRange> = _highlightRange.asStateFlow()

    private val _downloadStatus = MutableStateFlow<String?>(null)
    val downloadStatus: StateFlow<String?> = _downloadStatus.asStateFlow()

    private var currentText: String = ""
    private var currentSubtextOffset: Int = 0
    private var currentLanguage: AppLanguage = AppLanguage.ENGLISH
    private var currentVoiceGender: VoiceGender = VoiceGender.FEMALE
    private var customPitch: Float = 1.0f
    private var customRate: Float = 1.0f

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupProgressListener()
            setLanguage(currentLanguage)
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                _highlightRange.value = HighlightRange(-1, -1)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                _highlightRange.value = HighlightRange(-1, -1)
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                super.onRangeStart(utteranceId, start, end, frame)
                val absoluteStart = start + currentSubtextOffset
                val absoluteEnd = end + currentSubtextOffset
                _highlightRange.value = HighlightRange(absoluteStart, absoluteEnd)
            }
        })
    }

    fun setLanguage(language: AppLanguage) {
        currentLanguage = language
        if (!isInitialized) return

        val locale = when (language) {
            AppLanguage.ENGLISH -> Locale.US
            AppLanguage.TELUGU -> Locale("te", "IN")
            AppLanguage.HINDI -> Locale("hi", "IN")
        }

        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.US)
        }
        applyVoiceSettings()
    }

    fun setVoiceGender(gender: VoiceGender) {
        currentVoiceGender = gender
        applyVoiceSettings()
    }

    fun setSpeedAndPitch(speed: Float, pitch: Float) {
        customRate = speed
        customPitch = pitch
        applyVoiceSettings()
    }

    private fun applyVoiceSettings() {
        if (!isInitialized) return

        val (genderPitch, genderRate) = when (currentVoiceGender) {
            VoiceGender.MALE -> Pair(0.85f * customPitch, 0.98f * customRate)
            VoiceGender.FEMALE -> Pair(1.10f * customPitch, 1.02f * customRate)
            VoiceGender.KID -> Pair(1.58f * customPitch, 1.15f * customRate)
        }

        tts?.setPitch(genderPitch)
        tts?.setSpeechRate(genderRate)

        // Try selecting matching voice features if available on device
        try {
            val voices = tts?.voices ?: return
            val targetLanguageCode = when (currentLanguage) {
                AppLanguage.ENGLISH -> "en"
                AppLanguage.TELUGU -> "te"
                AppLanguage.HINDI -> "hi"
            }

            val matchingVoice = voices.find { voice ->
                voice.locale.language.equals(targetLanguageCode, ignoreCase = true) &&
                        when (currentVoiceGender) {
                            VoiceGender.MALE -> voice.name.contains("male", ignoreCase = true) || voice.name.contains("man", ignoreCase = true)
                            VoiceGender.FEMALE -> voice.name.contains("female", ignoreCase = true) || voice.name.contains("woman", ignoreCase = true)
                            VoiceGender.KID -> voice.name.contains("kid", ignoreCase = true) || voice.name.contains("child", ignoreCase = true)
                        }
            } ?: voices.find { it.locale.language.equals(targetLanguageCode, ignoreCase = true) }

            if (matchingVoice != null) {
                tts?.voice = matchingVoice
            }
        } catch (e: Exception) {
            // Ignore voice lookup errors fallback to default
        }
    }

    fun speak(text: String, startFromIndex: Int = 0) {
        if (!isInitialized || text.isBlank()) return

        stop()

        currentText = text
        currentSubtextOffset = startFromIndex.coerceIn(0, text.length)
        val textToSpeak = if (currentSubtextOffset > 0 && currentSubtextOffset < text.length) {
            text.substring(currentSubtextOffset)
        } else {
            currentSubtextOffset = 0
            text
        }

        applyVoiceSettings()

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utterance_${System.currentTimeMillis()}")
        }

        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, "utterance_${System.currentTimeMillis()}")
    }

    fun stop() {
        if (tts?.isSpeaking == true) {
            tts?.stop()
        }
        _isSpeaking.value = false
        _highlightRange.value = HighlightRange(-1, -1)
    }

    fun synthesizeToFile(text: String, onResult: (Boolean, String?) -> Unit) {
        if (!isInitialized || text.isBlank()) {
            onResult(false, "TTS engine not ready or text is empty.")
            return
        }

        try {
            val fileName = "PhotoToSpeech_${System.currentTimeMillis()}.wav"
            val tempFile = File(context.cacheDir, fileName)

            applyVoiceSettings()

            val utteranceId = "synth_${System.currentTimeMillis()}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}

                override fun onDone(id: String?) {
                    if (id == utteranceId) {
                        // Copy to public downloads folder or MediaStore
                        val publicPath = saveToPublicDownloads(tempFile, "PhotoToSpeech_${System.currentTimeMillis()}.mp3")
                        _downloadStatus.value = "Downloaded MP3 to Downloads: $publicPath"
                        onResult(true, publicPath)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId) {
                        onResult(false, "Audio synthesis failed.")
                    }
                }
            })

            val result = tts?.synthesizeToFile(text, params, tempFile, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                setupProgressListener()
                onResult(false, "Synthesize failed to start.")
            }
        } catch (e: Exception) {
            setupProgressListener()
            onResult(false, "Error: ${e.localizedMessage}")
        }
    }

    private fun saveToPublicDownloads(sourceFile: File, destFileName: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, destFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PhotoToSpeech")
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        FileInputStream(sourceFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    "Downloads/PhotoToSpeech/$destFileName"
                } else {
                    sourceFile.absolutePath
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetFolder = File(downloadsDir, "PhotoToSpeech")
                if (!targetFolder.exists()) targetFolder.mkdirs()

                val destFile = File(targetFolder, destFileName)
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                destFile.absolutePath
            }
        } catch (e: Exception) {
            sourceFile.absolutePath
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
