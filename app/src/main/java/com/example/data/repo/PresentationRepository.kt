package com.example.data.repo

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.ai.AiTranslationService
import com.example.data.audio.TtsAudioSynthesizer
import com.example.data.db.LanguageTrackEntity
import com.example.data.db.PresentationDao
import com.example.data.db.PresentationEntity
import com.example.data.db.SlideEntity
import com.example.data.model.Presentation
import com.example.data.model.Slide
import com.example.data.model.SlideVideoTheme
import com.example.data.model.SupportedLanguage
import com.example.data.pptx.PptxParser
import com.example.data.pptx.SamplePresentations
import com.example.data.video.SlideVideoEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

class PresentationRepository(
    private val context: Context,
    private val dao: PresentationDao,
    private val ttsSynthesizer: TtsAudioSynthesizer
) {
    private val TAG = "PresentationRepo"

    val allPresentations: Flow<List<Presentation>> = dao.getAllPresentations().map { entities ->
        entities.map { entity ->
            Presentation(
                id = entity.id,
                title = entity.title,
                fileName = entity.fileName,
                slideCount = entity.slideCount,
                createdAt = entity.createdAt,
                originalLanguage = entity.originalLanguage,
                currentLanguage = entity.currentLanguage,
                videoPath = entity.videoPath,
                audioPath = entity.audioPath
            )
        }
    }.flowOn(Dispatchers.IO)

    fun observePresentation(id: Long): Flow<Presentation?> = dao.observePresentationById(id).map { entity ->
        entity?.let {
            Presentation(
                id = it.id,
                title = it.title,
                fileName = it.fileName,
                slideCount = it.slideCount,
                createdAt = it.createdAt,
                originalLanguage = it.originalLanguage,
                currentLanguage = it.currentLanguage,
                videoPath = it.videoPath,
                audioPath = it.audioPath
            )
        }
    }.flowOn(Dispatchers.IO)

    fun observeSlides(presentationId: Long): Flow<List<Slide>> = dao.getSlidesForPresentation(presentationId).map { entities ->
        entities.map { entity ->
            val bullets = try {
                val jsonArr = JSONArray(entity.bulletPointsJson)
                (0 until jsonArr.length()).map { jsonArr.getString(it) }
            } catch (e: Exception) {
                emptyList()
            }

            Slide(
                slideIndex = entity.slideIndex,
                title = entity.title,
                bulletPoints = bullets,
                fullVerbatimText = entity.fullVerbatimText,
                imagePath = entity.imagePath,
                durationSeconds = entity.durationSeconds
            )
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Imports a presentation from a PowerPoint URI (shared from another app or picked in-app).
     */
    suspend fun importFromPptx(uri: Uri, originalFileName: String?): Long = withContext(Dispatchers.IO) {
        val parsed = PptxParser.parsePptx(context, uri, originalFileName)
        val presentationEntity = PresentationEntity(
            title = parsed.title,
            fileName = parsed.fileName,
            slideCount = parsed.slides.size,
            originalLanguage = "ar",
            currentLanguage = "ar"
        )
        val id = dao.insertPresentation(presentationEntity)

        val slideEntities = parsed.slides.map { slide ->
            val bulletsJson = JSONArray(slide.bulletPoints).toString()
            SlideEntity(
                presentationId = id,
                slideIndex = slide.slideIndex,
                title = slide.title,
                bulletPointsJson = bulletsJson,
                fullVerbatimText = slide.fullVerbatimText,
                imagePath = slide.imagePath,
                durationSeconds = slide.durationSeconds
            )
        }
        dao.insertSlides(slideEntities)
        id
    }

    /**
     * Seeds initial demo presentations if the database is empty.
     */
    suspend fun seedInitialPresentationsIfEmpty() = withContext(Dispatchers.IO) {
        val count = dao.getAllPresentations().first().size
        if (count == 0) {
            for (sample in SamplePresentations.ALL_SAMPLES) {
                val entity = PresentationEntity(
                    title = sample.title,
                    fileName = sample.fileName,
                    slideCount = sample.slides.size,
                    originalLanguage = sample.originalLanguage,
                    currentLanguage = sample.originalLanguage
                )
                val newId = dao.insertPresentation(entity)
                val slideEntities = sample.slides.map { slide ->
                    SlideEntity(
                        presentationId = newId,
                        slideIndex = slide.slideIndex,
                        title = slide.title,
                        bulletPointsJson = JSONArray(slide.bulletPoints).toString(),
                        fullVerbatimText = slide.fullVerbatimText,
                        imagePath = slide.imagePath,
                        durationSeconds = slide.durationSeconds
                    )
                }
                dao.insertSlides(slideEntities)
            }
        }
    }

    /**
     * Updates slide text edited by the user before generation or download.
     */
    suspend fun updateSlideText(
        presentationId: Long,
        slideIndex: Int,
        newTitle: String,
        newVerbatimText: String
    ) = withContext(Dispatchers.IO) {
        val cleanTitle = newTitle.trim()
        val cleanVerbatim = newVerbatimText.trim()
        val lines = cleanVerbatim.lines().map { it.trim() }.filter { it.isNotBlank() }
        val bullets = lines.filter { it != cleanTitle }
        val bulletsJson = JSONArray(bullets).toString()
        val duration = PptxParser.calculateSlideDuration(cleanVerbatim)

        dao.updateSlideContent(
            presentationId = presentationId,
            slideIndex = slideIndex,
            title = cleanTitle,
            fullVerbatimText = cleanVerbatim,
            bulletPointsJson = bulletsJson,
            durationSeconds = duration
        )
    }

    /**
     * Translates and generates video + audio for a chosen target language or auto-detected language.
     * Guaranteed: ONLY the text on the slides is spoken.
     */
    suspend fun generateVideoForLanguage(
        presentationId: Long,
        targetLanguageCode: String,
        theme: SlideVideoTheme = SlideVideoTheme.DARK_INDIGO,
        speechRate: Float = 1.0f,
        speechPitch: Float = 1.0f,
        onProgress: (Float, String) -> Unit
    ): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val presentation = dao.getPresentationById(presentationId)
            ?: return@withContext Pair(null, null)

        val slides = dao.getSlidesList(presentationId)
        if (slides.isEmpty()) return@withContext Pair(null, null)

        // Automatic Language Detection if auto is chosen
        val sampleText = slides.map { it.fullVerbatimText }.joinToString(" ")
        val effectiveLangCode = com.example.data.ai.LanguageDetector.resolveEffectiveLanguageCode(
            targetLanguageCode,
            sampleText
        )
        val targetLang = SupportedLanguage.fromCode(effectiveLangCode)
        val isSameLanguage = presentation.originalLanguage.equals(effectiveLangCode, ignoreCase = true) || targetLanguageCode == "auto"

        onProgress(0.10f, "تجهيز نصوص الشرائح الحرفية (لغة الإلقاء: ${targetLang.displayName})...")

        // Step 1: Translate verbatim slide text if target language is different
        val translatedSlidesMap = mutableMapOf<Int, String>()
        val slideDurations = mutableListOf<Float>()

        val videosDir = File(context.filesDir, "slide_videos").apply { mkdirs() }
        val audioDir = File(context.filesDir, "slide_audio").apply { mkdirs() }

        val videoOutputFile = File(videosDir, "presentation_${presentationId}_${effectiveLangCode}.mp4")
        val audioOutputFile = File(audioDir, "audio_${presentationId}_${effectiveLangCode}.wav")

        val totalSlidesCount = slides.size
        for ((idx, slide) in slides.withIndex()) {
            val progressVal = 0.10f + (0.25f * (idx.toFloat() / totalSlidesCount))
            val slideText = if (isSameLanguage) {
                slide.fullVerbatimText
            } else {
                onProgress(progressVal, "ترجمة شريحة ${idx + 1} إلى ${targetLang.displayName} (حرفياً فقط)...")
                AiTranslationService.translateSlideVerbatim(
                    slideText = slide.fullVerbatimText,
                    sourceLanguage = presentation.originalLanguage,
                    targetLanguageName = targetLang.displayName,
                    targetLanguageCode = targetLanguageCode
                )
            }

            translatedSlidesMap[slide.slideIndex] = slideText
            val calculatedDuration = PptxParser.calculateSlideDuration(slideText)
            slideDurations.add(calculatedDuration)
        }

        // Step 2: Synthesize audio narration for the entire presentation verbatim
        onProgress(0.40f, "توليد النطق الصوتي بالذكاء الاصطناعي...")
        val fullNarrationText = slides.joinToString("\n\n") { slide ->
            translatedSlidesMap[slide.slideIndex] ?: slide.fullVerbatimText
        }

        val audioSuccess = ttsSynthesizer.synthesizeTextToFile(
            text = fullNarrationText,
            outputFile = audioOutputFile,
            languageCode = effectiveLangCode,
            speechRate = speechRate,
            speechPitch = speechPitch
        )

        // Step 3: Render slides & encode MP4 video
        val mappedSlides = slides.map { entity ->
            val bullets = try {
                val jsonArr = JSONArray(entity.bulletPointsJson)
                (0 until jsonArr.length()).map { jsonArr.getString(it) }
            } catch (e: Exception) {
                emptyList()
            }
            Slide(
                slideIndex = entity.slideIndex,
                title = entity.title,
                bulletPoints = bullets,
                fullVerbatimText = entity.fullVerbatimText,
                imagePath = entity.imagePath,
                durationSeconds = entity.durationSeconds
            )
        }

        val videoSuccess = SlideVideoEncoder.encodeSlidesToMp4(
            context = context,
            slides = mappedSlides,
            theme = theme,
            languageCode = effectiveLangCode,
            outputMp4File = videoOutputFile,
            slideDurations = slideDurations,
            translatedTexts = translatedSlidesMap,
            onProgress = { p, status ->
                onProgress(p, status)
            }
        )

        val finalVideoPath = if (videoSuccess || videoOutputFile.exists()) videoOutputFile.absolutePath else null
        val finalAudioPath = if (audioSuccess || audioOutputFile.exists()) audioOutputFile.absolutePath else null

        // Step 4: Record in database
        val totalSecs = slideDurations.sum()
        dao.updatePresentation(
            presentation.copy(
                currentLanguage = effectiveLangCode,
                videoPath = finalVideoPath,
                audioPath = finalAudioPath,
                durationSeconds = totalSecs
            )
        )

        val trackEntity = LanguageTrackEntity(
            presentationId = presentationId,
            languageCode = effectiveLangCode,
            languageName = targetLang.displayName,
            translatedSlidesJson = JSONArray(translatedSlidesMap.values.toList()).toString(),
            videoPath = finalVideoPath,
            audioPath = finalAudioPath,
            durationSeconds = totalSecs,
            isReady = true
        )
        dao.insertLanguageTrack(trackEntity)

        Pair(finalVideoPath, finalAudioPath)
    }

    suspend fun deletePresentation(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteSlidesForPresentation(id)
        dao.deletePresentation(id)
    }
}
