package com.example.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class VoiceGender {
    MALE,
    FEMALE,
    KID
}

enum class AppLanguage(val code: String, val displayName: String, val localeTag: String) {
    ENGLISH("en", "English", "en-US"),
    TELUGU("te", "Telugu", "te-IN"),
    HINDI("hi", "Hindi", "hi-IN")
}

@Entity(tableName = "saved_speeches")
data class SavedSpeech(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val originalText: String,
    val translatedText: String? = null,
    val languageCode: String,
    val voiceType: String,
    val audioFilePath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_voice_recordings")
data class CustomVoiceRecording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val durationSeconds: Int,
    val timestamp: Long = System.currentTimeMillis()
)
