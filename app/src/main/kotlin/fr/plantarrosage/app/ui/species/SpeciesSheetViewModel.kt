package fr.plantarrosage.app.ui.species

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.plantarrosage.app.data.repo.IdentificationSession
import fr.plantarrosage.core.model.AppError
import fr.plantarrosage.core.model.CareSheet
import fr.plantarrosage.core.model.IdentificationCandidate
import fr.plantarrosage.core.service.SpeciesCareService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpeciesUiState(
    val loading: Boolean = true,
    val candidate: IdentificationCandidate? = null,
    val sheet: CareSheet? = null,
    val fromCache: Boolean = false,
    val stale: Boolean = false,
    val warning: AppError? = null,
)

class SpeciesSheetViewModel(
    private val session: IdentificationSession,
    private val careService: SpeciesCareService,
    private val candidateIndex: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(SpeciesUiState())
    val state: StateFlow<SpeciesUiState> = _state.asStateFlow()

    init {
        load()
    }

    /**
     * La fiche n'est demandée qu'à l'ouverture d'un candidat, jamais pour les cinq résultats.
     * À lui seul, ce choix divise par cinq la consommation du quota Perenual.
     */
    fun load() {
        val candidate = session.candidateAt(candidateIndex)
        if (candidate == null) {
            _state.value = SpeciesUiState(loading = false)
            return
        }

        _state.update { it.copy(loading = true, candidate = candidate) }

        viewModelScope.launch {
            val result = careService.careSheetFor(candidate)
            _state.value = SpeciesUiState(
                loading = false,
                candidate = candidate,
                sheet = result.sheet,
                fromCache = result.fromCache,
                stale = result.stale,
                warning = result.warning,
            )
        }
    }
}
