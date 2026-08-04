package fr.plantarrosage.core.perenual

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// species-list
// ---------------------------------------------------------------------------

@Serializable
data class PerenualSpeciesListDto(
    @SerialName("data") val data: List<PerenualSpeciesSummaryDto> = emptyList(),
    @SerialName("total") val total: Int? = null,
    @SerialName("current_page") val currentPage: Int? = null,
    @SerialName("last_page") val lastPage: Int? = null,
)

@Serializable
data class PerenualSpeciesSummaryDto(
    @SerialName("id") val id: Int,
    @SerialName("common_name") val commonName: String? = null,
    @SerialName("scientific_name")
    @Serializable(with = StringOrListSerializer::class)
    val scientificName: List<String> = emptyList(),
    @SerialName("other_name")
    @Serializable(with = StringOrListSerializer::class)
    val otherName: List<String> = emptyList(),
    @SerialName("cycle") val cycle: String? = null,
    @SerialName("watering") val watering: String? = null,
    @SerialName("sunlight")
    @Serializable(with = StringOrListSerializer::class)
    val sunlight: List<String> = emptyList(),
    @SerialName("default_image") val defaultImage: PerenualImageDto? = null,
)

@Serializable
data class PerenualImageDto(
    @SerialName("thumbnail") val thumbnail: String? = null,
    @SerialName("small_url") val smallUrl: String? = null,
    @SerialName("medium_url") val mediumUrl: String? = null,
    @SerialName("regular_url") val regularUrl: String? = null,
    @SerialName("original_url") val originalUrl: String? = null,
) {
    /** Meilleure URL disponible pour un affichage de fiche. */
    val bestUrl: String?
        get() = listOfNotNull(regularUrl, mediumUrl, smallUrl, thumbnail, originalUrl)
            .firstOrNull { it.isNotBlank() }
}

// ---------------------------------------------------------------------------
// species/details/{id}
// ---------------------------------------------------------------------------

@Serializable
data class PerenualSpeciesDetailsDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("common_name") val commonName: String? = null,
    @SerialName("scientific_name")
    @Serializable(with = StringOrListSerializer::class)
    val scientificName: List<String> = emptyList(),
    @SerialName("family") val family: String? = null,
    @SerialName("cycle") val cycle: String? = null,

    @SerialName("watering") val watering: String? = null,
    @SerialName("watering_period") val wateringPeriod: String? = null,
    @SerialName("watering_general_benchmark")
    @Serializable(with = BenchmarkSerializer::class)
    val wateringGeneralBenchmark: PerenualBenchmarkDto? = null,

    @SerialName("sunlight")
    @Serializable(with = StringOrListSerializer::class)
    val sunlight: List<String> = emptyList(),

    @SerialName("care_level") val careLevel: String? = null,
    @SerialName("growth_rate") val growthRate: String? = null,
    @SerialName("maintenance") val maintenance: String? = null,

    @SerialName("drought_tolerant")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val droughtTolerant: Boolean? = null,
    @SerialName("indoor")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val indoor: Boolean? = null,
    @SerialName("poisonous_to_humans")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val poisonousToHumans: Boolean? = null,
    @SerialName("poisonous_to_pets")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val poisonousToPets: Boolean? = null,

    @SerialName("propagation")
    @Serializable(with = StringOrListSerializer::class)
    val propagation: List<String> = emptyList(),
    @SerialName("pruning_month")
    @Serializable(with = StringOrListSerializer::class)
    val pruningMonth: List<String> = emptyList(),

    @SerialName("hardiness") val hardiness: PerenualHardinessDto? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("default_image") val defaultImage: PerenualImageDto? = null,
)

@Serializable
data class PerenualBenchmarkDto(
    @SerialName("value") val value: String? = null,
    @SerialName("unit") val unit: String? = null,
)

@Serializable
data class PerenualHardinessDto(
    @SerialName("min")
    @Serializable(with = FlexibleStringSerializer::class)
    val min: String? = null,
    @SerialName("max")
    @Serializable(with = FlexibleStringSerializer::class)
    val max: String? = null,
)

// ---------------------------------------------------------------------------
// species-care-guide-list
// ---------------------------------------------------------------------------

@Serializable
data class PerenualCareGuideListDto(
    @SerialName("data") val data: List<PerenualCareGuideDto> = emptyList(),
)

@Serializable
data class PerenualCareGuideDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("species_id") val speciesId: Int? = null,
    @SerialName("common_name") val commonName: String? = null,
    @SerialName("section") val section: List<PerenualCareSectionDto> = emptyList(),
)

@Serializable
data class PerenualCareSectionDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("description") val description: String? = null,
)
