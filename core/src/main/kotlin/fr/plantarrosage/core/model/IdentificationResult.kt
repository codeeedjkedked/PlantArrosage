package fr.plantarrosage.core.model

import kotlinx.serialization.Serializable

/** Une espèce proposée par Pl@ntNet, avec son score de confiance. */
@Serializable
data class IdentificationCandidate(
    /** Nom scientifique sans autorité, ex. `Monstera deliciosa`. */
    val scientificName: String,
    val genus: String?,
    val family: String?,
    /** Noms communs renvoyés par Pl@ntNet avec `lang=fr` — donc francophones en principe. */
    val commonNames: List<String> = emptyList(),
    /** Score entre 0 et 1 tel que renvoyé par l'API. */
    val score: Double,
    /** Photo de référence de l'espèce, taille moyenne. */
    val relatedImageUrl: String? = null,
) {
    val bestCommonName: String? get() = commonNames.firstOrNull()

    val confidence: ConfidenceBand get() = ConfidenceBand.of(score)
}

/**
 * Bandes de confiance affichées à l'utilisateur.
 *
 * Elles existent pour une raison précise : une identification erronée se propage en conseils
 * d'entretien erronés. L'utilisateur doit toujours voir à quel point le résultat est sûr, et
 * c'est toujours lui qui choisit le candidat — jamais l'application.
 */
enum class ConfidenceBand(val labelFr: String) {
    BONNE("Bonne confiance"),
    MOYENNE("Confiance moyenne"),
    FAIBLE("Faible confiance — à vérifier"),
    ;

    companion object {
        fun of(score: Double): ConfidenceBand = when {
            score >= 0.40 -> BONNE
            score >= 0.15 -> MOYENNE
            else -> FAIBLE
        }
    }
}

/** Réponse complète d'une identification. */
data class IdentificationResult(
    val candidates: List<IdentificationCandidate>,
    /** Identifications Pl@ntNet restantes sur la journée, quand l'API le communique. */
    val remainingRequests: Int? = null,
)
