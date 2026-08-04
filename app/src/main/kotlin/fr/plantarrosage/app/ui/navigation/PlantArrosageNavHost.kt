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
                onSelectCandidate = { index -> navController.navigate(Route.Species(index)) },
            )
        }

        composable<Route.Species> { entry ->
            val route = entry.toRoute<Route.Species>()
            val viewModel: SpeciesSheetViewModel = viewModel(
                factory = ViewModelFactories.species(container, route.candidateIndex)
            )
            SpeciesSheetScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onAddToCollection = { navController.navigate(Route.AddPlant(route.candidateIndex)) },
            )
        }

        composable<Route.AddPlant> { entry ->
            val route = entry.toRoute<Route.AddPlant>()
            val viewModel: AddPlantViewModel = viewModel(
                factory = ViewModelFactories.addPlant(container, route.candidateIndex)
            )
            AddPlantScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSaved = { plantId ->
                    // On revient à l'accueil puis on ouvre la plante : le parcours d'identification
                    // ne doit pas rester dans la pile de retour.
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
