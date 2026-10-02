package com.example.data.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.data.model.Slide
import com.example.data.model.SlideVideoTheme
import java.io.File
import java.io.FileOutputStream

object SlideFrameRenderer {

    const val FRAME_WIDTH = 1280
    const val FRAME_HEIGHT = 720

    /**
     * Renders a presentation slide into a polished 16:9 Bitmap image.
     */
    fun renderSlideToBitmap(
        slide: Slide,
        slideIndex: Int,
        totalSlides: Int,
        theme: SlideVideoTheme,
        languageCode: String,
        displayTitle: String = slide.title,
        displayBullets: List<String> = slide.bulletPoints
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(FRAME_WIDTH, FRAME_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val isRtl = languageCode.equals("ar", ignoreCase = true) || languageCode.equals("ur", ignoreCase = true)

        // 1. Background Gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, FRAME_WIDTH.toFloat(), FRAME_HEIGHT.toFloat(),
                theme.bgGradientStart.toInt(),
                theme.bgGradientEnd.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, FRAME_WIDTH.toFloat(), FRAME_HEIGHT.toFloat(), bgPaint)

        // 2. Subtle Grid / Ambient Circles
        val ambientPaint = Paint().apply {
            isAntiAlias = true
            color = theme.accentColor.toInt()
            alpha = 18
        }
        canvas.drawCircle(FRAME_WIDTH * 0.88f, FRAME_HEIGHT * 0.15f, 240f, ambientPaint)
        canvas.drawCircle(FRAME_WIDTH * 0.12f, FRAME_HEIGHT * 0.9f, 200f, ambientPaint)

        // 3. Top Header Bar: Slide count badge & AI Audio badge
        val badgePaint = Paint().apply {
            isAntiAlias = true
            color = theme.cardBg.toInt()
            alpha = 220
        }
        val strokePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = theme.accentColor.toInt()
            alpha = 100
        }

        // Slide badge: e.g. "شريحة 1 من 4"
        val badgeText = if (isRtl) "شريحة $slideIndex من $totalSlides" else "Slide $slideIndex of $totalSlides"
        val textPaintBadge = Paint().apply {
            isAntiAlias = true
            color = theme.textColor.toInt()
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val badgeWidth = textPaintBadge.measureText(badgeText) + 48f
        val badgeHeight = 44f
        val badgeRect = if (isRtl) {
            RectF(FRAME_WIDTH - 64f - badgeWidth, 36f, FRAME_WIDTH - 64f, 36f + badgeHeight)
        } else {
            RectF(64f, 36f, 64f + badgeWidth, 36f + badgeHeight)
        }
        canvas.drawRoundRect(badgeRect, 22f, 22f, badgePaint)
        canvas.drawRoundRect(badgeRect, 22f, 22f, strokePaint)
        canvas.drawText(
            badgeText,
            badgeRect.left + 24f,
            badgeRect.top + 30f,
            textPaintBadge
        )

        // Verbatim audio badge on the opposite side:
        val audioBadgeText = if (isRtl) "نص الصفحة فقط دون إضافات" else "Verbatim Slide Audio Only"
        val textPaintAudio = Paint().apply {
            isAntiAlias = true
            color = theme.accentColor.toInt()
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val audioBadgeWidth = textPaintAudio.measureText(audioBadgeText) + 36f
        val audioBadgeRect = if (isRtl) {
            RectF(64f, 36f, 64f + audioBadgeWidth, 36f + badgeHeight)
        } else {
            RectF(FRAME_WIDTH - 64f - audioBadgeWidth, 36f, FRAME_WIDTH - 64f, 36f + badgeHeight)
        }
        canvas.drawRoundRect(audioBadgeRect, 22f, 22f, badgePaint)
        canvas.drawRoundRect(audioBadgeRect, 22f, 22f, strokePaint)
        canvas.drawText(
            audioBadgeText,
            audioBadgeRect.left + 18f,
            audioBadgeRect.top + 29f,
            textPaintAudio
        )

        // 4. Slide Title
        val titleTextPaint = TextPaint().apply {
            isAntiAlias = true
            color = theme.textColor.toInt()
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val contentLeft = 72
        val contentRight = FRAME_WIDTH - 72
        val contentWidth = contentRight - contentLeft

        val titleAlignment = if (isRtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL

        @Suppress("DEPRECATION")
        val titleLayout = StaticLayout(
            displayTitle,
            titleTextPaint,
            contentWidth,
            titleAlignment,
            1.2f,
            0f,
            false
        )

        canvas.save()
        canvas.translate(contentLeft.toFloat(), 110f)
        titleLayout.draw(canvas)
        canvas.restore()

        val titleBottomY = 110f + titleLayout.height + 20f

        // 5. Accent Divider line
        val dividerPaint = Paint().apply {
            isAntiAlias = true
            strokeWidth = 4f
            shader = LinearGradient(
                if (isRtl) contentRight.toFloat() else contentLeft.toFloat(), 0f,
                if (isRtl) (contentRight - 320f) else (contentLeft + 320f), 0f,
                theme.accentColor.toInt(),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawLine(
            if (isRtl) contentRight.toFloat() else contentLeft.toFloat(),
            titleBottomY,
            if (isRtl) (contentRight - 320f) else (contentLeft + 320f),
            titleBottomY,
            dividerPaint
        )

        // 6. Slide Main Content Card / Bullet Points
        var currentY = titleBottomY + 36f
        val bodyPaint = TextPaint().apply {
            isAntiAlias = true
            color = theme.textColor.toInt()
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            alpha = 240
        }

        val bulletDotPaint = Paint().apply {
            isAntiAlias = true
            color = theme.accentColor.toInt()
        }

        val hasImage = !slide.imagePath.isNullOrBlank() && File(slide.imagePath).exists()
        val textColWidth = if (hasImage) (contentWidth * 0.60f).toInt() else contentWidth

        val textColLeft = if (isRtl && hasImage) {
            contentLeft + (contentWidth * 0.40f).toInt()
        } else {
            contentLeft
        }

        // Draw image if available on the other column
        if (hasImage) {
            try {
                val imageFile = File(slide.imagePath!!)
                val opt = BitmapFactory.Options().apply { inSampleSize = 2 }
                val slideBmp = BitmapFactory.decodeFile(imageFile.absolutePath, opt)
                if (slideBmp != null) {
                    val imgLeft = if (isRtl) contentLeft.toFloat() else (contentLeft + textColWidth + 30f)
                    val imgRect = RectF(imgLeft, currentY, imgLeft + (contentWidth * 0.35f), currentY + 320f)
                    val cardBgPaint = Paint().apply {
                        isAntiAlias = true
                        color = theme.cardBg.toInt()
                    }
                    canvas.drawRoundRect(imgRect, 16f, 16f, cardBgPaint)
                    canvas.drawBitmap(slideBmp, null, imgRect, Paint(Paint.FILTER_BITMAP_FLAG))
                }
            } catch (e: Exception) {
                // Ignore image decode errors gracefully
            }
        }

        val bullets = if (displayBullets.isNotEmpty()) displayBullets else slide.fullVerbatimText.lines().filter { it != displayTitle && it.isNotBlank() }

        for ((bIdx, bullet) in bullets.take(6).withIndex()) {
            val cleanBullet = bullet.trim()
            if (cleanBullet.isBlank()) continue

            // Bullet icon
            val dotRadius = 6f
            val dotX = if (isRtl) (textColLeft + textColWidth - 14f) else (textColLeft + 14f)
            val dotY = currentY + 22f
            canvas.drawCircle(dotX, dotY, dotRadius, bulletDotPaint)

            val bulletTextLeft = if (isRtl) textColLeft else (textColLeft + 36)
            val bulletTextWidth = textColWidth - 44

            @Suppress("DEPRECATION")
            val bulletLayout = StaticLayout(
                cleanBullet,
                bodyPaint,
                bulletTextWidth,
                titleAlignment,
                1.3f,
                0f,
                false
            )

            canvas.save()
            canvas.translate(bulletTextLeft.toFloat(), currentY)
            bulletLayout.draw(canvas)
            canvas.restore()

            currentY += bulletLayout.height + 28f
            if (currentY > FRAME_HEIGHT - 90f) break
        }

        // 7. Bottom Branding / Audio Waveform decor
        val footerPaint = Paint().apply {
            isAntiAlias = true
            color = theme.textColor.toInt()
            textSize = 20f
            alpha = 140
        }
        val footerText = "SlideVideo AI • Powered Presentation-to-Video Engine"
        canvas.drawText(footerText, 72f, FRAME_HEIGHT - 32f, footerPaint)

        // Mini animated-looking wave bars
        val wavePaint = Paint().apply {
            isAntiAlias = true
            color = theme.accentColor.toInt()
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
        }
        val waveStartX = FRAME_WIDTH - 180f
        val barHeights = listOf(14f, 26f, 18f, 32f, 22f, 38f, 16f, 28f, 20f, 10f)
        for (i in barHeights.indices) {
            val bx = waveStartX + (i * 9f)
            val bh = barHeights[i]
            canvas.drawLine(bx, (FRAME_HEIGHT - 38f) - (bh / 2f), bx, (FRAME_HEIGHT - 38f) + (bh / 2f), wavePaint)
        }

        return bitmap
    }

    /**
     * Saves a rendered slide frame to a PNG image file.
     */
    fun saveSlideFrame(bitmap: Bitmap, outputFile: File) {
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
    }
}
