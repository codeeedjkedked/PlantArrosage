package fr.plantarrosage.core.care

import fr.plantarrosage.core.util.normalizeForComparison

/**
 * Traduction des valeurs énumérées de Perenual.
 *
 * L'API ne répond qu'en anglais. Les champs énumérés — arrosage, exposition, cycle, difficulté,
 * croissance, entretien — représentent l'essentiel du contenu *actionnable* d'une fiche : les
 * traduire par table est sûr, exhaustif et testable. Les textes libres (`description`, guides)
 * restent en anglais et sont présentés comme tels : traduire automatiquement des conseils
 * botaniques introduirait un risque d'erreur que le confort de lecture ne justifie pas.
 *
 * Règle générale : une valeur inconnue est renvoyée telle quelle plutôt que perdue ou fatale.
 */
object FrenchLabels {

    private val WATERING = mapOf(
        "frequent" to "Arrosage fréquent",
        "average" to "Arrosage modéré",
        "minimum" to "Arrosage rare",
        "minimal" to "Arrosage rare",
        "none" to "Pas d'arrosage",
    )

    private val SUNLIGHT = mapOf(
        "full sun" to "Plein soleil",
        "full_sun" to "Plein soleil",
        "part shade" to "Mi-ombre",
        "part_shade" to "Mi-ombre",
        "partial shade" to "Mi-ombre",
        "part sun/part shade" to "Soleil ou mi-ombre",
        "sun-part shade" to "Soleil à mi-ombre",
        "sun-part_shade" to "Soleil à mi-ombre",
        "full shade" to "Ombre",
        "full_shade" to "Ombre",
        "deep shade" to "Ombre dense",
        "filtered shade" to "Ombre filtrée",
    )

    private val CYCLE = mapOf(
        "perennial" to "Vivace",
        "annual" to "Annuelle",
        "biennial" to "Bisannuelle",
        "biannual" to "Bisannuelle",
        "herbaceous perennial" to "Vivace herbacée",
    )

    private val CARE_LEVEL = mapOf(
        "low" to "Facile",
        "medium" to "Moyen",
        "moderate" to "Moyen",
        "high" to "Exigeant",
    )

    private val GROWTH_RATE = mapOf(
        "low" to "Croissance lente",
        "moderate" to "Croissance moyenne",
        "medium" to "Croissance moyenne",
        "high" to "Croissance rapide",
    )

    private val MAINTENANCE = mapOf(
        "low" to "Entretien réduit",
        "moderate" to "Entretien moyen",
        "medium" to "Entretien moyen",
        "high" to "Entretien soutenu",
    )

    private val PROPAGATION = mapOf(
        "cutting" to "Bouturage",
        "cuttings" to "Bouturage",
        "stem cutting" to "Bouture de tige",
        "leaf cutting" to "Bouture de feuille",
        "layering" to "Marcottage",
        "air layering" to "Marcottage aérien",
        "division" to "Division",
        "seed" to "Semis",
        "seeds" to "Semis",
        "grafting" to "Greffage",
        "budding" to "Écussonnage",
        "tissue culture" to "Culture in vitro",
        "offsets" to "Rejets",
        "runners" to "Stolons",
        "bulbs" to "Bulbes",
        "root cutting" to "Bouture de racine",
    )

    private val MONTHS = mapOf(
        "january" to "janvier", "february" to "février", "march" to "mars",
        "april" to "avril", "may" to "mai", "june" to "juin",
        "july" to "juillet", "august" to "août", "september" to "septembre",
        "october" to "octobre", "november" to "novembre", "december" to "décembre",
    )

    private val GUIDE_SECTIONS = mapOf(
        "watering" to "Arrosage",
        "sunlight" to "Exposition",
        "pruning" to "Taille",
        "fertilizing" to "Fertilisation",
        "soil" to "Substrat",
    )

    fun watering(raw: String?): String? = lookup(WATERING, raw)
    fun sunlight(raw: String?): String? = lookup(SUNLIGHT, raw)
    fun cycle(raw: String?): String? = lookup(CYCLE, raw)
    fun careLevel(raw: String?): String? = lookup(CARE_LEVEL, raw)
    fun growthRate(raw: String?): String? = lookup(GROWTH_RATE, raw)
    fun maintenance(raw: String?): String? = lookup(MAINTENANCE, raw)
    fun propagation(raw: String?): String? = lookup(PROPAGATION, raw)
    fun month(raw: String?): String? = lookup(MONTHS, raw)
    fun guideSection(raw: String?): String? = lookup(GUIDE_SECTIONS, raw)

    /** Zone de rusticité USDA : on affiche la plage brute, universellement comprise des jardiniers. */
    fun hardiness(min: String?, max: String?): String? = when {
        min.isNullOrBlank() && max.isNullOrBlank() -> null
        min == max || max.isNullOrBlank() -> "Zone USDA $min"
        min.isNullOrBlank() -> "Zone USDA $max"
        else -> "Zones USDA $min à $max"
    }

    fun toxicity(poisonousToHumans: Boolean?, poisonousToPets: Boolean?): String? = when {
        poisonousToHumans == null && poisonousToPets == null -> null
        poisonousToHumans == true && poisonousToPets == true ->
            "Toxique pour les humains et les animaux domestiques"
        poisonousToHumans == true -> "Toxique pour les humains"
        poisonousToPets == true -> "Toxique pour les animaux domestiques"
        else -> "Aucune toxicité connue"
    }

    /**
     * Traduit si la valeur est connue, la renvoie inchangée sinon.
     * Une valeur imprévue doit rester lisible : mieux vaut un mot anglais qu'un champ vide.
     */
    private fun lookup(table: Map<String, String>, raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val key = raw.normalizeForComparison()
        if (key.contains("upgrade")) return null
        return table[key]
            ?: table[key.replace('_', ' ')]
            ?: table[key.replace(' ', '_')]
            ?: raw.trim()
    }
}
