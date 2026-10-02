package com.example.data.model

data class Slide(
    val slideIndex: Int,
    val title: String,
    val bulletPoints: List<String>,
    val fullVerbatimText: String,
    val imagePath: String? = null,
    val durationSeconds: Float = 5.0f
)

data class Presentation(
    val id: Long = 0,
    val title: String,
    val fileName: String,
    val slideCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val originalLanguage: String = "ar",
    val currentLanguage: String = "ar",
    val slides: List<Slide> = emptyList(),
    val videoPath: String? = null,
    val audioPath: String? = null
)

data class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val flagEmoji: String
) {
    companion object {
        val ALL = listOf(
            SupportedLanguage("auto", "تلقائي (اكتشاف ذكي)", "Auto Detect", "✨"),
            SupportedLanguage("ar", "العربية", "Arabic", "🇸🇦"),
            SupportedLanguage("en", "English", "الإنجليزية", "🇬🇧"),
            SupportedLanguage("fr", "Français", "الفرنسية", "🇫🇷"),
            SupportedLanguage("es", "Español", "الإسبانية", "🇪🇸"),
            SupportedLanguage("de", "Deutsch", "الألمانية", "🇩🇪"),
            SupportedLanguage("tr", "Türkçe", "التركية", "🇹🇷"),
            SupportedLanguage("ur", "اردو", "الأردية", "🇵🇰"),
            SupportedLanguage("it", "Italiano", "الإيطالية", "🇮🇹"),
            SupportedLanguage("ru", "Русский", "الروسية", "🇷🇺"),
            SupportedLanguage("zh", "中文", "الصينية", "🇨🇳")
        )

        fun fromCode(code: String): SupportedLanguage {
            return ALL.find { it.code.equals(code, ignoreCase = true) } ?: ALL.first()
        }
    }
}

enum class SlideVideoTheme(
    val titleAr: String,
    val bgGradientStart: Long,
    val bgGradientEnd: Long,
    val cardBg: Long,
    val accentColor: Long,
    val textColor: Long
) {
    DARK_INDIGO("نيلي ليلي حديث", 0xFF0B0F19, 0xFF1E1B4B, 0xFF131B2E, 0xFF6366F1, 0xFFF8FAFC),
    CYAN_TEAL("أزرق بحري مشرق", 0xFF082F49, 0xFF0F172A, 0xFF0C4A6E, 0xFF06B6D4, 0xFFFFFFFF),
    EMERALD_BIZ("أخضر تنفيذي راقي", 0xFF022C22, 0xFF0F172A, 0xFF064E3B, 0xFF10B981, 0xFFECFDF5),
    SUNSET_WARM("غروب دافئ أنيق", 0xFF2D1515, 0xFF1C1917, 0xFF451A1A, 0xFFF59E0B, 0xFFFFFBEB),
    CLEAN_LIGHT("أبيض ناصع احترافي", 0xFFF8FAFC, 0xFFE2E8F0, 0xFFFFFFFF, 0xFF4F46E5, 0xFF0F172A)
}

data class GenerationState(
    val isGenerating: Boolean = false,
    val progress: Float = 0f,
    val currentStep: String = "",
    val outputVideoPath: String? = null,
    val error: String? = null
)
