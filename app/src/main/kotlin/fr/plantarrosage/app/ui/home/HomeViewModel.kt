package fr.plantarrosage.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.plantarrosage.app.data.repo.MyPlantsRepository
import fr.plantarrosage.app.data.repo.PlantWithSchedule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val dueToday: List<PlantWithSchedule> = emptyList(),
    val soon: List<PlantWithSchedule> = emptyList(),
    val later: List<PlantWithSchedule> = emptyList(),
    val loading: Boolean = true,
) {
    val isEmpty: Boolean
        get() = !loading && dueToday.isEmpty() && soon.isEmpty() && later.isEmpty()
}

class HomeViewModel(
    private val repository: MyPlantsRepository,
) : ViewModel() {

    /** Au-delà, la plante rejoint la liste générale plutôt que la section « Bientôt ». */
    private val soonHorizonDays = 3

    val state: StateFlow<HomeUiState> = repository.observeAll()
        .map { entities ->
            val schedules = entities.map(repository::scheduleFor)
            HomeUiState(
                dueToday = schedules.filter { it.daysUntilDue <= 0 },
                soon = schedules.filter { it.daysUntilDue in 1..soonHorizonDays },
                later = schedules.filter { it.daysUntilDue > soonHorizonDays },
                loading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun recordWatering(plantId: Long) {
        viewModelScope.launch { repository.recordWatering(plantId) }
    }
}
