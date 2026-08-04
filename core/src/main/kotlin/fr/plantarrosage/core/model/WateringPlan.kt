package fr.plantarrosage.core.model

import kotlinx.serialization.Serializable

/**
 * Résultat du calcul d'arrosage, conservé étape par étape.
 *
 * L'UI affiche le raisonnement complet (« base 7 j × 1,6 hiver × 0,85 extérieur ») : c'est ce qui
 * évite à l'utilisateur de se demander d'où sort le chiffre, et ce qui lui permet de juger s'il
 * veut le corriger.
 */
@Serializable
data class WateringPlan(
    /** Intervalle avant ajustements, issu de Perenual ou de la valeur par défaut. */
    val baseIntervalDays: Int,
    /** Explication de la provenance de la base, ex. « repère Perenual : 7 à 10 jours ». */
    val baseSourceFr: String,
    val factors: List<WateringFactor>,
    /** Intervalle réellement appliqué, borné entre 2 et 60 jours. */
    val effectiveIntervalDays: Int,
    /** Renseigné quand l'utilisateur a fixé sa propre valeur, qui l'emporte sur tout le reste. */
    val userOverrideDays: Int? = null,
) {
    val isOverridden: Boolean get() = userOverrideDays != null

    /** Ligne d'explication prête à afficher. */
    fun explanationFr(): String {
        if (isOverridden) {
            return "Tous les $effectiveIntervalDays jours — réglage personnalisé"
        }
        val suffix = factors.joinToString("") { " × ${it.formattedMultiplier()} (${it.labelFr})" }
        return "Tous les $effectiveIntervalDays jours — base $baseIntervalDays j ($baseSourceFr)$suffix"
    }
}

/** Un ajustement multiplicatif appliqué à l'intervalle de base. */
@Serializable
data class WateringFactor(
    val labelFr: String,
    val multiplier: Double,
) {
    fun formattedMultiplier(): String = String.format(java.util.Locale.FRANCE, "%.2f", multiplier)
        .trimEnd('0')
        .trimEnd(',')
}

/** Saison de l'hémisphère nord, seule prise en charge en V1. */
enum class Season(val labelFr: String, val multiplier: Double) {
    HIVER("hiver (repos végétatif)", 1.6),
    PRINTEMPS("printemps", 1.0),
    ETE("été (forte évaporation)", 0.8),
    AUTOMNE("automne", 1.2),
}
