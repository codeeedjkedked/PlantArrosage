package fr.plantarrosage.core.model

import kotlinx.serialization.Serializable

/**
 * Nom scientifique décomposé et nettoyé, prêt pour la comparaison.
 *
 * [binomial] est la clé de rapprochement et de cache : c'est la seule forme sur laquelle
 * Pl@ntNet et Perenual peuvent se rencontrer.
 */
@Serializable
data class NormalizedName(
    /** Genre en minuscules sans accents, ex. `monstera`. Jamais vide. */
    val genus: String,
    /** Épithète spécifique, ex. `deliciosa`. `null` pour un nom de genre seul (`Sedum sp.`). */
    val epithet: String?,
    /** Chaîne d'origine, débarrassée seulement des blancs superflus. */
    val original: String,
    /** Vrai si le nom portait un marqueur d'hybride (`×`). */
    val isHybrid: Boolean = false,
) {
    /** `genus epithet`, ou `genus` seul quand l'épithète est absente. Clé de cache. */
    val binomial: String = if (epithet != null) "$genus $epithet" else genus

    /** Vrai quand seul le genre est connu — la recherche doit alors être élargie. */
    val isGenusOnly: Boolean get() = epithet == null
}

/** Qualité du rapprochement entre le nom rendu par Pl@ntNet et la fiche trouvée chez Perenual. */
enum class MatchQuality {
    /** Même espèce : le binôme correspond. */
    EXACT,

    /** Même genre, espèce différente — les conseils sont indicatifs. */
    GENUS,

    /** Rapprochement par nom commun uniquement : à prendre avec prudence. */
    APPROXIMATE,

    /** Rien trouvé chez Perenual. */
    NONE,
}

/** Richesse de la fiche effectivement obtenue, dictée par les limites de l'offre Perenual. */
enum class DetailLevel {
    /** Détails + guide d'entretien : fiche complète. */
    FULL,

    /** Seuls les champs de `species-list` sont disponibles (espèce hors du palier gratuit). */
    SUMMARY,

    /** Aucune donnée Perenual : la fiche est construite sur les seules infos Pl@ntNet. */
    NONE,
}

/** Où la plante vit : influe sur la fréquence d'arrosage. */
enum class PlantLocation { INTERIEUR, EXTERIEUR }

/** Organe photographié, transmis tel quel à Pl@ntNet. */
enum class PlantOrgan(val apiValue: String, val labelFr: String) {
    LEAF("leaf", "Feuille"),
    FLOWER("flower", "Fleur"),
    FRUIT("fruit", "Fruit"),
    BARK("bark", "Écorce"),
    AUTO("auto", "Automatique"),
}

/** Corpus Pl@ntNet interrogé. `ALL` couvre le monde entier et convient aux plantes d'intérieur. */
enum class PlantNetProject(val apiValue: String, val labelFr: String) {
    ALL("all", "Toutes flores"),
    WEUROPE("weurope", "Europe de l'Ouest"),
    K_WORLD_FLORA("k-world-flora", "Flore mondiale (K)"),
}
