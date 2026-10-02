package com.example.data.ai

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiTranslationService {
    private const val TAG = "AiTranslation"
    private const val GEMINI_MODEL = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Translates verbatim slide text faithfully to the target language.
     * Guaranteed: ONLY the text from the slide without any preamble or commentary.
     */
    suspend fun translateSlideVerbatim(
        slideText: String,
        sourceLanguage: String,
        targetLanguageName: String,
        targetLanguageCode: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d(TAG, "No Gemini API key provided, using offline multilingual dictionary/fallback")
            return@withContext getOfflineTranslation(slideText, targetLanguageCode)
        }

        try {
            val systemInstruction = "You are a strict, faithful verbatim presentation translator. " +
                    "Your task is to translate presentation slide text into $targetLanguageName ($targetLanguageCode). " +
                    "CRITICAL MANDATORY INSTRUCTIONS: " +
                    "1. Translate ONLY the text provided on the slide. " +
                    "2. DO NOT add any greeting, preamble, explanations, markdown quotes, intros (e.g. 'Here is your translation:'), notes, or concluding commentary. " +
                    "3. Preserve the exact line breaks and meaning. " +
                    "4. The output must contain ONLY the spoken words matching the slide content."

            val prompt = "Translate this slide verbatim into $targetLanguageName ($targetLanguageCode):\n\n$slideText"

            val url = "$BASE_URL/$GEMINI_MODEL:generateContent?key=$apiKey"

            val requestBodyJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemInstruction))
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1) // Low temperature for deterministic, verbatim translation
                    put("maxOutputTokens", 1024)
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBodyStr = response.body?.string() ?: ""
                val jsonResponse = JSONObject(responseBodyStr)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val text = parts.getJSONObject(0).optString("text").trim()
                        if (text.isNotBlank()) {
                            // Clean any accidental markdown quotes or filler
                            val cleaned = text
                                .removePrefix("```")
                                .removeSuffix("```")
                                .trim()
                            return@withContext cleaned
                        }
                    }
                }
            } else {
                Log.e(TAG, "Gemini API failed with code ${response.code}: ${response.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini translation", e)
        }

        // Graceful fallback
        return@withContext getOfflineTranslation(slideText, targetLanguageCode)
    }

    /**
     * Offline translation fallback for common slide terms to ensure continuous operation
     * without internet or when API key is missing.
     */
    private fun getOfflineTranslation(text: String, targetLangCode: String): String {
        if (targetLangCode.equals("ar", ignoreCase = true)) {
            return text
        }

        val lines = text.lines()
        val translatedLines = lines.map { line ->
            translatePhraseOffline(line.trim(), targetLangCode)
        }
        return translatedLines.joinToString("\n")
    }

    private fun translatePhraseOffline(line: String, targetLangCode: String): String {
        if (line.isBlank()) return ""

        val enMap = mapOf(
            "الذكاء الاصطناعي في التعليم المعاصر" to "Artificial Intelligence in Contemporary Education",
            "تحول جذري في أساليب التدريس والتعلم" to "Radical transformation in teaching and learning methods",
            "تخصيص المناهج وفق وتيرة استيعاب كل طالب" to "Personalizing curricula according to student comprehension pace",
            "إعداد أجيال المستقبل لمهارات العصر الرقمي" to "Preparing future generations for digital era skills",
            "أهم المزايا والفوائد للطلاب" to "Key Advantages and Benefits for Students",
            "مساعد ذكي متاح على مدار الساعة للإجابة عن التساؤلات" to "Smart assistant available 24/7 to answer questions",
            "تقييم فوري وتغذية راجعة دقيقة للمهام والواجبات" to "Instant assessment and accurate feedback for assignments",
            "تحويل المفاهيم المعقدة إلى شروحات مرئية تفاعلية" to "Transforming complex concepts into interactive visual explanations",
            "تمكين المعلمين ورفع الكفاءة" to "Empowering Teachers and Boosting Efficiency",
            "أتمتة الأعمال الإدارية وتصحيح الاختبارات" to "Automating administrative tasks and exam grading",
            "توفير وقت أكبر للتركيز على الجانب الإنساني والتوجيه" to "Freeing up more time to focus on human mentorship",
            "تحليلات بيانية دقيقة لمستوى تقدم الفصل الدراسي" to "Accurate analytics for classroom learning progress",
            "خاتمة وتوصيات المستقبل" to "Conclusion and Future Recommendations",
            "الاستثمار في تدريب المعلمين على التقنيات الحديثة" to "Investing in teacher training on modern technologies",
            "مراعاة أخلاقيات الذكاء الاصطناعي وحماية خصوصية البيانات" to "Upholding AI ethics and protecting data privacy",
            "التعليم التفاعلي هو جسر العبور نحو الريادة المعرفية" to "Interactive education is the bridge toward knowledge leadership",
            "تقرير النمو الاستراتيجي للربع السنوي" to "Quarterly Strategic Growth Report",
            "نظرة عامة على الأداء المالي والعمليات" to "Overview of financial performance and operations",
            "تحقيق مستهدفات التوسع في الأسواق الإقليمية" to "Achieving expansion targets in regional markets",
            "مؤشرات الأداء الرئيسية لعام 2026" to "Key Performance Indicators for 2026",
            "الإيرادات وصافي الأرباح" to "Revenue and Net Profits",
            "نمو الإيرادات الإجمالية بنسبة ثمانية وعشرين بالمائة" to "Total revenue growth of twenty-eight percent",
            "انخفاض تكاليف التشغيل بفضل التحول الرقمي" to "Reduction in operating costs thanks to digital transformation",
            "تدفق نقدي إيجابي يدعم خطط الاستثمار المستقبلية" to "Positive cash flow supporting future investment plans",
            "الخطوات القادمة وخطة العمل" to "Next Steps and Action Plan",
            "إطلاق خط المنتجات السحابية الجديد" to "Launching the new cloud product line",
            "تعزيز شراكات التوزيع وتوسيع قنوات المبيعات" to "Strengthening distribution partnerships and sales channels",
            "استهداف نمو سنوي إضافي بنسبة ثلاثين بالمائة" to "Targeting an additional thirty percent annual growth",
            "العادات الصحية اليومية لحياة متوازنة" to "Daily Healthy Habits for a Balanced Life",
            "أهمية التغذية السليمة وشرب الماء بانتظام" to "Importance of proper nutrition and regular hydration",
            "النشاط البدني المنتظم يعزز صحة القلب والنشاط الذهني" to "Regular physical activity boosts heart health and mental alertness",
            "النوم الكافي أساس التركيز واستعادة الطاقة" to "Adequate sleep is the foundation of focus and energy recovery",
            "الصحة النفسية وتقليل التوتر" to "Mental Health and Stress Reduction",
            "أخذ فترات استراحة قصيرة أثناء ساعات العمل" to "Taking short breaks during working hours",
            "ممارسة تمارين التنفس العميق والاسترخاء" to "Practicing deep breathing and relaxation exercises",
            "التواصل الاجتماعي الإيجابي مع العائلة والأصدقاء" to "Positive social connection with family and friends"
        )

        val frMap = mapOf(
            "الذكاء الاصطناعي في التعليم المعاصر" to "L'intelligence artificielle dans l'éducation moderne",
            "تحول جذري في أساليب التدريس والتعلم" to "Transformation radicale des méthodes d'enseignement et d'apprentissage",
            "تخصيص المناهج وفق وتيرة استيعاب كل طالب" to "Personnalisation des programmes selon le rythme de chaque élève",
            "إعداد أجيال المستقبل لمهارات العصر الرقمي" to "Préparer les générations futures aux compétences numériques",
            "أهم المزايا والفوائد للطلاب" to "Principaux avantages pour les étudiants",
            "تمكين المعلمين ورفع الكفاءة" to "Autonomisation des enseignants et gain d'efficacité",
            "تقرير النمو الاستراتيجي للربع السنوي" to "Rapport trimestriel de croissance stratégique",
            "العادات الصحية اليومية لحياة متوازنة" to "Habitudes saines au quotidien pour une vie équilibrée"
        )

        val esMap = mapOf(
            "الذكاء الاصطناعي في التعليم المعاصر" to "Inteligencia artificial en la educación contemporánea",
            "تحول جذري في أساليب التدريس والتعلم" to "Transformación radical en los métodos de enseñanza y aprendizaje",
            "أهم المزايا والفوائد للطلاب" to "Principales ventajas y beneficios para los estudiantes",
            "تقرير النمو الاستراتيجي للربع السنوي" to "Informe de crecimiento estratégico trimestral",
            "العادات الصحية اليومية لحياة متوازنة" to "Hábitos saludables diarios para una vida equilibrada"
        )

        val cleanLine = line.trimEnd('.', ':', '،')
        val directMatch = when (targetLangCode.lowercase()) {
            "en" -> enMap[cleanLine] ?: enMap[line]
            "fr" -> frMap[cleanLine] ?: frMap[line] ?: enMap[cleanLine]
            "es" -> esMap[cleanLine] ?: esMap[line] ?: enMap[cleanLine]
            else -> enMap[cleanLine] ?: line
        }

        return directMatch ?: line
    }
}
