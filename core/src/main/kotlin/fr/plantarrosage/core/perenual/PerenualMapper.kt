package fr.plantarrosage.core.perenual

import fr.plantarrosage.core.care.FrenchLabels
import fr.plantarrosage.core.care.WateringIntervalCalculator
import fr.plantarrosage.core.matching.SpeciesListEntry
import fr.plantarrosage.core.model.CareGuideSection
import fr.plantarrosage.core.model.CareSheet
import fr.plantarrosage.core.model.DetailLevel
import fr.plantarrosage.core.model.IdentificationCandidate
import fr.plantarrosage.core.model.MatchQuality

/** Convertit les réponses Perenual en fiche d'entretien française. */
object PerenualMapper {

    private val UPGRADE_SENTINEL = Regex("(?i)upgrade")

    fun toEntries(dto: PerenualSpeciesListDto): List<SpeciesListEntry> =
        dto.data.map { summary ->
            SpeciesListEntry(
                id = summary.id,
                commonName = summary.commonName,
                scientificNames = summary.scientificName,
                cycle = summary.cycle,
                watering = summary.watering,
                sunlight = summary.sunlight,
                imageUrl = summary.defaultImage?.bestUrl,
            )
        }

    /**
     * Fiche résumée, construite sur les seuls champs de `species-list`.
     * C'est le cas normal — et non dégradé — pour toute espèce hors du palier gratuit.
     */
    fun toSummarySheet(
        candidate: IdentificationCandidate,
        entry: SpeciesListEntry,
        matchQuality: MatchQuality,
    ): CareSheet {
        // Pas de repère chiffré dans `species-list` : l'énumération `watering` suffit à dériver
        // un intervalle, ce qui rend la fiche résumée pleinement exploitable.
        val (baseDays, _) = WateringIntervalCalculator.baseIntervalDays(
            benchmarkValue = null,
            benchmarkUnit = null,
            wateringEnum = entry.watering.sanitized(),
        )

        return CareSheet(
            scientificName = candidate.scientificName,
            commonNameFr = candidate.bestCommonName ?: entry.commonName,
            family = candidate.family,
            perenualId = entry.id,
            matchQuality = matchQuality,
            detailLevel = DetailLevel.SUMMARY,
            wateringRaw = entry.watering.sanitized(),
            wateringFr = FrenchLabels.watering(entry.watering.sanitized()),
            wateringBenchmarkFr = null,
            baseWateringIntervalDays = baseDays,
            sunlightRaw = entry.sunlight.sanitized(),
            sunlightFr = entry.sunlight.sanitized().mapNotNull { FrenchLabels.sunlight(it) },
            cycleFr = FrenchLabels.cycle(entry.cycle.sanitized()),
            imageUrl = entry.imageUrl ?: candidate.relatedImageUrl,
        )
    }

    /** Fiche complète : détails + guide d'entretien. */
    fun toFullSheet(
        candidate: IdentificationCandidate,
        entry: SpeciesListEntry,
        details: PerenualSpeciesDetailsDto,
        guide: PerenualCareGuideListDto?,
        matchQuality: MatchQuality,
    ): CareSheet {
        val benchmarkValue = details.wateringGeneralBenchmark?.value.sanitized()
        val benchmarkUnit = details.wateringGeneralBenchmark?.unit.sanitized()
        val wateringEnum = (details.watering ?: entry.watering).sanitized()

        val (baseDays, baseSource) = WateringIntervalCalculator.baseIntervalDays(
            benchmarkValue = benchmarkValue,
            benchmarkUnit = benchmarkUnit,
            wateringEnum = wateringEnum,
        )
        val benchmarkLabel = baseSource.takeIf { it.contains("Perenual") }

        val sunlight = details.sunlight.sanitized().ifEmpty { entry.sunlight.sanitized() }

        return CareSheet(
            scientificName = candidate.scientificName,
            commonNameFr = candidate.bestCommonName ?: details.commonName ?: entry.commonName,
            family = candidate.family ?: details.family.sanitized(),
            perenualId = entry.id,
            matchQuality = matchQuality,
            detailLevel = DetailLevel.FULL,

            wateringRaw = wateringEnum,
            wateringFr = FrenchLabels.watering(wateringEnum),
            wateringBenchmarkFr = benchmarkLabel,
            baseWateringIntervalDays = baseDays,

            sunlightRaw = sunlight,
            sunlightFr = sunlight.mapNotNull { FrenchLabels.sunlight(it) },

            cycleFr = FrenchLabels.cycle((details.cycle ?: entry.cycle).sanitized()),
            careLevelFr = FrenchLabels.careLevel(details.careLevel.sanitized()),
            growthRateFr = FrenchLabels.growthRate(details.growthRate.sanitized()),
            maintenanceFr = FrenchLabels.maintenance(details.maintenance.sanitized()),
            propagationFr = details.propagation.sanitized().mapNotNull { FrenchLabels.propagation(it) },
            pruningMonthsFr = details.pruningMonth.sanitized().mapNotNull { FrenchLabels.month(it) },
            hardinessFr = FrenchLabels.hardiness(
                details.hardiness?.min.sanitized(),
                details.hardiness?.max.sanitized(),
            ),
            indoor = details.indoor,
            droughtTolerant = details.droughtTolerant,

            poisonousToHumans = details.poisonousToHumans,
            poisonousToPets = details.poisonousToPets,

            description = details.description.sanitized(),
            descriptionLanguage = "en",
            guideSections = toGuideSections(guide),

            imageUrl = details.defaultImage?.bestUrl ?: entry.imageUrl ?: candidate.relatedImageUrl,
        )
    }

    private fun toGuideSections(guide: PerenualCareGuideListDto?): List<CareGuideSection> =
        guide?.data.orEmpty()
            .flatMap { it.section }
            .mapNotNull { section ->
                val body = section.description.sanitized() ?: return@mapNotNull null
                val type = section.type?.lowercase()?.trim() ?: return@mapNotNull null
                CareGuideSection(
                    type = type,
                    titleFr = FrenchLabels.guideSection(type) ?: type.replaceFirstChar { it.uppercase() },
                    body = body,
                )
            }

    /**
     * Neutralise la sentinelle « upgrade required » que Perenual place dans les champs premium
     * sur le palier gratuit : ce n'est pas une valeur, c'est une absence.
     */
    private fun String?.sanitized(): String? = this
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !UPGRADE_SENTINEL.containsMatchIn(it) }

    private fun List<String>.sanitized(): List<String> = mapNotNull { it.sanitized() }
}
