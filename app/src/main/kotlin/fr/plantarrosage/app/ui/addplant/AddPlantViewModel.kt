package fr.plantarrosage.app.ui.addplant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.plantarrosage.app.data.repo.IdentificationSession
import fr.plantarrosage.app.data.repo.MyPlantsRepository
import fr.plantarrosage.core.care.WateringIntervalCalculator
import fr.plantarrosage.core.model.CareSheet
import fr.plantarrosage.core.model.PlantLocation
import fr.plantarrosage.core.model.WateringPlan
import fr.plantarrosage.core.service.SpeciesCareService
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddPlantUiState(
    val loading: Boolean = true,
    val sheet: CareSheet? = null,
    val nickname: String = "",
    val location: PlantLocation = PlantLocation.INTERIEUR,
    val customIntervalDays: Int? = null,
    val wateredNow: Boolean = false,
    val remindersEnabled: Boolean = true,
    val plan: WateringPlan? = null,
    val savedPlantId: Long? = null,
    val saving: Boolean = false,
)

class AddPlantViewModel(
    private val session: IdentificationSession,
    private val careService: SpeciesCareService,
    private val repository: MyPlantsRepository,
    private val candidateIndex: Int,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(AddPlantUiState())
    val state: StateFlow<AddPlantUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val candidate = session.candidateAt(candidateIndex)
            if (candidate == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }

            // La fiche est déjà en cache après le passage sur l'écran d'espèce : cet appel ne
            // consomme aucune requête supplémentaire.
            val sheet = careService.careSheetFor(candidate).sheet

            _state.update {
                it.copy(
                    loading = false,
                    sheet = sheet,
                    nickname = sheet.commonNameFr ?: sheet.scientificName,
                )
            }
            recomputePlan()
        }
    }

    fun setNickname(value: String) {
        _state.update { it.copy(nickname = value) }
    }

    fun setLocation(location: PlantLocation) {
        _state.update { it.copy(location = location) }
        recomputePlan()
    }

    fun setCustomInterval(days: Int?) {
        _state.update { it.copy(customIntervalDays = days) }
        recomputePlan()
    }

    fun setWateredNow(value: Boolean) {
        _state.update { it.copy(wateredNow = value) }
    }

    fun setRemindersEnabled(value: Boolean) {
        _state.update { it.copy(remindersEnabled = value) }
    }

    fun save() {
        val current = _state.value
        val sheet = current.sheet ?: return
        if (current.saving) return

        _state.update { it.copy(saving = true) }

        viewModelScope.launch {
            val id = repository.add(
                nickname = current.nickname,
                careSheet = sheet,
                photoBytes = session.photoBytes,
                location = current.location,
                customIntervalDays = current.customIntervalDays,
                wateredNow = current.wateredNow,
                remindersEnabled = current.remindersEnabled,
            )
            session.clear()
            _state.update { it.copy(saving = false, savedPlantId = id) }
        }
    }

    private fun recomputePlan() {
        val current = _state.value
        val sheet = current.sheet ?: return

        _state.update {
            it.copy(
                plan = WateringIntervalCalculator.compute(
                    sheet = sheet,
                    location = current.location,
                    today = clock.instant().atZone(clock.zone).toLocalDate(),
                    userOverrideDays = current.customIntervalDays,
                )
            )
        }
    }
}
