package fr.plantarrosage.core.plantnet

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Réponse de `POST /v2/identify/{project}`.
 *
 * Seuls les champs réellement exploités sont déclarés ; le parseur est configuré en
 * `ignoreUnknownKeys` pour que l'ajout d'un champ côté Pl@ntNet ne casse rien.
 */
@Serializable
data class PlantNetResponseDto(
    @SerialName("bestMatch") val bestMatch: String? = null,
    @SerialName("results") val results: List<PlantNetResultDto> = emptyList(),
    @SerialName("remainingIdentificationRequests") val remainingIdentificationRequests: Int? = null,
)

@Serializable
data class PlantNetResultDto(
    @SerialName("score") val score: Double = 0.0,
    @SerialName("species") val species: PlantNetSpeciesDto? = null,
    @SerialName("images") val images: List<PlantNetImageDto> = emptyList(),
    @SerialName("gbif") val gbif: PlantNetReferenceDto? = null,
    @SerialName("powo") val powo: PlantNetReferenceDto? = null,
)

/** Renvoi vers une base externe. L'identifiant arrive tantôt en chaîne, tantôt en nombre. */
@Serializable
data class PlantNetReferenceDto(
    @SerialName("id")
    @Serializable(with = fr.plantarrosage.core.perenual.FlexibleStringSerializer::class)
    val id: String? = null,
)

@Serializable
data class PlantNetSpeciesDto(
    @SerialName("scientificNameWithoutAuthor") val scientificNameWithoutAuthor: String? = null,
    @SerialName("scientificNameAuthorship") val scientificNameAuthorship: String? = null,
    @SerialName("commonNames") val commonNames: List<String> = emptyList(),
    @SerialName("genus") val genus: PlantNetTaxonDto? = null,
    @SerialName("family") val family: PlantNetTaxonDto? = null,
)

@Serializable
data class PlantNetTaxonDto(
    @SerialName("scientificNameWithoutAuthor") val scientificNameWithoutAuthor: String? = null,
)

@Serializable
data class PlantNetImageDto(
    @SerialName("organ") val organ: String? = null,
    @SerialName("url") val url: PlantNetImageUrlDto? = null,
)

@Serializable
data class PlantNetImageUrlDto(
    @SerialName("o") val original: String? = null,
    @SerialName("m") val medium: String? = null,
    @SerialName("s") val small: String? = null,
)
