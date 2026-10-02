package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PresentationDao {
    @Query("SELECT * FROM presentations ORDER BY createdAt DESC")
    fun getAllPresentations(): Flow<List<PresentationEntity>>

    @Query("SELECT * FROM presentations WHERE id = :id LIMIT 1")
    suspend fun getPresentationById(id: Long): PresentationEntity?

    @Query("SELECT * FROM presentations WHERE id = :id LIMIT 1")
    fun observePresentationById(id: Long): Flow<PresentationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresentation(presentation: PresentationEntity): Long

    @Update
    suspend fun updatePresentation(presentation: PresentationEntity)

    @Query("DELETE FROM presentations WHERE id = :id")
    suspend fun deletePresentation(id: Long)

    // Slides
    @Query("SELECT * FROM slides WHERE presentationId = :presentationId ORDER BY slideIndex ASC")
    fun getSlidesForPresentation(presentationId: Long): Flow<List<SlideEntity>>

    @Query("SELECT * FROM slides WHERE presentationId = :presentationId ORDER BY slideIndex ASC")
    suspend fun getSlidesList(presentationId: Long): List<SlideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlides(slides: List<SlideEntity>)

    @Query("DELETE FROM slides WHERE presentationId = :presentationId")
    suspend fun deleteSlidesForPresentation(presentationId: Long)

    @Query("UPDATE slides SET title = :title, fullVerbatimText = :fullVerbatimText, bulletPointsJson = :bulletPointsJson, durationSeconds = :durationSeconds WHERE presentationId = :presentationId AND slideIndex = :slideIndex")
    suspend fun updateSlideContent(presentationId: Long, slideIndex: Int, title: String, fullVerbatimText: String, bulletPointsJson: String, durationSeconds: Float)

    // Language tracks
    @Query("SELECT * FROM language_tracks WHERE presentationId = :presentationId")
    fun getLanguageTracks(presentationId: Long): Flow<List<LanguageTrackEntity>>

    @Query("SELECT * FROM language_tracks WHERE presentationId = :presentationId AND languageCode = :languageCode LIMIT 1")
    suspend fun getLanguageTrack(presentationId: Long, languageCode: String): LanguageTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLanguageTrack(track: LanguageTrackEntity): Long

    @Update
    suspend fun updateLanguageTrack(track: LanguageTrackEntity)
}
