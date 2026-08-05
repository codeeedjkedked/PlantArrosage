package fr.plantarrosage.core.water

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Une entrée de la base d'arrosage locale.
 *
 * Cette base existe parce qu'aucune API gratuite ne publie de véritables intervalles d'arrosage :
 * l'offre gratuite de Perenual se limite à une échelle à quatre crans, dont une valeur domine
 * largement, ce qui faisait afficher le même chiffre à presque toutes les espèces.
 */
@Serializable
data class WateringReferenceEntry(
    /** Clé de recherche, déjà normalisée : minuscules, sans accent ni ponctuation. */
    @SerialName("cle") val key: String,
    @SerialName("rang") val rank: WateringRank,
    @SerialName("intervalleJours") val intervalDays: Int,
    @SerialName("toleranceSecheresse") val droughtTolerance: DroughtTolerance,
    @SerialName("conseilFr") val adviceFr: String,
    /** Erreur classique sur cette plante, quand il y en a une qui vaut d'être signalée. */
    @SerialName("pieges") val pitfallFr: String? = null,
)

/**
 * Niveau de précision de l'entrée, du plus spécifique au plus général.
 *
 * L'arrosage se joue le plus souvent au niveau du **genre** : tous les *Sansevieria* demandent le
 * même traitement. Curer par genre couvre donc beaucoup plus de terrain qu'un travail
 * espèce par espèce, à effort égal.
 */
@Serializable
enum class WateringRank {
    @SerialName("ESPECE") ESPECE,
    @SerialName("GENRE") GENRE,
    @SerialName("FAMILLE") FAMILLE,
    @SerialName("TYPE") TYPE,
}

@Serializable
enum class DroughtTolerance {
    @SerialName("ELEVEE") ELEVEE,
    @SerialName("MOYENNE") MOYENNE,
    @SerialName("FAIBLE") FAIBLE,
    ;

    /** Vrai pour les plantes qui préfèrent nettement sécher entre deux arrosages. */
    val isDroughtTolerant: Boolean get() = this == ELEVEE
}

/** Contenu complet du fichier de référence. */
@Serializable
data class WateringReferenceFile(
    @SerialName("version") val version: Int,
    @SerialName("avertissement") val disclaimerFr: String,
    @SerialName("entrees") val entries: List<WateringReferenceEntry>,
)
