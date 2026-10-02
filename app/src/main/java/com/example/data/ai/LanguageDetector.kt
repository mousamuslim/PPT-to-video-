package com.example.data.ai

import com.example.data.model.SupportedLanguage

object LanguageDetector {

    val AUTO_LANGUAGE = SupportedLanguage(
        code = "auto",
        displayName = "تلقائي (اكتشاف ذكي)",
        nativeName = "Auto Detect",
        flagEmoji = "✨"
    )

    /**
     * Automatically detects the primary language of the text.
     * Returns the detected SupportedLanguage code.
     */
    fun detectLanguage(text: String): SupportedLanguage {
        if (text.isBlank()) return SupportedLanguage.fromCode("ar")

        // 1. Check for Arabic characters
        val arabicCharCount = text.count { c ->
            c in '\u0600'..'\u06FF' || c in '\u0750'..'\u077F' || c in '\uFB50'..'\uFDFF' || c in '\uFE70'..'\uFEFF'
        }
        val totalLetters = text.count { it.isLetter() }.coerceAtLeast(1)
        val arabicRatio = arabicCharCount.toFloat() / totalLetters

        if (arabicRatio > 0.25f) {
            // Check for Urdu specific characters
            val urduChars = setOf('ٹ', 'ڈ', 'ڑ', 'ں', 'ے', 'ھ', 'چ', 'پ', 'گ')
            val isUrdu = text.any { it in urduChars } && text.contains("ہے")
            return if (isUrdu) SupportedLanguage.fromCode("ur") else SupportedLanguage.fromCode("ar")
        }

        // 2. Check Turkish characters
        val turkishSpecific = setOf('ğ', 'Ğ', 'ı', 'İ', 'ş', 'Ş')
        if (text.any { it in turkishSpecific }) {
            return SupportedLanguage.fromCode("tr")
        }

        // 3. Check German characters & common words
        val germanSpecific = setOf('ä', 'Ä', 'ö', 'Ö', 'ü', 'Ü', 'ß')
        if (text.any { it in germanSpecific }) {
            return SupportedLanguage.fromCode("de")
        }

        // 4. Check Spanish characters & common words
        val spanishSpecific = setOf('ñ', 'Ñ', '¿', '¡')
        val words = text.lowercase().split(Regex("[^\\p{L}]+"))
        val spanishWords = setOf("el", "la", "los", "las", "un", "una", "del", "por", "para", "con", "es", "en", "que", "y")
        val spanishMatches = words.count { it in spanishWords }
        if (text.any { it in spanishSpecific } || (spanishMatches >= 3 && spanishMatches > words.size * 0.15f)) {
            return SupportedLanguage.fromCode("es")
        }

        // 5. Check French characters & common words
        val frenchWords = setOf("le", "la", "les", "des", "un", "une", "dans", "pour", "avec", "est", "et", "que", "qui", "en")
        val frenchMatches = words.count { it in frenchWords }
        val frenchAccents = setOf('é', 'è', 'ê', 'ë', 'à', 'ç', 'œ', 'ù')
        if (text.any { it in frenchAccents } && frenchMatches >= 2) {
            return SupportedLanguage.fromCode("fr")
        }

        // 6. Default to English for Latin alphabet
        return SupportedLanguage.fromCode("en")
    }

    /**
     * Resolves the actual language code when given "auto" or a specific code.
     */
    fun resolveEffectiveLanguageCode(selectedCode: String, sampleText: String): String {
        return if (selectedCode.equals("auto", ignoreCase = true)) {
            detectLanguage(sampleText).code
        } else {
            selectedCode
        }
    }
}
