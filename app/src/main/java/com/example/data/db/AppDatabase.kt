package com.example.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.models.CustomVoiceRecording
import com.example.data.models.SavedSpeech
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedSpeechDao {
    @Query("SELECT * FROM saved_speeches ORDER BY timestamp DESC")
    fun getAllSavedSpeeches(): Flow<List<SavedSpeech>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeech(speech: SavedSpeech): Long

    @Delete
    suspend fun deleteSpeech(speech: SavedSpeech)
}

@Dao
interface CustomVoiceDao {
    @Query("SELECT * FROM custom_voice_recordings ORDER BY timestamp DESC")
    fun getAllVoiceRecordings(): Flow<List<CustomVoiceRecording>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: CustomVoiceRecording): Long

    @Delete
    suspend fun deleteRecording(recording: CustomVoiceRecording)
}

@Database(entities = [SavedSpeech::class, CustomVoiceRecording::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedSpeechDao(): SavedSpeechDao
    abstract fun customVoiceDao(): CustomVoiceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "photo_to_speech_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
