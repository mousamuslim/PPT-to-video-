package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.pptx.PptxParser
import com.example.data.pptx.SamplePresentations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SlideVideo AI", appName)
    }

    @Test
    fun `verify sample presentations have verbatim slides`() {
        val samples = SamplePresentations.ALL_SAMPLES
        assertTrue(samples.isNotEmpty())

        val aiSample = samples.first()
        assertTrue(aiSample.slides.isNotEmpty())
        for (slide in aiSample.slides) {
            assertTrue(slide.title.isNotBlank())
            assertTrue(slide.fullVerbatimText.isNotBlank())
            assertTrue(slide.durationSeconds >= 4.0f)
        }
    }

    @Test
    fun `calculate slide duration accurately`() {
        val shortText = "عنوان الشريحة"
        val duration = PptxParser.calculateSlideDuration(shortText)
        assertTrue(duration >= 4.0f)
    }

    @Test
    fun `detect arabic language automatically`() {
        val arabicText = "الذكاء الاصطناعي في التعليم المعاصر"
        val detected = com.example.data.ai.LanguageDetector.detectLanguage(arabicText)
        assertEquals("ar", detected.code)
    }

    @Test
    fun `detect english language automatically`() {
        val englishText = "Artificial Intelligence in Contemporary Education and Machine Learning"
        val detected = com.example.data.ai.LanguageDetector.detectLanguage(englishText)
        assertEquals("en", detected.code)
    }
}
