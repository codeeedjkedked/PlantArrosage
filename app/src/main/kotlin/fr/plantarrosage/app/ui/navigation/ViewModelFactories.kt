package fr.plantarrosage.app.ui.navigation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.plantarrosage.app.di.AppContainer
import fr.plantarrosage.app.ui.addplant.AddPlantViewModel
import fr.plantarrosage.app.ui.capture.CaptureViewModel
import fr.plantarrosage.app.ui.home.HomeViewModel
import fr.plantarrosage.app.ui.plantdetail.PlantDetailViewModel
import fr.plantarrosage.app.ui.results.ResultsViewModel
import fr.plantarrosage.app.ui.settings.SettingsViewModel
import fr.plantarrosage.app.ui.species.SpeciesSheetViewModel

/**
 * Fabriques de ViewModels, construites à la main à partir du conteneur.
 *
 * Les paramètres de route (index de candidat, identifiant de plante) sont injectés au constructeur
 * plutôt que lus depuis un `SavedStateHandle` : c'est plus direct à suivre et suffisant tant que
 * les ViewModels ne survivent qu'à leur destination.
 */
object ViewModelFactories {

    fun home(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { HomeViewModel(container.myPlantsRepository) }
    }

    fun capture(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            CaptureViewModel(
                identificationRepository = container.identificationRepository,
                imagePreparer = container.imagePreparer,
                session = container.identificationSession,
                settings = container.settings,
            )
        }
    }

    fun results(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { ResultsViewModel(container.identificationSession) }
    }

    fun species(container: AppContainer, candidateIndex: Int): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                SpeciesSheetViewModel(
                    session = container.identificationSession,
                    careService = container.speciesCareService,
                    candidateIndex = candidateIndex,
                )
            }
        }

    fun addPlant(container: AppContainer, candidateIndex: Int): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                AddPlantViewModel(
                    session = container.identificationSession,
                    careService = container.speciesCareService,
                    repository = container.myPlantsRepository,
                    candidateIndex = candidateIndex,
                    clock = container.clock,
                )
            }
        }

    fun plantDetail(container: AppContainer, plantId: Long): ViewModelProvider.Factory =
        viewModelFactory {
            initializer { PlantDetailViewModel(container.myPlantsRepository, plantId) }
        }

    fun settings(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            SettingsViewModel(
                settings = container.settings,
                perenualClient = container.perenualClient,
                cache = container.speciesCareCache,
                plantsRepository = container.myPlantsRepository,
            )
        }
    }
}
