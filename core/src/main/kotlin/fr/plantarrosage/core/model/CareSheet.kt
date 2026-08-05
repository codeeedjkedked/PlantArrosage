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
    /**
     * D'où vient [baseWateringIntervalDays].
     *
     * Toujours renseigné : sans attribution, une valeur par défaut et une donnée réelle
     * s'affichent à l'identique, et l'utilisateur ne peut pas juger de ce qu'il lit.
     */
    val baseIntervalSourceFr: String = "",
    /**
     * Faux quand aucune source ne documente l'arrosage et que l'intervalle affiché n'est qu'un
     * repli prudent. L'écran doit alors le dire, plutôt que de présenter un chiffre inventé
     * comme une donnée.
     */
    val hasWateringData: Boolean = true,
    /**
     * Vrai quand [baseWateringIntervalDays] vient de la base locale curée.
     *
     * Ce n'est pas une simple provenance : une valeur curée intègre **déjà** les traits de
     * l'espèce, tolérance à la sécheresse comprise. Leur réappliquer le facteur correspondant
     * allongerait l'intervalle une seconde fois — un aloès passerait de quatorze jours en été à
     * dix-neuf, bien au-delà de ce que recommandent les sources horticoles.
     */
    val baseIsCurated: Boolean = false,
    /** Conseil d'arrosage rédigé, issu de la base locale. */
    val wateringAdviceFr: String? = null,
    /** Erreur classique sur cette plante, quand elle vaut d'être signalée. */
    val wateringPitfallFr: String? = null,

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

    // --- Renvois vers les bases de référence ---
    val gbifId: String? = null,
    val powoId: String? = null,
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

        const val DEFAULT_INTERVAL_SOURCE = "valeur par défaut, aucune donnée disponible"

        /**
         * Fiche de repli construite sur les seules données Pl@ntNet.
         *
         * C'est la règle de dégradation la plus importante de l'application : même sans aucune
         * donnée d'entretien, la plante reste enregistrable et les rappels fonctionnent, avec un
         * intervalle par défaut que l'utilisateur peut ajuster.
         */
        fun fallbackFrom(subject: SpeciesSubject): CareSheet = CareSheet(
            scientificName = subject.scientificName,
            commonNameFr = subject.bestCommonName,
            family = subject.family,
            matchQuality = MatchQuality.NONE,
            detailLevel = DetailLevel.NONE,
            baseWateringIntervalDays = DEFAULT_INTERVAL_DAYS,
            baseIntervalSourceFr = DEFAULT_INTERVAL_SOURCE,
            hasWateringData = false,
            imageUrl = subject.imageUrl,
        )

        fun fallbackFrom(candidate: IdentificationCandidate): CareSheet =
            fallbackFrom(candidate.toSubject()).copy(
                gbifId = candidate.gbifId,
                powoId = candidate.powoId,
            )

        /**
         * Plante saisie entièrement à la main, sans espèce identifiée.
         *
         * L'utilisateur reste maître : il nomme sa plante et fixe son rythme. Le fait qu'aucune
         * base ne la connaisse ne doit pas l'empêcher de la suivre.
         */
        fun manual(name: String): CareSheet = CareSheet(
            scientificName = name.trim(),
            commonNameFr = name.trim(),
            matchQuality = MatchQuality.NONE,
            detailLevel = DetailLevel.NONE,
            baseWateringIntervalDays = DEFAULT_INTERVAL_DAYS,
            baseIntervalSourceFr = DEFAULT_INTERVAL_SOURCE,
            hasWateringData = false,
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
