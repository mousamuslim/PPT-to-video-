package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presentations")
data class PresentationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val fileName: String,
    val slideCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val originalLanguage: String = "ar",
    val currentLanguage: String = "ar",
    val videoPath: String? = null,
    val audioPath: String? = null,
    val durationSeconds: Float = 0f
)

@Entity(tableName = "slides")
data class SlideEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val presentationId: Long,
    val slideIndex: Int,
    val title: String,
    val bulletPointsJson: String, // JSON array of bullet strings
    val fullVerbatimText: String, // Exactly what's printed on slide
    val imagePath: String? = null,
    val durationSeconds: Float = 5.0f
)

@Entity(tableName = "language_tracks")
data class LanguageTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val presentationId: Long,
    val languageCode: String,
    val languageName: String,
    val translatedSlidesJson: String, // Array of translated verbatim text per slide
    val videoPath: String? = null,
    val audioPath: String? = null,
    val durationSeconds: Float = 0f,
    val isReady: Boolean = false
)
