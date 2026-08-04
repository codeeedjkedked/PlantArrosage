package fr.plantarrosage.app.ui.capture

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.plantarrosage.app.data.media.ImagePreparer
import fr.plantarrosage.app.data.prefs.SettingsRepository
import fr.plantarrosage.app.data.repo.IdentificationRepository
import fr.plantarrosage.app.data.repo.IdentificationSession
import fr.plantarrosage.app.data.repo.SelectedPhoto
import fr.plantarrosage.core.model.AppError
import fr.plantarrosage.core.model.PlantOrgan
import fr.plantarrosage.core.plantnet.PlantNetClient
import fr.plantarrosage.core.util.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CaptureUiState(
    val photos: List<SelectedPhoto> = emptyList(),
    val organ: PlantOrgan = PlantOrgan.AUTO,
    val identifying: Boolean = false,
    val error: AppError? = null,
    val hasPlantNetKey: Boolean = true,
    val remainingIdentifications: Int? = null,
    val navigateToResults: Boolean = false,
) {
    val canIdentify: Boolean get() = photos.isNotEmpty() && !identifying && hasPlantNetKey
    val maxPhotos: Int get() = PlantNetClient.MAX_IMAGES
    val canAddPhoto: Boolean get() = photos.size < maxPhotos
}

class CaptureViewModel(
    private val identificationRepository: IdentificationRepository,
    private val imagePreparer: ImagePreparer,
    private val session: IdentificationSession,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    organ = settings.defaultOrgan.first(),
                    hasPlantNetKey = !settings.plantNetKey().isNullOrBlank(),
                    remainingIdentifications = settings.plantNetRemaining(),
                )
            }
        }
    }

    /** À rappeler au retour sur l'écran : la clé a pu être saisie entre-temps dans les Réglages. */
    fun refreshKeyState() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    hasPlantNetKey = !settings.plantNetKey().isNullOrBlank(),
                    remainingIdentifications = settings.plantNetRemaining(),
                )
            }
        }
    }

    fun setOrgan(organ: PlantOrgan) {
        _state.update { it.copy(organ = organ) }
    }

    fun addPhoto(uri: Uri) {
        _state.update { current ->
            if (!current.canAddPhoto) return@update current
            current.copy(photos = current.photos + SelectedPhoto(uri, current.organ), error = null)
        }
    }

    fun removePhoto(index: Int) {
        _state.update { current ->
            current.copy(photos = current.photos.filterIndexed { i, _ -> i != index })
        }
    }

    fun identify() {
        val photos = _state.value.photos
        if (photos.isEmpty()) return

        _state.update { it.copy(identifying = true, error = null) }

        viewModelScope.launch {
            when (val outcome = identificationRepository.identify(photos)) {
                is Outcome.Success -> {
                    // La première photo devient l'illustration de la plante si elle est enregistrée.
                    val photoBytes = imagePreparer.prepare(photos.first().uri)
                    session.store(outcome.value, photoBytes)
                    _state.update {
                        it.copy(
                            identifying = false,
                            navigateToResults = true,
                            remainingIdentifications = outcome.value.remainingRequests
                                ?: it.remainingIdentifications,
                        )
                    }
                }
                is Outcome.Failure -> {
                    _state.update { it.copy(identifying = false, error = outcome.error) }
                }
            }
        }
    }

    fun onNavigatedToResults() {
        _state.update { it.copy(navigateToResults = false) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /** Repart d'une capture vierge après un retour depuis les résultats. */
    fun reset() {
        _state.update { it.copy(photos = emptyList(), error = null) }
    }
}
