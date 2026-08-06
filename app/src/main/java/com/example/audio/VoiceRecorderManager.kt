package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class VoiceRecorderManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _recordingTimer = MutableStateFlow(0)
    val recordingTimer: StateFlow<Int> = _recordingTimer.asStateFlow()

    private var currentOutputFile: File? = null

    fun startRecording(title: String = "My_Voice"): File? {
        stopPlaying()
        try {
            val fileName = "Voice_${title}_${System.currentTimeMillis()}.m4a"
            val outputDir = context.getExternalFilesDir("CustomVoices") ?: context.filesDir
            val outputFile = File(outputDir, fileName)
            currentOutputFile = outputFile

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            _isRecording.value = true
            return outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            _isRecording.value = false
            return null
        }
    }

    fun stopRecording(): File? {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            _isRecording.value = false
            return currentOutputFile
        } catch (e: Exception) {
            e.printStackTrace()
            mediaRecorder = null
            _isRecording.value = false
            return null
        }
    }

    fun playAudio(filePath: String, pitch: Float = 1.0f) {
        stopPlaying()
        try {
            val file = File(filePath)
            if (!file.exists()) return

            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    playbackParams = playbackParams.setPitch(pitch)
                }
                start()
                setOnCompletionListener {
                    _isPlaying.value = false
                }
            }
            _isPlaying.value = true
        } catch (e: Exception) {
            e.printStackTrace()
            _isPlaying.value = false
        }
    }

    fun stopPlaying() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
            _isPlaying.value = false
        } catch (e: Exception) {
            e.printStackTrace()
            _isPlaying.value = false
        }
    }
}
