package fr.plantarrosage.app.data.repo

import android.content.Context
import android.net.Uri
import fr.plantarrosage.app.data.db.WateringEventEntity
import fr.plantarrosage.app.data.media.PhotoStorage
import fr.plantarrosage.app.data.prefs.SettingsRepository
import fr.plantarrosage.core.backup.BackupCodec
import fr.plantarrosage.core.backup.BackupFile
import fr.plantarrosage.core.backup.BackupPlant
import fr.plantarrosage.core.backup.BackupSettings
import fr.plantarrosage.core.backup.BackupWateringEvent
import fr.plantarrosage.core.model.AppError
import fr.plantarrosage.core.model.PlantOrgan
import fr.plantarrosage.core.util.Outcome
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Ce qu'un export a produit, pour le dire à l'utilisateur. */
data class ExportSummary(val plantCount: Int, val photoCount: Int, val sizeBytes: Long)

/** Ce qu'un import a réellement écrit. */
data class ImportSummary(
    val addedCount: Int,
    val alreadyPresent: Int,
    val photoCount: Int,
    val settingsRestored: Boolean,
)

/**
 * Sauvegarde et restauration de la collection dans une archive ZIP.
 *
 * L'archive contient le document JSON décrit par `:core` et un dossier `photos/`. Le format ZIP
 * plutôt qu'un JSON seul : les photos représentent l'essentiel du poids et ne se réencodent pas
 * raisonnablement en base64 — un fichier de dix mégaoctets en deviendrait treize, illisible et
 * lent à écrire.
 *
 * L'utilisateur choisit lui-même l'emplacement via le sélecteur de documents du système, ce qui
 * évite toute permission de stockage.
 */
class BackupRepository(
    private val context: Context,
    private val plants: MyPlantsRepository,
    private val photoStorage: PhotoStorage,
    private val settings: SettingsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    /** Nom proposé au sélecteur de documents. */
    fun suggestedFileName(): String {
        val date = clock.instant().atZone(clock.zone).toLocalDate()
        return "plantarrosage-$date.zip"
    }

    suspend fun export(target: Uri, includeKeys: Boolean): Outcome<ExportSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val entities = plants.findAll()
                var photoCount = 0

                val backupPlants = entities.map { entity ->
                    val photoUris = plants.photosOf(entity)
                    val fileNames = photoUris.mapNotNull { uri ->
                        Uri.parse(uri).lastPathSegment
                    }
                    photoCount += fileNames.size

                    BackupPlant(
                        nickname = entity.nickname,
                        scientificName = entity.scientificName,
                        normalizedBinomial = entity.normalizedBinomial,
                        commonNameFr = entity.commonNameFr,
                        family = entity.family,
                        perenualId = entity.perenualId,
                        careJson = entity.careJson,
                        location = entity.location,
                        baseIntervalDays = entity.baseIntervalDays,
                        customIntervalDays = entity.customIntervalDays,
                        sunlightRawJson = entity.sunlightRawJson,
                        droughtTolerant = entity.droughtTolerant,
                        lastWateredAtEpochMillis = entity.lastWateredAt,
                        remindersEnabled = entity.remindersEnabled,
                        notes = entity.notes,
                        createdAtEpochMillis = entity.createdAt,
                        photoFileNames = fileNames,
                        wateringEvents = plants.historyOf(entity.id).map { event ->
                            BackupWateringEvent(
                                wateredAtEpochMillis = event.wateredAt,
                                source = event.source,
                                note = event.note,
                                intervalAtTimeDays = event.intervalAtTimeDays,
                            )
                        },
                    )
                }

                val document = BackupCodec.encode(
                    BackupFile(
                        exportedAtEpochMillis = clock.millis(),
                        plants = backupPlants,
                        settings = currentSettings(includeKeys),
                    )
                )

                var written = 0L
                context.contentResolver.openOutputStream(target, "wt")?.use { out ->
                    ZipOutputStream(out.buffered()).use { zip ->
                        zip.putNextEntry(ZipEntry(BackupFile.DOCUMENT_NAME))
                        val bytes = document.toByteArray()
                        zip.write(bytes)
                        written += bytes.size
                        zip.closeEntry()

                        // Les noms sont ceux cités par le JSON : c'est ce lien qui rend la
                        // restauration possible sur un autre appareil.
                        entities.forEach { entity ->
                            plants.photosOf(entity).forEach { uri ->
                                photoStorage.read(uri)?.let { (name, bytes) ->
                                    zip.putNextEntry(
                                        ZipEntry("${BackupFile.PHOTO_DIRECTORY}/$name")
                                    )
                                    zip.write(bytes)
                                    written += bytes.size
                                    zip.closeEntry()
                                }
                            }
                        }
                    }
                } ?: error("flux de sortie indisponible")

                ExportSummary(
                    plantCount = backupPlants.size,
                    photoCount = photoCount,
                    sizeBytes = written,
                )
            }.fold(
                onSuccess = { Outcome.Success(it) },
                onFailure = { Outcome.Failure(AppError.BackupUnreadable(it.message.orEmpty())) },
            )
        }

    suspend fun import(source: Uri, restoreSettings: Boolean): Outcome<ImportSummary> =
        withContext(Dispatchers.IO) {
            val archive = runCatching { readArchive(source) }
                .getOrElse {
                    return@withContext Outcome.Failure(
                        AppError.BackupUnreadable(it.message.orEmpty())
                    )
                }

            val document = archive.document
                ?: return@withContext Outcome.Failure(
                    AppError.BackupUnreadable("archive sans ${BackupFile.DOCUMENT_NAME}")
                )

            val file = when (val decoded = BackupCodec.decode(document)) {
                is Outcome.Success -> decoded.value
                is Outcome.Failure -> return@withContext decoded
            }

            runCatching {
                val existing = plants.findAll()
                    .map { "${it.normalizedBinomial}@${it.createdAt}" }
                    .toSet()
                val plan = BackupCodec.plan(file.plants, existing)

                var restoredPhotos = 0
                plan.toAdd.forEach { plant ->
                    // Seules les photos réellement présentes dans l'archive sont réécrites : une
                    // archive amputée doit restaurer la plante quand même, sans photo fantôme.
                    val uris = plant.photoFileNames.mapNotNull { name ->
                        archive.photos[name]?.let { bytes ->
                            restoredPhotos++
                            photoStorage.restore(name, bytes).toString()
                        }
                    }

                    plants.restore(
                        plant = plant,
                        photoUris = uris,
                        events = plant.wateringEvents.map { event ->
                            WateringEventEntity(
                                plantId = 0,
                                wateredAt = event.wateredAtEpochMillis,
                                source = event.source,
                                note = event.note,
                                intervalAtTimeDays = event.intervalAtTimeDays,
                            )
                        },
                    )
                }

                val settingsApplied = restoreSettings && file.settings != null
                if (settingsApplied) applySettings(file.settings!!)

                ImportSummary(
                    addedCount = plan.addedCount,
                    alreadyPresent = plan.alreadyPresent,
                    photoCount = restoredPhotos,
                    settingsRestored = settingsApplied,
                )
            }.fold(
                onSuccess = { Outcome.Success(it) },
                onFailure = { Outcome.Failure(AppError.BackupUnreadable(it.message.orEmpty())) },
            )
        }

    private class Archive(val document: String?, val photos: Map<String, ByteArray>)

    /**
     * Lit l'archive en mémoire.
     *
     * Les photos sont plafonnées à 1 200 px et une collection réaliste tient largement sous les
     * quelques dizaines de mégaoctets : la simplicité d'une lecture en un passage vaut mieux ici
     * qu'un traitement en flux qui devrait relire l'archive deux fois pour retrouver le JSON.
     */
    private fun readArchive(source: Uri): Archive {
        var document: String? = null
        val photos = mutableMapOf<String, ByteArray>()

        context.contentResolver.openInputStream(source)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val current: ZipEntry = entry
                    if (!current.isDirectory) {
                        val bytes = zip.readAllBytesCompat()
                        val name = current.name
                        when {
                            name.endsWith(BackupFile.DOCUMENT_NAME) -> document = bytes.decodeToString()
                            name.startsWith("${BackupFile.PHOTO_DIRECTORY}/") ->
                                photos[name.substringAfterLast('/')] = bytes
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("fichier illisible")

        return Archive(document, photos)
    }

    /** `readAllBytes` n'existe qu'à partir d'Android 13 ; la boucle marche partout. */
    private fun ZipInputStream.readAllBytesCompat(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private suspend fun currentSettings(includeKeys: Boolean) = BackupSettings(
        plantNetKey = if (includeKeys) settings.storedPlantNetKey.first().ifBlank { null } else null,
        perenualKey = if (includeKeys) settings.storedPerenualKey.first().ifBlank { null } else null,
        reminderHour = settings.currentReminderHour(),
        defaultOrgan = settings.defaultOrgan.first().name,
    )

    private suspend fun applySettings(restored: BackupSettings) {
        // Une clé absente de la sauvegarde ne doit pas effacer celle déjà saisie sur l'appareil.
        restored.plantNetKey?.takeIf { it.isNotBlank() }?.let { settings.setPlantNetKey(it) }
        restored.perenualKey?.takeIf { it.isNotBlank() }?.let { settings.setPerenualKey(it) }
        restored.reminderHour?.let { settings.setReminderHour(it) }
        restored.defaultOrgan
            ?.let { name -> runCatching { PlantOrgan.valueOf(name) }.getOrNull() }
            ?.let { settings.setDefaultOrgan(it) }
    }
}
