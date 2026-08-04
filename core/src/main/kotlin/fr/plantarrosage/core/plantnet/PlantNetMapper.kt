package fr.plantarrosage.core.plantnet

import fr.plantarrosage.core.model.IdentificationCandidate
import fr.plantarrosage.core.model.IdentificationResult

/** Convertit la réponse brute de Pl@ntNet en modèle de domaine. */
object PlantNetMapper {

    /** En dessous de ce score, le candidat est trop faible pour valoir la peine d'être affiché. */
    private const val MIN_DISPLAYABLE_SCORE = 0.01

    /** Assez pour comparer sans noyer l'écran ni la bande passante. */
    private const val MAX_REFERENCE_IMAGES = 6

    fun toDomain(dto: PlantNetResponseDto): IdentificationResult {
        val candidates = dto.results
            .mapNotNull { result ->
                val scientificName = result.species?.scientificNameWithoutAuthor?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                IdentificationCandidate(
                    scientificName = scientificName,
                    genus = result.species.genus?.scientificNameWithoutAuthor,
                    family = result.species.family?.scientificNameWithoutAuthor,
                    commonNames = result.species.commonNames.filter { it.isNotBlank() },
                    score = result.score,
                    relatedImageUrls = result.images
                        .mapNotNull { it.url?.medium ?: it.url?.small ?: it.url?.original }
                        .distinct()
                        .take(MAX_REFERENCE_IMAGES),
                    gbifId = result.gbif?.id?.takeIf { it.isNotBlank() },
                    powoId = result.powo?.id?.takeIf { it.isNotBlank() },
                )
            }
            .filter { it.score >= MIN_DISPLAYABLE_SCORE }
            .sortedByDescending { it.score }

        return IdentificationResult(
            candidates = candidates,
            remainingRequests = dto.remainingIdentificationRequests,
        )
    }
}
