package fr.plantarrosage.core.backup

import fr.plantarrosage.core.model.AppError
import fr.plantarrosage.core.util.Outcome
import kotlinx.serialization.json.Json

/**
 * Lecture et écriture du document de sauvegarde, et arbitrage de l'import.
 *
 * Tout ce qui se décide ici est indépendant d'Android : le fichier est du texte, la fusion est un
 * calcul sur des listes. C'est ce qui permet de le vérifier hors ligne, alors que l'archive ZIP
 * et le sélecteur de documents, eux, ne s'exécutent que sur le téléphone.
 */
object BackupCodec {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /**
     * Décode et vérifie la version.
     *
     * Un JSON valide mais issu d'un format futur est refusé plutôt qu'importé partiellement :
     * l'utilisateur préfère un refus net à une collection restaurée à moitié sans le savoir.
     */
    fun decode(text: String): Outcome<BackupFile> {
        val file = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { return Outcome.Failure(AppError.BackupUnreadable(it.message.orEmpty())) }

        if (file.version > BackupFile.CURRENT_VERSION) {
            return Outcome.Failure(AppError.BackupTooRecent(file.version))
        }
        return Outcome.Success(file)
    }

    /**
     * Ce qu'un import doit réellement écrire, compte tenu de ce qui est déjà là.
     *
     * L'import **ajoute** sans écraser : réimporter deux fois le même fichier, ou importer une
     * sauvegarde qui recoupe partiellement la collection courante, ne doit jamais dupliquer une
     * plante ni faire perdre un arrosage saisi depuis l'export.
     */
    fun plan(imported: List<BackupPlant>, existingIdentities: Set<String>): ImportPlan {
        val (skipped, toAdd) = imported.partition { it.identity in existingIdentities }

        // Une même archive peut contenir deux fois la même plante si elle a été bricolée à la
        // main : on ne garde que la première occurrence de chaque identité.
        val seen = mutableSetOf<String>()
        val deduplicated = toAdd.filter { seen.add(it.identity) }

        return ImportPlan(
            toAdd = deduplicated,
            alreadyPresent = skipped.size + (toAdd.size - deduplicated.size),
        )
    }

    data class ImportPlan(
        val toAdd: List<BackupPlant>,
        val alreadyPresent: Int,
    ) {
        val addedCount: Int get() = toAdd.size
        val photoCount: Int get() = toAdd.sumOf { it.photoFileNames.size }
    }
}
