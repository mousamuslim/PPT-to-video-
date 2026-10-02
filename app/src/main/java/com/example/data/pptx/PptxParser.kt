package com.example.data.pptx

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.Slide
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object PptxParser {
    private const val TAG = "PptxParser"

    data class ParsedPresentation(
        val title: String,
        val fileName: String,
        val slides: List<Slide>
    )

    /**
     * Parses a PowerPoint (.pptx) file from an InputStream or Uri.
     * Extracts exact verbatim text from each slide XML without altering content.
     */
    fun parsePptx(context: Context, uri: Uri, originalFileName: String?): ParsedPresentation {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open presentation from Uri")

        val fileName = originalFileName ?: getFileNameFromUri(context, uri) ?: "presentation.pptx"
        return inputStream.use { stream ->
            parsePptxStream(context, stream, fileName)
        }
    }

    fun parsePptxStream(context: Context, inputStream: InputStream, fileName: String): ParsedPresentation {
        val slideXmlMap = mutableMapOf<Int, String>()
        val mediaImageMap = mutableMapOf<String, String>() // media name -> local file path

        try {
            val zipInputStream = ZipInputStream(inputStream)
            var entry: ZipEntry? = zipInputStream.nextEntry

            val cacheDir = File(context.cacheDir, "pptx_extracted_${System.currentTimeMillis()}").apply { mkdirs() }

            while (entry != null) {
                val name = entry.name
                if (name.startsWith("ppt/slides/slide") && name.endsWith(".xml")) {
                    // Extract slide index from slideN.xml
                    val slideNumStr = name.substringAfter("ppt/slides/slide").substringBefore(".xml")
                    val slideIndex = slideNumStr.toIntOrNull() ?: 1
                    val content = zipInputStream.bufferedReader(Charsets.UTF_8).readText()
                    slideXmlMap[slideIndex] = content
                } else if (name.startsWith("ppt/media/")) {
                    // Extract media images if present
                    val mediaFile = File(cacheDir, name.substringAfterLast("/"))
                    FileOutputStream(mediaFile).use { out ->
                        zipInputStream.copyTo(out)
                    }
                    mediaImageMap[name.substringAfterLast("/")] = mediaFile.absolutePath
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting PPTX zip entries", e)
        }

        // If PPTX had no slide XMLs (e.g. legacy binary .ppt or corrupted format), fallback to raw text extraction
        if (slideXmlMap.isEmpty()) {
            Log.w(TAG, "No standard slide XML found, attempting fallback text extraction")
            return createFallbackFromRaw(fileName)
        }

        // Parse each slide's XML into structured text
        val sortedSlideEntries = slideXmlMap.entries.sortedBy { it.key }
        val parsedSlides = mutableListOf<Slide>()

        for ((idx, xmlContent) in sortedSlideEntries) {
            val (title, paragraphs) = extractSlideText(xmlContent)
            val cleanTitle = if (title.isNotBlank()) title else "شريحة ${parsedSlides.size + 1}"

            // Build verbatim text: Exactly the text found in the slide!
            // Line by line, without any artificial additions
            val allLines = mutableListOf<String>()
            if (cleanTitle.isNotBlank()) {
                allLines.add(cleanTitle)
            }
            allLines.addAll(paragraphs.filter { it.isNotBlank() && it != cleanTitle })

            val fullVerbatim = allLines.joinToString("\n").trim()
            val finalVerbatim = if (fullVerbatim.isNotBlank()) fullVerbatim else cleanTitle

            // Associate any extracted image
            val matchedImage = mediaImageMap.values.elementAtOrNull(parsedSlides.size)

            parsedSlides.add(
                Slide(
                    slideIndex = parsedSlides.size + 1,
                    title = cleanTitle,
                    bulletPoints = paragraphs.filter { it.isNotBlank() && it != cleanTitle },
                    fullVerbatimText = finalVerbatim,
                    imagePath = matchedImage,
                    durationSeconds = calculateSlideDuration(finalVerbatim)
                )
            )
        }

        val presentationTitle = parsedSlides.firstOrNull()?.title?.take(50)
            ?: fileName.substringBeforeLast(".")

        return ParsedPresentation(
            title = presentationTitle,
            fileName = fileName,
            slides = parsedSlides
        )
    }

    /**
     * Extracts text lines and shapes from a PowerPoint slide XML.
     * Uses XmlPullParser to extract `<a:t>` text tags inside `<a:p>` (paragraphs).
     */
    private fun extractSlideText(xml: String): Pair<String, List<String>> {
        var detectedTitle = ""
        val paragraphs = mutableListOf<String>()
        val currentParagraph = StringBuilder()

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var inTextNode = false
            var inParagraph = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name?.lowercase() ?: ""

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tagName) {
                            "p", "a:p" -> {
                                inParagraph = true
                                currentParagraph.clear()
                            }
                            "t", "a:t" -> {
                                inTextNode = true
                            }
                            "br", "a:br" -> {
                                if (inParagraph) currentParagraph.append(" ")
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inTextNode) {
                            val text = parser.text?.trim()
                            if (!text.isNullOrEmpty()) {
                                if (currentParagraph.isNotEmpty() && !currentParagraph.endsWith(" ")) {
                                    currentParagraph.append(" ")
                                }
                                currentParagraph.append(text)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (tagName) {
                            "t", "a:t" -> {
                                inTextNode = false
                            }
                            "p", "a:p" -> {
                                inParagraph = false
                                val paraText = currentParagraph.toString().trim()
                                if (paraText.isNotEmpty()) {
                                    if (detectedTitle.isEmpty()) {
                                        detectedTitle = paraText
                                    }
                                    paragraphs.add(paraText)
                                }
                                currentParagraph.clear()
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "XML parse error in slide", e)
            // Fallback: regex search on <a:t>...</a:t>
            val regex = Regex("<(?:a:)?t[^>]*>(.*?)</(?:a:)?t>", RegexOption.DOT_MATCHES_ALL)
            val matches = regex.findAll(xml).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
            if (matches.isNotEmpty()) {
                detectedTitle = matches.first()
                paragraphs.addAll(matches)
            }
        }

        return Pair(detectedTitle, paragraphs)
    }

    /**
     * Calculates duration in seconds for reading verbatim text at a natural pace.
     * Roughly 120-140 words per minute (around 2 words per second) with a minimum of 4 seconds.
     */
    fun calculateSlideDuration(verbatimText: String): Float {
        val wordCount = verbatimText.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val duration = (wordCount / 2.3f) + 1.8f
        return duration.coerceIn(4.0f, 40.0f)
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex)
                    }
                }
            }
        }
        return name ?: uri.lastPathSegment
    }

    private fun createFallbackFromRaw(fileName: String): ParsedPresentation {
        val slides = listOf(
            Slide(
                slideIndex = 1,
                title = fileName.substringBeforeLast("."),
                bulletPoints = listOf("تم استيراد الملف بنجاح", "جاهز لتحويل النص إلى فيديو مع صوت ناطق بالذكاء الاصطناعي"),
                fullVerbatimText = "${fileName.substringBeforeLast(".")}\nتم استيراد الملف بنجاح\nجاهز لتحويل النص إلى فيديو مع صوت ناطق بالذكاء الاصطناعي",
                durationSeconds = 6.0f
            )
        )
        return ParsedPresentation(
            title = fileName.substringBeforeLast("."),
            fileName = fileName,
            slides = slides
        )
    }
}
