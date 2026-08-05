package fr.plantarrosage.core.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Format de sauvegarde.
 *
 * L'archive écrite sur le téléphone est un ZIP contenant ce document sous le nom
 * `sauvegarde.json`, plus un dossier `photos/`. Le JSON ne référence les images que par leur nom
 * de fichier : c'est ce qui permet de restaurer une collection entière sur un autre appareil, où
 * les URI d'origine n'auraient plus aucun sens.
 *
 * Les noms de champs sont en français, comme le reste du domaine — un utilisateur qui ouvre son
 * fichier doit pouvoir le lire.
 */
@Serializable
data class BackupFile(
    /** Version du format, pas de l'application. Incrémentée à chaque changement incompatible. */
    @SerialName("version") val version: Int = CURRENT_VERSION,
    @SerialName("exporteLe") val exportedAtEpochMillis: Long,
    @SerialName("plantes") val plants: List<BackupPlant> = emptyList(),
    @SerialName("reglages") val settings: BackupSettings? = null,
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val DOCUMENT_NAME = "sauvegarde.json"
        const val PHOTO_DIRECTORY = "photos"
    }
}

@Serializable
data class BackupPlant(
    @SerialName("surnom") val nickname: String,
    @SerialName("nomScientifique") val scientificName: String,
    @SerialName("binomeNormalise") val normalizedBinomial: String,
    @SerialName("nomCommunFr") val commonNameFr: String? = null,
    @SerialName("famille") val family: String? = null,
    @SerialName("perenualId") val perenualId: Int? = null,
    @SerialName("ficheJson") val careJson: String? = null,
    @SerialName("emplacement") val location: String,
    @SerialName("intervalleBaseJours") val baseIntervalDays: Int,
    @SerialName("intervallePersonnaliseJours") val customIntervalDays: Int? = null,
    @SerialName("ensoleillementJson") val sunlightRawJson: String? = null,
    @SerialName("tolereSecheresse") val droughtTolerant: Boolean? = null,
    @SerialName("dernierArrosage") val lastWateredAtEpochMillis: Long? = null,
    @SerialName("rappelsActifs") val remindersEnabled: Boolean = true,
    @SerialName("notes") val notes: String? = null,
    @SerialName("creeLe") val createdAtEpochMillis: Long,
    /** Noms de fichiers dans `photos/`, couverture en tête. */
    @SerialName("photos") val photoFileNames: List<String> = emptyList(),
    @SerialName("arrosages") val wateringEvents: List<BackupWateringEvent> = emptyList(),
) {
    /**
     * Identité d'une plante entre deux appareils.
     *
     * Ni l'identifiant Room ni le surnom ne conviennent : le premier est local, le second se
     * renomme. Le couple espèce + date de création, lui, est stable et distingue deux exemplaires
     * de la même espèce ajoutés à des moments différents — le cas réel qu'on veut préserver.
     */
    val identity: String get() = "$normalizedBinomial@$createdAtEpochMillis"
}

@Serializable
data class BackupWateringEvent(
    @SerialName("arroseLe") val wateredAtEpochMillis: Long,
    @SerialName("origine") val source: String,
    @SerialName("note") val note: String? = null,
    @SerialName("intervalleDuJourJours") val intervalAtTimeDays: Int,
)

/**
 * Réglages transportés avec la collection.
 *
 * Les clés API en font partie : sans elles, restaurer sur un nouveau téléphone laisserait une
 * application incapable d'identifier quoi que ce soit. C'est aussi la raison pour laquelle
 * l'écran d'export prévient que le fichier ne doit pas être partagé.
 */
@Serializable
data class BackupSettings(
    @SerialName("clePlantNet") val plantNetKey: String? = null,
    @SerialName("clePerenual") val perenualKey: String? = null,
    @SerialName("heureRappel") val reminderHour: Int? = null,
    @SerialName("organeParDefaut") val defaultOrgan: String? = null,
)
