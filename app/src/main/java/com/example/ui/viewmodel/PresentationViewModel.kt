package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.audio.TtsAudioSynthesizer
import com.example.data.db.SlideVideoDatabase
import com.example.data.model.GenerationState
import com.example.data.model.Presentation
import com.example.data.model.Slide
import com.example.data.model.SlideVideoTheme
import com.example.data.model.SupportedLanguage
import com.example.data.repo.PresentationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class Screen {
    object Home : Screen()
    data class Studio(val presentationId: Long) : Screen()
    data class Player(val presentationId: Long, val languageCode: String = "ar") : Screen()
}

class PresentationViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "PresentationVM"

    private val db = SlideVideoDatabase.getInstance(application)
    val tts = TtsAudioSynthesizer(application)
    val repository = PresentationRepository(application, db.presentationDao(), tts)

    val presentations: StateFlow<List<Presentation>> = repository.allPresentations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _activePresentation = MutableStateFlow<Presentation?>(null)
    val activePresentation: StateFlow<Presentation?> = _activePresentation.asStateFlow()

    private val _activeSlides = MutableStateFlow<List<Slide>>(emptyList())
    val activeSlides: StateFlow<List<Slide>> = _activeSlides.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(SupportedLanguage.ALL[0]) // Default Arabic
    val selectedLanguage: StateFlow<SupportedLanguage> = _selectedLanguage.asStateFlow()

    private val _selectedTheme = MutableStateFlow(SlideVideoTheme.DARK_INDIGO)
    val selectedTheme: StateFlow<SlideVideoTheme> = _selectedTheme.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _speechPitch = MutableStateFlow(1.0f)
    val speechPitch: StateFlow<Float> = _speechPitch.asStateFlow()

    private val _generationState = MutableStateFlow(GenerationState())
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedInitialPresentationsIfEmpty()
        }
    }

    fun navigateTo(screen: Screen) {
        tts.stopSpeaking()
        _currentScreen.value = screen
        if (screen is Screen.Studio) {
            loadPresentation(screen.presentationId)
        } else if (screen is Screen.Player) {
            loadPresentation(screen.presentationId)
            _selectedLanguage.value = SupportedLanguage.fromCode(screen.languageCode)
        }
    }

    fun handleIncomingUri(uri: Uri, fileName: String?) {
        viewModelScope.launch {
            try {
                _statusMessage.value = "جاري استيراد ملف البوربوينت..."
                val id = repository.importFromPptx(uri, fileName)
                _statusMessage.value = "تم استيراد العرض بنجاح!"
                navigateTo(Screen.Studio(id))
            } catch (e: Exception) {
                Log.e(TAG, "Error importing incoming URI", e)
                _statusMessage.value = "فشل استيراد الملف: ${e.message}"
            }
        }
    }

    fun loadPresentation(id: Long) {
        viewModelScope.launch {
            repository.observePresentation(id).collect { pres ->
                _activePresentation.value = pres
                if (pres != null) {
                    _selectedLanguage.value = SupportedLanguage.fromCode(pres.currentLanguage)
                }
            }
        }
        viewModelScope.launch {
            repository.observeSlides(id).collect { slides ->
                _activeSlides.value = slides
            }
        }
    }

    fun setSelectedLanguage(language: SupportedLanguage) {
        _selectedLanguage.value = language
    }

    fun setSelectedTheme(theme: SlideVideoTheme) {
        _selectedTheme.value = theme
    }

    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate
    }

    fun setSpeechPitch(pitch: Float) {
        _speechPitch.value = pitch
    }

    fun previewSlideVoice(slide: Slide) {
        viewModelScope.launch {
            val effectiveCode = com.example.data.ai.LanguageDetector.resolveEffectiveLanguageCode(
                _selectedLanguage.value.code,
                slide.fullVerbatimText
            )
            tts.speakText(
                text = slide.fullVerbatimText,
                languageCode = effectiveCode,
                speechRate = _speechRate.value,
                speechPitch = _speechPitch.value
            )
        }
    }

    fun updateSlideText(presentationId: Long, slideIndex: Int, newTitle: String, newVerbatimText: String) {
        viewModelScope.launch {
            repository.updateSlideText(presentationId, slideIndex, newTitle, newVerbatimText)
            _statusMessage.value = "تم حفظ تعديلات الشريحة بنجاح!"
            // Reload slides
            loadPresentation(presentationId)
        }
    }

    fun speakCustomText(text: String) {
        viewModelScope.launch {
            val effectiveCode = com.example.data.ai.LanguageDetector.resolveEffectiveLanguageCode(
                _selectedLanguage.value.code,
                text
            )
            tts.speakText(
                text = text,
                languageCode = effectiveCode,
                speechRate = _speechRate.value,
                speechPitch = _speechPitch.value
            )
        }
    }

    fun stopVoicePreview() {
        tts.stopSpeaking()
    }

    fun generateVideo(presentationId: Long) {
        viewModelScope.launch {
            _generationState.value = GenerationState(
                isGenerating = true,
                progress = 0.05f,
                currentStep = "بدء معالجة العرض التقديمي..."
            )

            try {
                val (videoPath, audioPath) = repository.generateVideoForLanguage(
                    presentationId = presentationId,
                    targetLanguageCode = _selectedLanguage.value.code,
                    theme = _selectedTheme.value,
                    speechRate = _speechRate.value,
                    speechPitch = _speechPitch.value,
                    onProgress = { progress, step ->
                        _generationState.value = GenerationState(
                            isGenerating = true,
                            progress = progress,
                            currentStep = step
                        )
                    }
                )

                _generationState.value = GenerationState(
                    isGenerating = false,
                    progress = 1.0f,
                    currentStep = "اكتمل إنشاء الفيديو بنجاح!",
                    outputVideoPath = videoPath
                )

                loadPresentation(presentationId)
                navigateTo(Screen.Player(presentationId, _selectedLanguage.value.code))

            } catch (e: Exception) {
                Log.e(TAG, "Error generating video", e)
                _generationState.value = GenerationState(
                    isGenerating = false,
                    error = "حدث خطأ أثناء إنشاء الفيديو: ${e.message}"
                )
            }
        }
    }

    fun deletePresentation(id: Long) {
        viewModelScope.launch {
            repository.deletePresentation(id)
            if (_activePresentation.value?.id == id) {
                _activePresentation.value = null
                _activeSlides.value = emptyList()
                _currentScreen.value = Screen.Home
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        tts.shutdown()
    }
}
