package fr.plantarrosage.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.plantarrosage.core.matching.SpeciesListEntry
import fr.plantarrosage.core.model.AppError
import fr.plantarrosage.core.service.SpeciesCareService
import fr.plantarrosage.core.util.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpeciesSearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<SpeciesListEntry> = emptyList(),
    val error: AppError? = null,
    /** Vrai une fois qu'une recherche a été lancée : distingue « rien trouvé » de « rien demandé ». */
    val hasSearched: Boolean = false,
) {
    val canSearch: Boolean get() = query.trim().length >= MIN_QUERY_LENGTH && !searching

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}

/**
 * Recherche d'espèce dans la base Perenual.
 *
 * La recherche n'est **pas** déclenchée à la frappe : chaque appel consomme une des cent requêtes
 * quotidiennes. L'utilisateur valide explicitement, ce qui rend la dépense prévisible.
 */
class SpeciesSearchViewModel(
    private val careService: SpeciesCareService,
) : ViewModel() {

    private val _state = MutableStateFlow(SpeciesSearchUiState())
    val state: StateFlow<SpeciesSearchUiState> = _state.asStateFlow()

    fun setQuery(value: String) {
        _state.update { it.copy(query = value, error = null) }
    }

    fun search() {
        val terme = _state.value.query.trim()
        if (terme.length < SpeciesSearchUiState.MIN_QUERY_LENGTH) return

        _state.update { it.copy(searching = true, error = null) }

        viewModelScope.launch {
            when (val outcome = careService.searchSpecies(terme)) {
                is Outcome.Success -> _state.update {
                    it.copy(searching = false, results = outcome.value, hasSearched = true)
                }
                is Outcome.Failure -> _state.update {
                    it.copy(searching = false, error = outcome.error, hasSearched = true)
                }
            }
        }
    }
}
