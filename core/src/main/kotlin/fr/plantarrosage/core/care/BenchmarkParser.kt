package fr.plantarrosage.core.care

/**
 * Lit le repère d'arrosage Perenual (`watering_general_benchmark`) et le convertit en jours.
 *
 * Le champ est notoirement sale : la valeur arrive souvent en chaîne doublement échappée
 * (`"\"7-10\""`), parfois avec un tiret cadratin, parfois en semaines, et sur l'offre gratuite
 * certains enregistrements portent la sentinelle « upgrade required » à la place d'une valeur.
 * Toutes ces formes doivent retourner `null` plutôt que lever une exception : un repère illisible
 * n'est pas une panne, on retombe simplement sur l'énumération `watering`.
 */
object BenchmarkParser {

    private val UPGRADE_SENTINEL = Regex("(?i)upgrade")
    private val RANGE_SEPARATORS = Regex("\\s*(?:-|–|—|to|a|à)\\s*", RegexOption.IGNORE_CASE)
    private val NUMBER = Regex("\\d+(?:[.,]\\d+)?")

    /** Résultat du parsing : un nombre de jours et le libellé français à afficher. */
    data class Benchmark(val days: Int, val labelFr: String)

    /**
     * @param value valeur brute du champ `value`, éventuellement échappée
     * @param unit unité brute (`days`, `weeks`, `months`…)
     */
    fun parse(value: String?, unit: String?): Benchmark? {
        if (value.isNullOrBlank()) return null
        if (UPGRADE_SENTINEL.containsMatchIn(value)) return null

        // Dépouiller les guillemets échappés : "\"7-10\"" → 7-10
        val cleaned = value.replace("\\", "").replace("\"", "").trim()
        if (cleaned.isEmpty()) return null

        val numbers = NUMBER.findAll(cleaned)
            .map { it.value.replace(',', '.').toDouble() }
            .toList()
        if (numbers.isEmpty()) return null

        // Une plage n'est retenue comme telle que si les deux nombres sont bien séparés par un
        // séparateur de plage — sinon on prend le premier nombre trouvé.
        val isRange = numbers.size >= 2 && RANGE_SEPARATORS.containsMatchIn(cleaned)
        val midpoint = if (isRange) (numbers[0] + numbers[1]) / 2.0 else numbers[0]

        val multiplier = when (unit?.lowercase()?.trim()) {
            "week", "weeks", "semaine", "semaines" -> 7.0
            "month", "months", "mois" -> 30.0
            "hour", "hours" -> 1.0 / 24.0
            else -> 1.0 // days par défaut
        }

        val days = Math.round(midpoint * multiplier).toInt()
        if (days < 1) return null

        val label = if (isRange) {
            "repère Perenual : ${format(numbers[0] * multiplier)} à ${format(numbers[1] * multiplier)} jours"
        } else {
            "repère Perenual : ${format(midpoint * multiplier)} jours"
        }

        return Benchmark(days = days, labelFr = label)
    }

    private fun format(value: Double): String {
        val rounded = Math.round(value).toInt()
        return rounded.toString()
    }
}
