package com.example.data.pptx

import com.example.data.model.Presentation
import com.example.data.model.Slide

object SamplePresentations {

    fun getSampleAiEducation(): Presentation {
        val slides = listOf(
            Slide(
                slideIndex = 1,
                title = "الذكاء الاصطناعي في التعليم المعاصر",
                bulletPoints = listOf(
                    "تحول جذري في أساليب التدريس والتعلم",
                    "تخصيص المناهج وفق وتيرة استيعاب كل طالب",
                    "إعداد أجيال المستقبل لمهارات العصر الرقمي"
                ),
                fullVerbatimText = "الذكاء الاصطناعي في التعليم المعاصر.\nتحول جذري في أساليب التدريس والتعلم.\nتخصيص المناهج وفق وتيرة استيعاب كل طالب.\nإعداد أجيال المستقبل لمهارات العصر الرقمي.",
                durationSeconds = 8.5f
            ),
            Slide(
                slideIndex = 2,
                title = "أهم المزايا والفوائد للطلاب",
                bulletPoints = listOf(
                    "مساعد ذكي متاح على مدار الساعة للإجابة عن التساؤلات",
                    "تقييم فوري وتغذية راجعة دقيقة للمهام والواجبات",
                    "تحويل المفاهيم المعقدة إلى شروحات مرئية تفاعلية"
                ),
                fullVerbatimText = "أهم المزايا والفوائد للطلاب.\nمساعد ذكي متاح على مدار الساعة للإجابة عن التساؤلات.\nتقييم فوري وتغذية راجعة دقيقة للمهام والواجبات.\nتحويل المفاهيم المعقدة إلى شروحات مرئية تفاعلية.",
                durationSeconds = 9.0f
            ),
            Slide(
                slideIndex = 3,
                title = "تمكين المعلمين ورفع الكفاءة",
                bulletPoints = listOf(
                    "أتمتة الأعمال الإدارية وتصحيح الاختبارات",
                    "توفير وقت أكبر للتركيز على الجانب الإنساني والتوجيه",
                    "تحليلات بيانية دقيقة لمستوى تقدم الفصل الدراسي"
                ),
                fullVerbatimText = "تمكين المعلمين ورفع الكفاءة.\nأتمتة الأعمال الإدارية وتصحيح الاختبارات.\nتوفير وقت أكبر للتركيز على الجانب الإنساني والتوجيه.\nتحليلات بيانية دقيقة لمستوى تقدم الفصل الدراسي.",
                durationSeconds = 8.8f
            ),
            Slide(
                slideIndex = 4,
                title = "خاتمة وتوصيات المستقبل",
                bulletPoints = listOf(
                    "الاستثمار في تدريب المعلمين على التقنيات الحديثة",
                    "مراعاة أخلاقيات الذكاء الاصطناعي وحماية خصوصية البيانات",
                    "التعليم التفاعلي هو جسر العبور نحو الريادة المعرفية"
                ),
                fullVerbatimText = "خاتمة وتوصيات المستقبل.\nالاستثمار في تدريب المعلمين على التقنيات الحديثة.\nمراعاة أخلاقيات الذكاء الاصطناعي وحماية خصوصية البيانات.\nالتعليم التفاعلي هو جسر العبور نحو الريادة المعرفية.",
                durationSeconds = 9.2f
            )
        )

        return Presentation(
            id = 1,
            title = "الذكاء الاصطناعي في التعليم المعاصر",
            fileName = "AI_in_Education_2026.pptx",
            slideCount = slides.size,
            originalLanguage = "ar",
            slides = slides
        )
    }

    fun getSampleBusinessReport(): Presentation {
        val slides = listOf(
            Slide(
                slideIndex = 1,
                title = "تقرير النمو الاستراتيجي للربع السنوي",
                bulletPoints = listOf(
                    "نظرة عامة على الأداء المالي والعمليات",
                    "تحقيق مستهدفات التوسع في الأسواق الإقليمية",
                    "مؤشرات الأداء الرئيسية لعام 2026"
                ),
                fullVerbatimText = "تقرير النمو الاستراتيجي للربع السنوي.\nنظرة عامة على الأداء المالي والعمليات.\nتحقيق مستهدفات التوسع في الأسواق الإقليمية.\nمؤشرات الأداء الرئيسية لعام 2026.",
                durationSeconds = 8.0f
            ),
            Slide(
                slideIndex = 2,
                title = "الإيرادات وصافي الأرباح",
                bulletPoints = listOf(
                    "نمو الإيرادات الإجمالية بنسبة ثمانية وعشرين بالمائة",
                    "انخفاض تكاليف التشغيل بفضل التحول الرقمي",
                    "تدفق نقدي إيجابي يدعم خطط الاستثمار المستقبلية"
                ),
                fullVerbatimText = "الإيرادات وصافي الأرباح.\nنمو الإيرادات الإجمالية بنسبة ثمانية وعشرين بالمائة.\nانخفاض تكاليف التشغيل بفضل التحول الرقمي.\nتدفق نقدي إيجابي يدعم خطط الاستثمار المستقبلية.",
                durationSeconds = 8.5f
            ),
            Slide(
                slideIndex = 3,
                title = "الخطوات القادمة وخطة العمل",
                bulletPoints = listOf(
                    "إطلاق خط المنتجات السحابية الجديد",
                    "تعزيز شراكات التوزيع وتوسيع قنوات المبيعات",
                    "استهداف نمو سنوي إضافي بنسبة ثلاثين بالمائة"
                ),
                fullVerbatimText = "الخطوات القادمة وخطة العمل.\nإطلاق خط المنتجات السحابية الجديد.\nتعزيز شراكات التوزيع وتوسيع قنوات المبيعات.\nاستهداف نمو سنوي إضافي بنسبة ثلاثين بالمائة.",
                durationSeconds = 8.2f
            )
        )

        return Presentation(
            id = 2,
            title = "تقرير النمو الاستراتيجي للربع السنوي",
            fileName = "Business_Growth_Report.pptx",
            slideCount = slides.size,
            originalLanguage = "ar",
            slides = slides
        )
    }

    fun getSampleHealthWellness(): Presentation {
        val slides = listOf(
            Slide(
                slideIndex = 1,
                title = "العادات الصحية اليومية لحياة متوازنة",
                bulletPoints = listOf(
                    "أهمية التغذية السليمة وشرب الماء بانتظام",
                    "النشاط البدني المنتظم يعزز صحة القلب والنشاط الذهني",
                    "النوم الكافي أساس التركيز واستعادة الطاقة"
                ),
                fullVerbatimText = "العادات الصحية اليومية لحياة متوازنة.\nأهمية التغذية السليمة وشرب الماء بانتظام.\nالنشاط البدني المنتظم يعزز صحة القلب والنشاط الذهني.\nالنوم الكافي أساس التركيز واستعادة الطاقة.",
                durationSeconds = 8.5f
            ),
            Slide(
                slideIndex = 2,
                title = "الصحة النفسية وتقليل التوتر",
                bulletPoints = listOf(
                    "أخذ فترات استراحة قصيرة أثناء ساعات العمل",
                    "ممارسة تمارين التنفس العميق والاسترخاء",
                    "التواصل الاجتماعي الإيجابي مع العائلة والأصدقاء"
                ),
                fullVerbatimText = "الصحة النفسية وتقليل التوتر.\nأخذ فترات استراحة قصيرة أثناء ساعات العمل.\nممارسة تمارين التنفس العميق والاسترخاء.\nالتواصل الاجتماعي الإيجابي مع العائلة والأصدقاء.",
                durationSeconds = 8.0f
            )
        )

        return Presentation(
            id = 3,
            title = "العادات الصحية اليومية لحياة متوازنة",
            fileName = "Health_and_Wellness_Guide.pptx",
            slideCount = slides.size,
            originalLanguage = "ar",
            slides = slides
        )
    }

    val ALL_SAMPLES = listOf(
        getSampleAiEducation(),
        getSampleBusinessReport(),
        getSampleHealthWellness()
    )
}
