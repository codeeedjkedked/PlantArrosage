package fr.plantarrosage.core.matching

import fr.plantarrosage.core.model.NormalizedName
import fr.plantarrosage.core.util.normalizeForComparison

/**
 * Ramène un nom scientifique quelconque à la forme binomiale `genre espèce`.
 *
 * Pl@ntNet renvoie des noms enrichis (autorité, rang infra-spécifique, cultivar, marqueur
 * d'hybride) alors que Perenual se cherche par chaîne libre. Sans ce nettoyage, le rapprochement
 * entre les deux services échoue sur la majorité des espèces.
 */
object ScientificNameNormalizer {

    /** `subsp. angustifolia`, `var. glabriusculum`, `f. rubra`, `cv. Robusta`… */
    private val RANK_MARKERS = setOf(
        "subsp", "subsp.", "ssp", "ssp.",
        "var", "var.", "subvar", "subvar.",
        "f", "f.", "forma",
        "cv", "cv.",
        "sect", "sect.", "ser", "ser.",
        "nothosubsp", "nothosubsp.",
    )

    /** Marqueurs signalant un taxon non déterminé au rang de l'espèce. */
    private val UNDETERMINED = setOf("sp", "sp.", "spp", "spp.", "indet", "indet.")

    private val HYBRID_TOKENS = setOf("×", "x", "+")

    /** Contenu entre parenthèses : toujours de l'autorité, ex. `(L.) Burm.f.` */
    private val PARENTHESES = Regex("\\([^)]*\\)")

    /** Cultivars entre apostrophes ou guillemets, ex. `'Robusta'`, `"Variegata"`. */
    private val CULTIVAR = Regex("['\"‘’“”][^'\"‘’“”]*['\"‘’“”]")

    /** Ce qu'on garde dans un token de nom : lettres et trait d'union. */
    private val NON_NAME_CHARS = Regex("[^a-z-]")

    /**
     * @return le nom décomposé, ou `null` si l'entrée ne contient aucun genre exploitable.
     */
    fun normalize(raw: String): NormalizedName? {
        val original = raw.trim().replace(Regex("\\s+"), " ")
        if (original.isEmpty()) return null

        val cleaned = original
            .let { PARENTHESES.replace(it, " ") }
            .let { CULTIVAR.replace(it, " ") }
            .normalizeForComparison()

        var isHybrid = false
        val tokens = buildList {
            for (rawToken in cleaned.split(' ')) {
                if (rawToken.isEmpty()) continue

                // Marqueur d'hybride isolé : « Rosa × damascena ».
                if (rawToken in HYBRID_TOKENS) {
                    isHybrid = true
                    continue
                }
                // Marqueur collé à l'épithète : « Rosa ×damascena ».
                var token = rawToken
                if (token.startsWith("×") || token.startsWith("+")) {
                    isHybrid = true
                    token = token.drop(1)
                }

                val isRankOrUndetermined = token in RANK_MARKERS || token in UNDETERMINED
                val stripped = NON_NAME_CHARS.replace(token, "").trim('-')
                if (stripped.isEmpty()) continue

                add(Token(text = stripped, isRankOrUndetermined = isRankOrUndetermined))
            }
        }

        val genus = tokens.firstOrNull { !it.isRankOrUndetermined }?.text ?: return null
        val genusIndex = tokens.indexOfFirst { !it.isRankOrUndetermined }

        // L'épithète est le token qui suit immédiatement le genre. Tout ce qui vient après est
        // de l'autorité ou du rang infra-spécifique, et n'entre pas dans le binôme.
        val next = tokens.getOrNull(genusIndex + 1)
        val epithet = when {
            next == null -> null
            next.isRankOrUndetermined -> null
            // Une initiale d'autorité collée au binôme (« Aloe vera L. ») n'est pas une épithète.
            next.text.length < 3 -> null
            else -> next.text
        }

        return NormalizedName(
            genus = genus,
            epithet = epithet,
            original = original,
            isHybrid = isHybrid,
        )
    }

    private data class Token(val text: String, val isRankOrUndetermined: Boolean)
}
