package fr.plantarrosage.core.model

import kotlinx.serialization.Serializable

/**
 * Fiche d'entretien normalisée, telle que l'UI la consomme.
 *
 * Les champs énumérés sont déjà traduits en français ; les textes libres restent dans la langue
 * de la source et portent [descriptionLanguage] pour que l'UI puisse l'annoncer honnêtement.
 * On ne traduit pas automatiquement des conseils botaniques : une erreur de traduction sur un
 * conseil de soin coûte plus cher que le désagrément de lire un paragraphe en anglais.
 */
@Serializable
data class CareSheet(
    val scientificName: String,
    val commonNameFr: String? = null,
    val family: String? = null,

    val perenualId: Int? = null,
    val matchQuality: MatchQuality,
    val detailLevel: DetailLevel,

    // --- Arrosage ---
    /** Valeur brute Perenual (`Frequent`, `Average`, `Minimum`, `None`). */
    val wateringRaw: String? = null,
    /** Libellé français correspondant, ex. « Arrosage fréquent ». */
    val wateringFr: String? = null,
    /** Repère chiffré quand Perenual le fournit, ex. « 7 à 10 jours ». */
    val wateringBenchmarkFr: String? = null,
    /** Intervalle de base en jours, avant ajustements saisonniers. */
    val baseWateringIntervalDays: Int,

    // --- Lumière ---
    val sunlightRaw: List<String> = emptyList(),
    val sunlightFr: List<String> = emptyList(),

    // --- Caractéristiques générales ---
    val cycleFr: String? = null,
    val careLevelFr: String? = null,
    val growthRateFr: String? = null,
    val maintenanceFr: String? = null,
    val propagationFr: List<String> = emptyList(),
    val pruningMonthsFr: List<String> = emptyList(),
    val hardinessFr: String? = null,
    val indoor: Boolean? = null,
    val droughtTolerant: Boolean? = null,

    // --- Toxicité ---
    val poisonousToHumans: Boolean? = null,
    val poisonousToPets: Boolean? = null,

    // --- Textes libres ---
    val description: String? = null,
    /** Code ISO de la langue des textes libres. `en` pour Perenual. */
    val descriptionLanguage: String = "en",
    val guideSections: List<CareGuideSection> = emptyList(),

    val imageUrl: String? = null,
) {
    /** Vrai quand aucune donnée Perenual n'a pu être associée. */
    val isFallback: Boolean get() = detailLevel == DetailLevel.NONE

    /** Vrai quand la fiche décrit une espèce voisine et non l'espèce identifiée. */
    val isGenusApproximation: Boolean get() = matchQuality == MatchQuality.GENUS

    /** Toxicité connue pour au moins une catégorie. */
    val hasToxicityInfo: Boolean get() = poisonousToHumans != null || poisonousToPets != null

    companion object {
        /** Intervalle retenu quand aucune source ne dit rien. Volontairement prudent. */
        const val DEFAULT_INTERVAL_DAYS = 7

        /**
         * Fiche de repli construite sur les seules données Pl@ntNet.
         *
         * C'est la règle de dégradation la plus importante de l'application : même sans aucune
         * donnée d'entretien, la plante reste enregistrable et les rappels fonctionnent, avec un
         * intervalle par défaut que l'utilisateur peut ajuster.
         */
        fun fallbackFrom(candidate: IdentificationCandidate): CareSheet = CareSheet(
            scientificName = candidate.scientificName,
            commonNameFr = candidate.bestCommonName,
            family = candidate.family,
            matchQuality = MatchQuality.NONE,
            detailLevel = DetailLevel.NONE,
            baseWateringIntervalDays = DEFAULT_INTERVAL_DAYS,
            imageUrl = candidate.relatedImageUrl,
        )
    }
}

/** Une section du guide d'entretien Perenual (`watering`, `sunlight`, `pruning`). */
@Serializable
data class CareGuideSection(
    val type: String,
    /** Titre français dérivé de [type]. */
    val titleFr: String,
    /** Texte de la source, non traduit. */
    val body: String,
)
