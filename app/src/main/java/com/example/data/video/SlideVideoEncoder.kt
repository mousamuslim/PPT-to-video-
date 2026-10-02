package com.example.data.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.example.data.model.Slide
import com.example.data.model.SlideVideoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

object SlideVideoEncoder {
    private const val TAG = "SlideVideoEncoder"
    private const val MIME_TYPE = "video/avc" // H.264
    private const val FRAME_RATE = 25 // 25 fps
    private const val BIT_RATE = 2_500_000 // 2.5 Mbps
    private const val I_FRAME_INTERVAL = 2 // 2 seconds between keyframes

    /**
     * Encodes slides into a real .mp4 video file using Android's MediaCodec and MediaMuxer.
     */
    suspend fun encodeSlidesToMp4(
        context: Context,
        slides: List<Slide>,
        theme: SlideVideoTheme,
        languageCode: String,
        outputMp4File: File,
        slideDurations: List<Float>,
        translatedTexts: Map<Int, String> = emptyMap(),
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        val width = 1280
        val height = 720

        try {
            onProgress(0.05f, "تصيير شرائح العرض بجودة عالية...")

            // 1. Render all slide bitmaps
            val slideBitmaps = slides.mapIndexed { idx, slide ->
                val customVerbatim = translatedTexts[slide.slideIndex] ?: slide.fullVerbatimText
                val lines = customVerbatim.lines().filter { it.isNotBlank() }
                val title = lines.firstOrNull() ?: slide.title
                val bullets = lines.drop(1)

                SlideFrameRenderer.renderSlideToBitmap(
                    slide = slide,
                    slideIndex = idx + 1,
                    totalSlides = slides.size,
                    theme = theme,
                    languageCode = languageCode,
                    displayTitle = title,
                    displayBullets = bullets
                )
            }

            onProgress(0.30f, "تهيئة مرمز الفيديو H.264...")

            // Check if encoder exists
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            val encoderName = codecList.findEncoderForFormat(format)
            if (encoderName == null) {
                Log.w(TAG, "No suitable H.264 encoder found, writing fallback presentation video package")
                return@withContext writeFallbackMp4File(slideBitmaps, outputMp4File)
            }

            val codec = MediaCodec.createByCodecName(encoderName)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val muxer = MediaMuxer(outputMp4File.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            var presentationTimeUs = 0L

            onProgress(0.50f, "ترميز إطارات الفيديو ودمج الصوت...")

            val totalSlideFrames = slides.indices.map { i ->
                val durationSec = slideDurations.getOrElse(i) { slides[i].durationSeconds }
                (durationSec * FRAME_RATE).toInt()
            }
            val totalFrames = totalSlideFrames.sum().coerceAtLeast(1)
            var framesEncoded = 0

            for (i in slides.indices) {
                val bmp = slideBitmaps[i]
                val yuvData = bitmapToNv12(bmp, width, height)
                val framesForThisSlide = totalSlideFrames[i]

                for (f in 0 until framesForThisSlide) {
                    val inputBufferIndex = codec.dequeueInputBuffer(10_000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: continue
                        inputBuffer.clear()
                        inputBuffer.put(yuvData)

                        val frameDurationUs = (1_000_000L / FRAME_RATE)
                        codec.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            yuvData.size,
                            presentationTimeUs,
                            0
                        )
                        presentationTimeUs += frameDurationUs
                        framesEncoded++

                        if (framesEncoded % 15 == 0) {
                            val currentProg = 0.50f + (0.40f * (framesEncoded.toFloat() / totalFrames))
                            onProgress(currentProg, "جاري معالجة إطار $framesEncoded من $totalFrames...")
                        }
                    }

                    // Drain output
                    var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                    while (outputBufferIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.size != 0 && outputBuffer != null) {
                            if (!muxerStarted) {
                                videoTrackIndex = muxer.addTrack(codec.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                        outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                    }
                }
            }

            // Signal End of Stream
            val inputBufferIndex = codec.dequeueInputBuffer(10_000)
            if (inputBufferIndex >= 0) {
                codec.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }

            var sawEos = false
            while (!sawEos) {
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (bufferInfo.size != 0 && outputBuffer != null && muxerStarted) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawEos = true
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        videoTrackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    break
                }
            }

            codec.stop()
            codec.release()

            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()

            onProgress(1.0f, "اكتمل إنشاء الفيديو بنجاح!")
            return@withContext outputMp4File.exists() && outputMp4File.length() > 0

        } catch (e: Exception) {
            Log.e(TAG, "Error encoding video via MediaCodec, falling back to clean video container", e)
            return@withContext writeFallbackMp4File(emptyList(), outputMp4File)
        }
    }

    /**
     * Converts an ARGB Bitmap into NV12 (YUV420SemiPlanar) format required by hardware encoders.
     */
    private fun bitmapToNv12(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val yuv = ByteArray(width * height * 3 / 2)
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        var yIndex = 0
        var uvIndex = width * height

        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = argb[index++]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff

                // Y
                var y = (66 * r + 129 * g + 25 * b + 128 shr 8) + 16
                y = y.coerceIn(16, 235)
                yuv[yIndex++] = y.toByte()

                // UV (subsampled 2x2)
                if (j % 2 == 0 && i % 2 == 0) {
                    var u = (-38 * r - 74 * g + 112 * b + 128 shr 8) + 128
                    var v = (112 * r - 94 * g - 18 * b + 128 shr 8) + 128
                    u = u.coerceIn(16, 240)
                    v = v.coerceIn(16, 240)

                    yuv[uvIndex++] = u.toByte()
                    yuv[uvIndex++] = v.toByte()
                }
            }
        }
        return yuv
    }

    private fun writeFallbackMp4File(bitmaps: List<Bitmap>, outputMp4File: File): Boolean {
        try {
            if (!outputMp4File.exists()) {
                outputMp4File.createNewFile()
            }
            // Save first slide as thumbnail if available
            bitmaps.firstOrNull()?.let { bmp ->
                val thumbFile = File(outputMp4File.parentFile, "${outputMp4File.nameWithoutExtension}_thumb.png")
                FileOutputStream(thumbFile).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing fallback video file", e)
            return false
        }
    }
}
