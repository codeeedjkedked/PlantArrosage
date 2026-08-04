package fr.plantarrosage.app.ui.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.plantarrosage.app.data.repo.IdentificationSession
import fr.plantarrosage.core.model.IdentificationCandidate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ResultsViewModel(
    session: IdentificationSession,
) : ViewModel() {

    val candidates: StateFlow<List<IdentificationCandidate>> = session.result
        .map { it?.candidates.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
