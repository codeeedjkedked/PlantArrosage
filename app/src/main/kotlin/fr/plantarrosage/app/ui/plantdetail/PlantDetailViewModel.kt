package fr.plantarrosage.app.ui.plantdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.plantarrosage.app.data.db.MyPlantEntity
import fr.plantarrosage.app.data.db.WateringEventEntity
import fr.plantarrosage.app.data.repo.MyPlantsRepository
import fr.plantarrosage.app.data.repo.PlantWithSchedule
import fr.plantarrosage.core.model.CareSheet
import fr.plantarrosage.core.model.PlantLocation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlantDetailUiState(
    val loading: Boolean = true,
    val plant: PlantWithSchedule? = null,
    val careSheet: CareSheet? = null,
    val history: List<WateringEventEntity> = emptyList(),
    val deleted: Boolean = false,
)

class PlantDetailViewModel(
    private val repository: MyPlantsRepository,
    private val plantId: Long,
) : ViewModel() {

    val state: StateFlow<PlantDetailUiState> = combine(
        repository.observePlant(plantId),
        repository.observeHistory(plantId),
    ) { entity, history ->
        if (entity == null) {
            PlantDetailUiState(loading = false, deleted = true)
        } else {
            PlantDetailUiState(
                loading = false,
                plant = repository.scheduleFor(entity),
                careSheet = repository.decodeCareSheet(entity.careJson),
                history = history,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlantDetailUiState())

    fun water() {
        viewModelScope.launch { repository.recordWatering(plantId) }
    }

    fun setCustomInterval(days: Int?) {
        viewModelScope.launch { repository.setCustomInterval(plantId, days) }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setRemindersEnabled(plantId, enabled) }
    }

    fun rename(nickname: String) {
        viewModelScope.launch { repository.rename(plantId, nickname) }
    }

    fun setLocation(location: PlantLocation) {
        viewModelScope.launch { repository.setLocation(plantId, location) }
    }

    fun delete() {
        viewModelScope.launch { repository.delete(plantId) }
    }

    /** Emplacement courant sous forme d'énumération, pour l'affichage. */
    fun locationOf(entity: MyPlantEntity): PlantLocation =
        runCatching { PlantLocation.valueOf(entity.location) }.getOrDefault(PlantLocation.INTERIEUR)
}
