package fr.plantarrosage.core.matching

import fr.plantarrosage.core.model.MatchQuality

/**
 * Entrée de la liste d'espèces Perenual, réduite à ce dont l'appariement a besoin.
 *
 * Le matcher travaille sur ce type plutôt que sur les DTO : il reste ainsi testable sans
 * fabriquer de JSON, et insensible aux évolutions de schéma de l'API.
 */
data class SpeciesListEntry(
    val id: Int,
    val commonName: String?,
    val scientificNames: List<String>,
    val cycle: String? = null,
    val watering: String? = null,
    val sunlight: List<String> = emptyList(),
    val imageUrl: String? = null,
)

/** Entrée retenue par [SpeciesMatcher], avec le score et la qualité qui ont motivé le choix. */
data class MatchCandidate(
    val entry: SpeciesListEntry,
    val score: Double,
    val quality: MatchQuality,
)
