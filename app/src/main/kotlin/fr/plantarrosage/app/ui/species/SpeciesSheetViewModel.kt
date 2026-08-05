package fr.plantarrosage.app.ui.species

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.plantarrosage.app.data.repo.IdentificationSession
import fr.plantarrosage.core.model.AppError
import fr.plantarrosage.core.model.CareSheet
import fr.plantarrosage.core.model.SourceLinks
import fr.plantarrosage.core.model.SpeciesSubject
import fr.plantarrosage.core.service.SpeciesCareService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpeciesUiState(
    val loading: Boolean = true,
    val sheet: CareSheet? = null,
    val links: List<SourceLinks.Link> = emptyList(),
    val fromCache: Boolean = false,
    val stale: Boolean = false,
    val warning: AppError? = null,
)

/**
 * Fiche d'entretien d'une espèce, quelle que soit sa provenance : identification Pl@ntNet ou
 * recherche par nom.
 */
class SpeciesSheetViewModel(
    private val session: IdentificationSession,
    private val careService: SpeciesCareService,
    private val candidateIndex: Int,
    private val perenualId: Int,
    private val scientificName: String,
    private val commonName: String,
) : ViewModel() {

    private val _state = MutableStateFlow(SpeciesUiState())
    val state: StateFlow<SpeciesUiState> = _state.asStateFlow()

    /**
     * Les photos que l'utilisateur vient de prendre, vides lors d'une recherche par nom.
     *
     * Les rappeler ici, sous la fiche, permet de confronter ce qu'on lit à ce qu'on a devant soi
     * sans revenir en arrière — c'est le moment où l'on décide si l'identification tient.
     */
    val userPhotos: List<String>
        get() = if (candidateIndex >= 0) session.userPhotoUris else emptyList()

    init {
        load()
    }

    /**
     * La fiche n'est demandée qu'à l'ouverture d'une espèce, jamais pour les cinq résultats.
     * À lui seul, ce choix divise par cinq la consommation du quota Perenual.
     */
    fun load() {
        _state.update { it.copy(loading = true) }

        viewModelScope.launch {
            val candidate = candidateIndex.takeIf { it >= 0 }?.let(session::candidateAt)

            val result = when {
                candidate != null -> careService.careSheetFor(candidate)

                scientificName.isNotBlank() -> careService.careSheetFor(
                    SpeciesSubject(
                        scientificName = scientificName,
                        commonNames = listOfNotNull(commonName.takeIf { it.isNotBlank() }),
                        knownPerenualId = perenualId.takeIf { it > 0 },
                    )
                )

                else -> {
                    _state.value = SpeciesUiState(loading = false)
                    return@launch
                }
            }

            _state.value = SpeciesUiState(
                loading = false,
                sheet = result.sheet,
                links = SourceLinks.forSheet(
                    scientificName = result.sheet.scientificName,
                    perenualId = result.sheet.perenualId,
                    gbifId = result.sheet.gbifId,
                    powoId = result.sheet.powoId,
                ),
                fromCache = result.fromCache,
                stale = result.stale,
                warning = result.warning,
            )
        }
    }
}
