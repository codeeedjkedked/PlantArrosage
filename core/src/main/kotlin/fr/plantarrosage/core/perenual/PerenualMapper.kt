package fr.plantarrosage.core.perenual

import fr.plantarrosage.core.care.FrenchLabels
import fr.plantarrosage.core.care.WateringIntervalCalculator
import fr.plantarrosage.core.matching.SpeciesListEntry
import fr.plantarrosage.core.model.CareGuideSection
import fr.plantarrosage.core.model.CareSheet
import fr.plantarrosage.core.model.DetailLevel
import fr.plantarrosage.core.model.SpeciesSubject
import fr.plantarrosage.core.model.MatchQuality
import fr.plantarrosage.core.water.WateringReferenceEntry

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
        subject: SpeciesSubject,
        entry: SpeciesListEntry,
        matchQuality: MatchQuality,
        curated: WateringReferenceEntry? = null,
    ): CareSheet {
        // `species-list` ne porte aucun repère chiffré : la base locale et l'énumération
        // `watering` sont les seules sources d'intervalle ici.
        val base = WateringIntervalCalculator.resolveBase(
            curated = curated,
            benchmarkValue = null,
            benchmarkUnit = null,
            wateringEnum = entry.watering.sanitized(),
        )

        return CareSheet(
            scientificName = subject.scientificName,
            commonNameFr = subject.bestCommonName ?: entry.commonName,
            family = subject.family,
            perenualId = entry.id,
            matchQuality = matchQuality,
            detailLevel = DetailLevel.SUMMARY,
            wateringRaw = entry.watering.sanitized(),
            wateringFr = FrenchLabels.watering(entry.watering.sanitized()),
            wateringBenchmarkFr = null,
            baseWateringIntervalDays = base.days,
            baseIntervalSourceFr = base.sourceFr,
            hasWateringData = !base.isDefault,
            wateringAdviceFr = base.adviceFr,
            wateringPitfallFr = base.pitfallFr,
            sunlightRaw = entry.sunlight.sanitized(),
            sunlightFr = entry.sunlight.sanitized().mapNotNull { FrenchLabels.sunlight(it) },
            cycleFr = FrenchLabels.cycle(entry.cycle.sanitized()),
            imageUrl = entry.imageUrl ?: subject.imageUrl,
        )
    }

    /** Fiche complète : détails + guide d'entretien. */
    fun toFullSheet(
        subject: SpeciesSubject,
        entry: SpeciesListEntry,
        details: PerenualSpeciesDetailsDto,
        guide: PerenualCareGuideListDto?,
        matchQuality: MatchQuality,
        curated: WateringReferenceEntry? = null,
    ): CareSheet {
        val benchmarkValue = details.wateringGeneralBenchmark?.value.sanitized()
        val benchmarkUnit = details.wateringGeneralBenchmark?.unit.sanitized()
        val wateringEnum = (details.watering ?: entry.watering).sanitized()

        val base = WateringIntervalCalculator.resolveBase(
            curated = curated,
            benchmarkValue = benchmarkValue,
            benchmarkUnit = benchmarkUnit,
            wateringEnum = wateringEnum,
        )
        val benchmarkLabel = base.sourceFr.takeIf { it.startsWith("repère Perenual") }

        val sunlight = details.sunlight.sanitized().ifEmpty { entry.sunlight.sanitized() }

        return CareSheet(
            scientificName = subject.scientificName,
            commonNameFr = subject.bestCommonName ?: details.commonName ?: entry.commonName,
            family = subject.family ?: details.family.sanitized(),
            perenualId = entry.id,
            matchQuality = matchQuality,
            detailLevel = DetailLevel.FULL,

            wateringRaw = wateringEnum,
            wateringFr = FrenchLabels.watering(wateringEnum),
            wateringBenchmarkFr = benchmarkLabel,
            baseWateringIntervalDays = base.days,
            baseIntervalSourceFr = base.sourceFr,
            hasWateringData = !base.isDefault,
            wateringAdviceFr = base.adviceFr,
            wateringPitfallFr = base.pitfallFr,

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
            droughtTolerant = details.droughtTolerant ?: base.droughtTolerant,

            poisonousToHumans = details.poisonousToHumans,
            poisonousToPets = details.poisonousToPets,

            description = details.description.sanitized(),
            descriptionLanguage = "en",
            guideSections = toGuideSections(guide),

            imageUrl = details.defaultImage?.bestUrl ?: entry.imageUrl ?: subject.imageUrl,
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
