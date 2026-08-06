package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.HighlightRange
import com.example.audio.TtsManager
import com.example.audio.VoiceRecorderManager
import com.example.data.api.GeminiRepository
import com.example.data.db.AppDatabase
import com.example.data.models.AppLanguage
import com.example.data.models.CustomVoiceRecording
import com.example.data.models.SavedSpeech
import com.example.data.models.VoiceGender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val savedSpeechDao = db.savedSpeechDao()
    private val customVoiceDao = db.customVoiceDao()

    val ttsManager = TtsManager(application)
    val recorderManager = VoiceRecorderManager(application)
    private val geminiRepository = GeminiRepository()

    // State
    private val _textToSpeak = MutableStateFlow("Welcome to Photo to Speech! Tap an image, capture a photo, or type text here to listen in Male, Female, or Kid voices.")
    val textToSpeak: StateFlow<String> = _textToSpeak.asStateFlow()

    private val _translatedText = MutableStateFlow<String?>(null)
    val translatedText: StateFlow<String?> = _translatedText.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(AppLanguage.ENGLISH)
    val selectedLanguage: StateFlow<AppLanguage> = _selectedLanguage.asStateFlow()

    private val _selectedVoiceGender = MutableStateFlow(VoiceGender.FEMALE)
    val selectedVoiceGender: StateFlow<VoiceGender> = _selectedVoiceGender.asStateFlow()

    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    private val _isOcrLoading = MutableStateFlow(false)
    val isOcrLoading: StateFlow<Boolean> = _isOcrLoading.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val isSpeaking: StateFlow<Boolean> = ttsManager.isSpeaking
    val highlightRange: StateFlow<HighlightRange> = ttsManager.highlightRange
    val downloadStatus: StateFlow<String?> = ttsManager.downloadStatus

    val isRecording: StateFlow<Boolean> = recorderManager.isRecording
    val isPlayingRecording: StateFlow<Boolean> = recorderManager.isPlaying

    val savedSpeeches = savedSpeechDao.getAllSavedSpeeches()
    val customVoiceRecordings = customVoiceDao.getAllVoiceRecordings()

    fun updateText(newText: String) {
        _textToSpeak.value = newText
        _translatedText.value = null
    }

    fun setLanguage(language: AppLanguage) {
        _selectedLanguage.value = language
        ttsManager.setLanguage(language)
    }

    fun setVoiceGender(gender: VoiceGender) {
        _selectedVoiceGender.value = gender
        ttsManager.setVoiceGender(gender)
    }

    fun setPitchAndSpeed(pitchValue: Float, speedValue: Float) {
        _pitch.value = pitchValue
        _speed.value = speedValue
        ttsManager.setSpeedAndPitch(speedValue, pitchValue)
    }

    fun processPhotoForSpeech(bitmap: Bitmap) {
        _capturedBitmap.value = bitmap
        _isOcrLoading.value = true
        _userMessage.value = "Scanning text from photo..."

        viewModelScope.launch {
            val result = geminiRepository.extractTextFromImage(bitmap)
            _isOcrLoading.value = false
            result.onSuccess { extractedText ->
                _textToSpeak.value = extractedText
                _userMessage.value = "Text extracted successfully!"
                // Speak extracted text automatically
                ttsManager.speak(extractedText, 0)
            }.onFailure { error ->
                _userMessage.value = "OCR Note: ${error.localizedMessage ?: "Could not extract text. Enter text manually."}"
            }
        }
    }

    fun translateTextTo(targetLanguage: AppLanguage) {
        val current = _textToSpeak.value
        if (current.isBlank()) {
            _userMessage.value = "Please enter text to translate."
            return
        }

        _isTranslating.value = true
        _userMessage.value = "Translating to ${targetLanguage.displayName}..."

        viewModelScope.launch {
            val result = geminiRepository.translateText(current, targetLanguage)
            _isTranslating.value = false
            result.onSuccess { translated ->
                _translatedText.value = translated
                _selectedLanguage.value = targetLanguage
                ttsManager.setLanguage(targetLanguage)
                _userMessage.value = "Translated successfully!"
            }.onFailure { error ->
                _userMessage.value = "Translation failed: ${error.localizedMessage}"
            }
        }
    }

    fun speakCurrentText(startFromIndex: Int = 0) {
        val text = _translatedText.value ?: _textToSpeak.value
        ttsManager.speak(text, startFromIndex)
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun downloadSpeechAsAudio() {
        val text = _translatedText.value ?: _textToSpeak.value
        if (text.isBlank()) {
            _userMessage.value = "No text to generate audio."
            return
        }

        _userMessage.value = "Synthesizing MP3 audio download..."
        ttsManager.synthesizeToFile(text) { success, filePath ->
            if (success && filePath != null) {
                _userMessage.value = "Audio saved to Downloads folder!"
                saveSpeechToDb(filePath)
            } else {
                _userMessage.value = "Failed to download audio file."
            }
        }
    }

    private fun saveSpeechToDb(audioPath: String) {
        viewModelScope.launch {
            val speech = SavedSpeech(
                title = (_textToSpeak.value.take(30) + "..."),
                originalText = _textToSpeak.value,
                translatedText = _translatedText.value,
                languageCode = _selectedLanguage.value.code,
                voiceType = _selectedVoiceGender.value.name,
                audioFilePath = audioPath
            )
            savedSpeechDao.insertSpeech(speech)
        }
    }

    fun deleteSavedSpeech(speech: SavedSpeech) {
        viewModelScope.launch {
            savedSpeechDao.deleteSpeech(speech)
        }
    }

    fun startVoiceRecording(title: String) {
        val file = recorderManager.startRecording(title)
        if (file != null) {
            _userMessage.value = "Recording started... Speak into microphone."
        } else {
            _userMessage.value = "Failed to start recording. Grant mic permissions."
        }
    }

    fun stopVoiceRecording(title: String) {
        val file = recorderManager.stopRecording()
        if (file != null) {
            _userMessage.value = "Voice recorded successfully!"
            viewModelScope.launch {
                val recording = CustomVoiceRecording(
                    title = if (title.isBlank()) "My Voice Clip" else title,
                    filePath = file.absolutePath,
                    durationSeconds = 5
                )
                customVoiceDao.insertRecording(recording)
            }
        } else {
            _userMessage.value = "Recording stopped."
        }
    }

    fun playRecording(recording: CustomVoiceRecording) {
        recorderManager.playAudio(recording.filePath)
    }

    fun deleteRecording(recording: CustomVoiceRecording) {
        viewModelScope.launch {
            customVoiceDao.deleteRecording(recording)
            try {
                File(recording.filePath).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
        recorderManager.stopPlaying()
    }
}
