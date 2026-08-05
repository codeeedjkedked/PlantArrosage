package fr.plantarrosage.core.care

import fr.plantarrosage.core.model.CareSheet
import fr.plantarrosage.core.model.PlantLocation
import fr.plantarrosage.core.model.WateringFactor
import fr.plantarrosage.core.model.WateringPlan
import fr.plantarrosage.core.water.WateringRank
import fr.plantarrosage.core.water.WateringReference
import fr.plantarrosage.core.water.WateringReferenceEntry
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Dérive un intervalle d'arrosage en jours à partir d'une fiche d'entretien.
 *
 * Le calcul n'est volontairement pas malin : une base issue de Perenual, quelques facteurs
 * multiplicatifs nommés, un bornage. Chaque étape est conservée dans le [WateringPlan] pour que
 * l'écran puisse afficher le raisonnement, et le résultat n'est jamais qu'une suggestion —
 * l'utilisateur garde la main via `userOverrideDays`.
 *
 * L'intervalle n'est **pas** figé à l'enregistrement de la plante : on stocke la base et on
 * recalcule à chaque évaluation, si bien que le rythme se détend seul en hiver et se resserre
 * en été sans aucune migration de données.
 */
object WateringIntervalCalculator {

    const val MIN_INTERVAL_DAYS = 2
    const val MAX_INTERVAL_DAYS = 60

    /** Repli quand Perenual ne fournit aucun repère chiffré. */
    private val ENUM_FALLBACK_DAYS = mapOf(
        "frequent" to 3,
        "average" to 7,
        "minimum" to 14,
        "minimal" to 14,
        "none" to 30,
    )

    private const val OUTDOOR_MULTIPLIER = 0.85
    private const val DROUGHT_TOLERANT_MULTIPLIER = 1.3
    private const val FULL_SUN_MULTIPLIER = 0.9
    private const val FULL_SHADE_MULTIPLIER = 1.2

    /**
     * Base retenue pour une espèce, avec sa provenance.
     *
     * [isDefault] distingue une valeur réellement documentée d'un repli prudent. Sans ce drapeau,
     * les deux s'affichaient à l'identique et l'utilisateur ne pouvait pas juger de ce qu'il lisait.
     */
    data class BaseInterval(
        val days: Int,
        val sourceFr: String,
        val isDefault: Boolean,
        val adviceFr: String? = null,
        val pitfallFr: String? = null,
        val droughtTolerant: Boolean? = null,
        /** La valeur intègre déjà les traits de l'espèce : ne pas les réappliquer. */
        val isCurated: Boolean = false,
    )

    /**
     * Arbitre entre la base locale et ce que Perenual veut bien fournir.
     *
     * Du plus spécifique au plus vague. La base locale par **genre** passe devant l'énumération
     * Perenual : une valeur curée vaut mieux qu'une échelle à quatre crans dont « Average » couvre
     * la majorité des plantes. Elle passe en revanche derrière le repère chiffré, qui est propre à
     * l'espèce quand il existe — mais il est réservé à l'offre payante et arrive donc rarement.
     */
    fun resolveBase(
        curated: WateringReferenceEntry?,
        benchmarkValue: String?,
        benchmarkUnit: String?,
        wateringEnum: String?,
    ): BaseInterval {
        fun fromCurated(entry: WateringReferenceEntry) = BaseInterval(
            days = entry.intervalDays.coerceIn(MIN_INTERVAL_DAYS, MAX_INTERVAL_DAYS),
            sourceFr = WateringReference.attribution(entry),
            isDefault = false,
            adviceFr = entry.adviceFr,
            pitfallFr = entry.pitfallFr,
            droughtTolerant = entry.droughtTolerance.isDroughtTolerant,
            isCurated = true,
        )

        // 1 et 2 — base locale, espèce puis genre.
        if (curated != null && curated.rank in PRECISE_RANKS) return fromCurated(curated)

        // 3 — repère chiffré Perenual.
        BenchmarkParser.parse(benchmarkValue, benchmarkUnit)?.let { benchmark ->
            return BaseInterval(benchmark.days, benchmark.labelFr, isDefault = false)
        }

        // 4 — énumération Perenual.
        val key = wateringEnum?.lowercase()?.trim()
        ENUM_FALLBACK_DAYS[key]?.let { days ->
            val label = FrenchLabels.watering(wateringEnum)?.lowercase() ?: "arrosage $key"
            return BaseInterval(days, "$label (Perenual)", isDefault = false)
        }

        // 5 — base locale, famille ou type.
        if (curated != null) return fromCurated(curated)

        // 6 — rien de connu : on le dit plutôt que d'inventer un chiffre.
        return BaseInterval(
            days = CareSheet.DEFAULT_INTERVAL_DAYS,
            sourceFr = CareSheet.DEFAULT_INTERVAL_SOURCE,
            isDefault = true,
        )
    }

    private val PRECISE_RANKS = setOf(WateringRank.ESPECE, WateringRank.GENRE)

    /**
     * Calcule la base en jours, avant tout ajustement.
     * Priorité au repère chiffré, repli sur l'énumération, puis sur la valeur par défaut.
     */
    fun baseIntervalDays(
        benchmarkValue: String?,
        benchmarkUnit: String?,
        wateringEnum: String?,
    ): Pair<Int, String> {
        BenchmarkParser.parse(benchmarkValue, benchmarkUnit)?.let { benchmark ->
            return benchmark.days to benchmark.labelFr
        }

        val key = wateringEnum?.lowercase()?.trim()
        ENUM_FALLBACK_DAYS[key]?.let { days ->
            val label = FrenchLabels.watering(wateringEnum)?.lowercase() ?: "arrosage $key"
            return days to label
        }

        return CareSheet.DEFAULT_INTERVAL_DAYS to "valeur par défaut, aucune donnée disponible"
    }

    /**
     * @param sheet fiche de l'espèce (fournit la base et les indices d'exposition)
     * @param location où vit la plante
     * @param today date d'évaluation, injectée pour rendre les tests déterministes
     * @param userOverrideDays réglage manuel, prioritaire sur tout le reste
     */
    fun compute(
        sheet: CareSheet,
        location: PlantLocation,
        today: LocalDate,
        userOverrideDays: Int? = null,
    ): WateringPlan = compute(
        baseIntervalDays = sheet.baseWateringIntervalDays,
        baseSourceFr = sheet.baseIntervalSourceFr.ifBlank { "fiche de l'espèce" },
        sunlightRaw = sheet.sunlightRaw,
        droughtTolerant = sheet.droughtTolerant.takeUnless { sheet.baseIsCurated },
        location = location,
        today = today,
        userOverrideDays = userOverrideDays,
    )

    /**
     * Rythme typique d'une **espèce**, hors contexte d'une plante précise.
     *
     * N'applique que les traits intrinsèques à l'espèce — tolérance à la sécheresse, exposition —
     * en laissant de côté la saison et l'emplacement, qui n'ont de sens que pour une plante
     * donnée.
     *
     * Sans cela, l'offre gratuite de Perenual ne fournissant le plus souvent qu'une énumération
     * à quatre valeurs, la quasi-totalité des espèces afficherait le même « 7 jours » et la
     * fiche perdrait tout pouvoir de distinction.
     */
    fun speciesTypical(sheet: CareSheet): WateringPlan = compute(
        baseIntervalDays = sheet.baseWateringIntervalDays,
        baseSourceFr = sheet.baseIntervalSourceFr.ifBlank { "fiche de l'espèce" },
        sunlightRaw = sheet.sunlightRaw,
        droughtTolerant = sheet.droughtTolerant.takeUnless { sheet.baseIsCurated },
        location = PlantLocation.INTERIEUR,
        today = null,
    )

    /**
     * @param today date d'évaluation, ou `null` pour ignorer la saison et l'emplacement — cas
     *   d'un rythme décrit au niveau de l'espèce et non d'une plante.
     */
    fun compute(
        baseIntervalDays: Int,
        baseSourceFr: String,
        sunlightRaw: List<String>,
        droughtTolerant: Boolean?,
        location: PlantLocation,
        today: LocalDate?,
        userOverrideDays: Int? = null,
    ): WateringPlan {
        val factors = buildList {
            if (today != null) {
                val season = SeasonResolver.seasonOf(today)
                if (season.multiplier != 1.0) {
                    add(WateringFactor(season.labelFr, season.multiplier))
                }

                if (location == PlantLocation.EXTERIEUR) {
                    add(WateringFactor("extérieur (vent et soleil)", OUTDOOR_MULTIPLIER))
                }
            }

            if (droughtTolerant == true) {
                add(WateringFactor("tolérante à la sécheresse", DROUGHT_TOLERANT_MULTIPLIER))
            }

            val sunlight = sunlightRaw.map { it.lowercase() }
            when {
                sunlight.any { it.contains("full sun") || it.contains("full_sun") } ->
                    add(WateringFactor("plein soleil", FULL_SUN_MULTIPLIER))

                sunlight.any { it.contains("full shade") || it.contains("full_shade") || it.contains("deep shade") } ->
                    add(WateringFactor("ombre", FULL_SHADE_MULTIPLIER))
            }
        }

        val adjusted = factors.fold(baseIntervalDays.toDouble()) { acc, factor -> acc * factor.multiplier }
        val computed = adjusted.roundToInt().coerceIn(MIN_INTERVAL_DAYS, MAX_INTERVAL_DAYS)

        val effective = userOverrideDays?.coerceIn(MIN_INTERVAL_DAYS, MAX_INTERVAL_DAYS) ?: computed

        return WateringPlan(
            baseIntervalDays = baseIntervalDays,
            baseSourceFr = baseSourceFr,
            factors = factors,
            effectiveIntervalDays = effective,
            userOverrideDays = userOverrideDays,
        )
    }
}
