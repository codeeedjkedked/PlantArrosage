package fr.plantarrosage.core.model

/**
 * Ce dont on cherche la fiche d'entretien, indépendamment de la façon dont on y est arrivé.
 *
 * Trois chemins y mènent : une identification Pl@ntNet, une recherche par nom dans la base
 * Perenual, ou une saisie entièrement manuelle. Le service d'entretien ne connaît que ce type,
 * ce qui évite de dupliquer la mécanique de cache et de dégradation pour chaque chemin.
 */
data class SpeciesSubject(
    val scientificName: String,
    val commonNames: List<String> = emptyList(),
    val family: String? = null,
    val imageUrl: String? = null,
    /** Renseigné quand l'espèce vient d'une recherche : évite de la rechercher une seconde fois. */
    val knownPerenualId: Int? = null,
) {
    val bestCommonName: String? get() = commonNames.firstOrNull { it.isNotBlank() }
}

fun IdentificationCandidate.toSubject(): SpeciesSubject = SpeciesSubject(
    scientificName = scientificName,
    commonNames = commonNames,
    family = family,
    imageUrl = relatedImageUrl,
)
