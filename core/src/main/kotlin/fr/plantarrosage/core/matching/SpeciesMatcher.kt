package fr.plantarrosage.core.matching

import fr.plantarrosage.core.model.MatchQuality
import fr.plantarrosage.core.model.NormalizedName
import fr.plantarrosage.core.util.normalizeForComparison
import fr.plantarrosage.core.util.significantTokens

/**
 * Choisit, parmi les résultats Perenual, celui qui correspond le mieux au nom identifié par
 * Pl@ntNet.
 *
 * C'est le point de jonction entre deux services qui ne parlent pas le même langage : l'un rend
 * un nom scientifique, l'autre indexe du texte libre. Toute la fiabilité de la fiche d'entretien
 * en dépend, d'où le barème explicite et la qualité remontée jusqu'à l'écran.
 */
object SpeciesMatcher {

    /** En dessous, on considère qu'il n'y a pas de correspondance exploitable. */
    const val ACCEPT_THRESHOLD = 0.40

    /** Au-dessus, la correspondance est jugée fiable et n'est pas signalée à l'utilisateur. */
    const val CONFIDENT_THRESHOLD = 0.60

    private const val SCORE_EXACT_BINOMIAL = 1.00
    private const val SCORE_SAME_GENUS_AND_EPITHET = 0.90
    private const val SCORE_SAME_GENUS = 0.60
    private const val SCORE_COMMON_NAME = 0.50

    /**
     * @param target nom normalisé issu de Pl@ntNet
     * @param entries résultats bruts de `species-list`
     * @param targetCommonNames noms communs Pl@ntNet, utilisés en dernier recours
     * @return la meilleure entrée si son score atteint [ACCEPT_THRESHOLD], sinon `null`
     */
    fun match(
        target: NormalizedName,
        entries: List<SpeciesListEntry>,
        targetCommonNames: List<String> = emptyList(),
    ): MatchCandidate? {
        if (entries.isEmpty()) return null

        val best = entries
            .map { entry -> score(target, entry, targetCommonNames) }
            .maxByOrNull { it.score }
            ?: return null

        return best.takeIf { it.score >= ACCEPT_THRESHOLD }
    }

    private fun score(
        target: NormalizedName,
        entry: SpeciesListEntry,
        targetCommonNames: List<String>,
    ): MatchCandidate {
        val normalizedEntryNames = entry.scientificNames.mapNotNull(ScientificNameNormalizer::normalize)

        // 1. Binôme identique — la seule correspondance véritablement sûre.
        if (normalizedEntryNames.any { it.binomial == target.binomial }) {
            return MatchCandidate(entry, SCORE_EXACT_BINOMIAL, MatchQuality.EXACT)
        }

        // 2. Même genre et même épithète malgré des décorations divergentes (hybride, rang).
        if (target.epithet != null &&
            normalizedEntryNames.any { it.genus == target.genus && it.epithet == target.epithet }
        ) {
            return MatchCandidate(entry, SCORE_SAME_GENUS_AND_EPITHET, MatchQuality.EXACT)
        }

        // 3. Même genre seulement : les conseils deviennent indicatifs.
        if (normalizedEntryNames.any { it.genus == target.genus }) {
            return MatchCandidate(entry, SCORE_SAME_GENUS, MatchQuality.GENUS)
        }

        // 4. Dernier recours : un nom commun en partage.
        if (sharesCommonName(entry.commonName, targetCommonNames)) {
            return MatchCandidate(entry, SCORE_COMMON_NAME, MatchQuality.APPROXIMATE)
        }

        return MatchCandidate(entry, 0.0, MatchQuality.NONE)
    }

    private fun sharesCommonName(entryCommonName: String?, targetCommonNames: List<String>): Boolean {
        if (entryCommonName.isNullOrBlank() || targetCommonNames.isEmpty()) return false

        // Égalité stricte d'abord : elle vaut même pour des noms trop courts pour produire
        // des tokens significatifs (« ZZ »).
        val entryNormalized = entryCommonName.normalizeForComparison()
        if (targetCommonNames.any { it.normalizeForComparison() == entryNormalized }) return true

        val entryTokens = entryCommonName.significantTokens().toSet()
        if (entryTokens.isEmpty()) return false

        return targetCommonNames.any { candidate ->
            candidate.significantTokens().any { it in entryTokens }
        }
    }
}
