package fr.plantarrosage.app.ui.navigation

import kotlinx.serialization.Serializable

/** Destinations de l'application, typées via kotlinx-serialization. */
sealed interface Route {

    @Serializable
    data object Home : Route

    @Serializable
    data object Capture : Route

    @Serializable
    data object Results : Route

    /** @param candidateIndex position du candidat retenu dans les résultats courants */
    @Serializable
    data class Species(val candidateIndex: Int) : Route

    @Serializable
    data class AddPlant(val candidateIndex: Int) : Route

    @Serializable
    data class PlantDetail(val plantId: Long) : Route

    @Serializable
    data object Settings : Route
}

/** Schéma du lien profond posé par les notifications. */
object DeepLinks {
    const val PLANT_DETAIL_PATTERN = "plantarrosage://plante/{plantId}"

    fun plantDetail(plantId: Long): String = "plantarrosage://plante/$plantId"
}
