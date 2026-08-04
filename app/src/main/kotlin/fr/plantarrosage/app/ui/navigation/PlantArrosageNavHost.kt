package fr.plantarrosage.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import fr.plantarrosage.app.di.AppContainer
import fr.plantarrosage.app.ui.addplant.AddPlantScreen
import fr.plantarrosage.app.ui.addplant.AddPlantViewModel
import fr.plantarrosage.app.ui.capture.CaptureScreen
import fr.plantarrosage.app.ui.capture.CaptureViewModel
import fr.plantarrosage.app.ui.home.HomeScreen
import fr.plantarrosage.app.ui.home.HomeViewModel
import fr.plantarrosage.app.ui.plantdetail.PlantDetailScreen
import fr.plantarrosage.app.ui.plantdetail.PlantDetailViewModel
import fr.plantarrosage.app.ui.results.ResultsScreen
import fr.plantarrosage.app.ui.results.ResultsViewModel
import fr.plantarrosage.app.ui.search.SpeciesSearchScreen
import fr.plantarrosage.app.ui.search.SpeciesSearchViewModel
import fr.plantarrosage.app.ui.settings.SettingsScreen
import fr.plantarrosage.app.ui.settings.SettingsViewModel
import fr.plantarrosage.app.ui.species.SpeciesSheetScreen
import fr.plantarrosage.app.ui.species.SpeciesSheetViewModel

@Composable
fun PlantArrosageNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Route.Home) {

        composable<Route.Home> {
            val viewModel: HomeViewModel = viewModel(factory = ViewModelFactories.home(container))
            HomeScreen(
                viewModel = viewModel,
                onIdentify = { navController.navigate(Route.Capture) },
                onSearchSpecies = { navController.navigate(Route.SpeciesSearch) },
                onAddManually = { navController.navigate(Route.AddPlant()) },
                onOpenPlant = { id -> navController.navigate(Route.PlantDetail(id)) },
                onOpenSettings = { navController.navigate(Route.Settings) },
            )
        }

        composable<Route.Capture> {
            val viewModel: CaptureViewModel = viewModel(factory = ViewModelFactories.capture(container))
            CaptureScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onResults = { navController.navigate(Route.Results) },
                onOpenSettings = { navController.navigate(Route.Settings) },
            )
        }

        composable<Route.Results> {
            val viewModel: ResultsViewModel = viewModel(factory = ViewModelFactories.results(container))
            ResultsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSelectCandidate = { index ->
                    navController.navigate(Route.Species(candidateIndex = index))
                },
            )
        }

        composable<Route.SpeciesSearch> {
            val viewModel: SpeciesSearchViewModel =
                viewModel(factory = ViewModelFactories.speciesSearch(container))
            SpeciesSearchScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenSpecies = { perenualId, scientificName, commonName ->
                    navController.navigate(
                        Route.Species(
                            perenualId = perenualId,
                            scientificName = scientificName,
                            commonName = commonName,
                        )
                    )
                },
                onAddManually = { navController.navigate(Route.AddPlant()) },
            )
        }

        composable<Route.Species> { entry ->
            val route = entry.toRoute<Route.Species>()
            val viewModel: SpeciesSheetViewModel =
                viewModel(factory = ViewModelFactories.species(container, route))
            SpeciesSheetScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onAddToCollection = {
                    navController.navigate(
                        Route.AddPlant(
                            candidateIndex = route.candidateIndex,
                            perenualId = route.perenualId,
                            scientificName = route.scientificName,
                            commonName = route.commonName,
                        )
                    )
                },
            )
        }

        composable<Route.AddPlant> { entry ->
            val route = entry.toRoute<Route.AddPlant>()
            val viewModel: AddPlantViewModel =
                viewModel(factory = ViewModelFactories.addPlant(container, route))
            AddPlantScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSaved = { plantId ->
                    // Le parcours d'ajout ne doit pas rester dans la pile de retour : depuis la
                    // fiche de la plante, Retour ramène à l'accueil.
                    navController.navigate(Route.PlantDetail(plantId)) {
                        popUpTo(Route.Home) { inclusive = false }
                    }
                },
            )
        }

        composable<Route.PlantDetail>(
            deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.PLANT_DETAIL_PATTERN })
        ) { entry ->
            val route = entry.toRoute<Route.PlantDetail>()
            val viewModel: PlantDetailViewModel = viewModel(
                factory = ViewModelFactories.plantDetail(container, route.plantId)
            )
            PlantDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onDeleted = {
                    navController.navigate(Route.Home) {
                        popUpTo(Route.Home) { inclusive = true }
                    }
                },
            )
        }

        composable<Route.Settings> {
            val viewModel: SettingsViewModel = viewModel(factory = ViewModelFactories.settings(container))
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
