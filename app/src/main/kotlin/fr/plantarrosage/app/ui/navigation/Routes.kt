package fr.plantarrosage.app.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Destinations de l'application, typées via kotlinx-serialization.
 *
 * Les fiches et l'ajout acceptent trois provenances, distinguées par leurs paramètres plutôt que
 * par des écrans séparés : une identification Pl@ntNet (`candidateIndex`), une espèce trouvée par
 * recherche (`perenualId` / `scientificName`), ou une saisie entièrement manuelle (aucun des deux).
 */
sealed interface Route {

    @Serializable
    data object Home : Route

    @Serializable
    data object Capture : Route

    @Serializable
    data object Results : Route

    @Serializable
    data object SpeciesSearch : Route

    @Serializable
    data class Species(
        val candidateIndex: Int = -1,
        val perenualId: Int = -1,
        val scientificName: String = "",
        val commonName: String = "",
    ) : Route

    @Serializable
    data class AddPlant(
        val candidateIndex: Int = -1,
        val perenualId: Int = -1,
        val scientificName: String = "",
        val commonName: String = "",
    ) : Route {
        /** Aucune espèce rattachée : l'utilisateur nomme sa plante et fixe son rythme lui-même. */
        val isManual: Boolean
            get() = candidateIndex < 0 && perenualId <= 0 && scientificName.isBlank()
    }

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
