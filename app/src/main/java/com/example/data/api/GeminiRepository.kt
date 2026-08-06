package com.example.data.api

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.data.models.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiKey: String
        get() = BuildConfig.GEMINI_API_KEY.ifEmpty { "MY_GEMINI_API_KEY" }

    suspend fun extractTextFromImage(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base64Image = bitmapToBase64(bitmap)
            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            val textPart = JSONObject().apply {
                                put("text", "Extract all visible text from this image clearly. Output ONLY the exact extracted text, maintaining line breaks where possible. If no text is visible, output 'No text recognized in image.'")
                            }
                            val imagePart = JSONObject().apply {
                                val inlineData = JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                }
                                put("inlineData", inlineData)
                            }
                            put(textPart)
                            put(imagePart)
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)
            }

            val requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url(requestUrl)
                .post(jsonBody.toString().toRequestBody(mediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val extractedText = parseGeminiResponse(responseBody)
                if (extractedText.isNotBlank()) {
                    Result.success(extractedText)
                } else {
                    Result.failure(Exception("Could not extract text from image."))
                }
            } else {
                Result.failure(Exception("OCR API Error (${response.code}): ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun translateText(text: String, targetLanguage: AppLanguage): Result<String> = withContext(Dispatchers.IO) {
        try {
            val targetLangName = when (targetLanguage) {
                AppLanguage.ENGLISH -> "English"
                AppLanguage.TELUGU -> "Telugu"
                AppLanguage.HINDI -> "Hindi"
            }

            val prompt = "Translate the following text or song lyrics accurately into $targetLangName. Output ONLY the translated text without introductory words or quotes.\n\nText:\n$text"

            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)
            }

            val requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url(requestUrl)
                .post(jsonBody.toString().toRequestBody(mediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val translatedText = parseGeminiResponse(responseBody)
                if (translatedText.isNotBlank()) {
                    Result.success(translatedText)
                } else {
                    Result.failure(Exception("Translation empty."))
                }
            } else {
                Result.failure(Exception("Translation Error (${response.code}): ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseGeminiResponse(jsonString: String): String {
        return try {
            val root = JSONObject(jsonString)
            val candidates = root.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            if (parts.length() == 0) return ""
            parts.getJSONObject(0).optString("text", "").trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Resize bitmap if too large to save memory/bandwidth
        val maxDimension = 1200
        val ratio = maxOf(bitmap.width.toFloat() / maxDimension, bitmap.height.toFloat() / maxDimension)
        val scaledBitmap = if (ratio > 1.0f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width / ratio).toInt(), (bitmap.height / ratio).toInt(), true)
        } else {
            bitmap
        }

        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
