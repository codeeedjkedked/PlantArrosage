package fr.plantarrosage.app.data.repo

import fr.plantarrosage.app.data.db.MyPlantDao
import fr.plantarrosage.app.data.db.MyPlantEntity
import fr.plantarrosage.app.data.db.WateringEventDao
import fr.plantarrosage.app.data.db.WateringEventEntity
import fr.plantarrosage.app.data.media.PhotoStorage
import fr.plantarrosage.app.data.prefs.SettingsRepository
import fr.plantarrosage.core.backup.BackupPlant
import fr.plantarrosage.core.care.NextWateringCalculator
import fr.plantarrosage.core.care.WateringIntervalCalculator
import fr.plantarrosage.core.matching.ScientificNameNormalizer
import fr.plantarrosage.core.model.CareSheet
import fr.plantarrosage.core.model.PlantLocation
import fr.plantarrosage.core.model.WateringPlan
import fr.plantarrosage.core.net.HttpClientFactory
import fr.plantarrosage.core.water.WateringReference
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** Origine d'un arrosage enregistré. */
enum class WateringSource { MANUELLE, NOTIFICATION }

/** Plante enrichie de son calcul d'arrosage courant. */
data class PlantWithSchedule(
    val entity: MyPlantEntity,
    val plan: WateringPlan,
    val daysUntilDue: Long,
) {
    val isOverdue: Boolean get() = daysUntilDue < 0
    val isDueToday: Boolean get() = daysUntilDue == 0L
}

/**
 * Accès à la collection « Mes plantes ».
 *
 * L'intervalle effectif n'est jamais lu depuis la base : il est recalculé à chaque lecture à
 * partir de `baseIntervalDays` et de la date du jour. C'est ce qui fait que le rythme s'adapte
 * à la saison sans qu'aucune migration ni tâche de fond ne soit nécessaire.
 */
class MyPlantsRepository(
    private val plantDao: MyPlantDao,
    private val eventDao: WateringEventDao,
    private val settings: SettingsRepository,
    private val photoStorage: PhotoStorage,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    private val zone: ZoneId get() = clock.zone

    fun observeAll(): Flow<List<MyPlantEntity>> = plantDao.observeAll()

    fun observePlant(id: Long): Flow<MyPlantEntity?> = plantDao.observeById(id)

    fun observeHistory(plantId: Long): Flow<List<WateringEventEntity>> =
        eventDao.observeForPlant(plantId)

    suspend fun findById(id: Long): MyPlantEntity? = plantDao.findById(id)

    /**
     * Recalcule le plan d'arrosage courant d'une plante.
     *
     * @param sheet fiche enregistrée, quand elle a déjà été décodée : elle porte l'attribution
     *   du chiffre de base. Omise pour les listes, où décoder le JSON de chaque ligne coûterait
     *   plus que ce que l'explication rapporte.
     */
    fun scheduleFor(entity: MyPlantEntity, sheet: CareSheet? = null): PlantWithSchedule {
        val plan = WateringIntervalCalculator.compute(
            baseIntervalDays = entity.baseIntervalDays,
            baseSourceFr = sheet?.baseIntervalSourceFr?.ifBlank { null } ?: "fiche de l'espèce",
            sunlightRaw = decodeSunlight(entity.sunlightRawJson),
            droughtTolerant = entity.droughtTolerant,
            location = entity.location.toLocation(),
            today = clock.instant().atZone(zone).toLocalDate(),
            userOverrideDays = entity.customIntervalDays,
        )

        return PlantWithSchedule(
            entity = entity,
            plan = plan,
            daysUntilDue = NextWateringCalculator.daysUntil(
                nextDueAt = Instant.ofEpochMilli(entity.nextDueAt),
                now = clock.instant(),
                zone = zone,
            ),
        )
    }

    suspend fun add(
        nickname: String,
        careSheet: CareSheet,
        photoBytes: ByteArray?,
        galleryBytes: List<ByteArray> = emptyList(),
        location: PlantLocation,
        customIntervalDays: Int?,
        wateredNow: Boolean,
        remindersEnabled: Boolean,
    ): Long {
        val now = clock.instant()

        // La galerie contient déjà la couverture quand elle vient d'une identification ; on ne la
        // persiste donc qu'une fois, et on retombe sur la seule couverture si la liste est vide.
        val gallery = galleryBytes.ifEmpty { listOfNotNull(photoBytes) }
        val photoUris = photoStorage.persistAll(gallery).map { it.toString() }
        val photoUri = photoUris.firstOrNull()
        val reminderHour = settings.currentReminderHour()

        val plan = WateringIntervalCalculator.compute(
            sheet = careSheet,
            location = location,
            today = now.atZone(zone).toLocalDate(),
            userOverrideDays = customIntervalDays,
        )

        val lastWateredAt = if (wateredNow) now else null
        val nextDueAt = NextWateringCalculator.nextDue(
            lastWateredAt = lastWateredAt,
            createdAt = now,
            intervalDays = plan.effectiveIntervalDays,
            reminderHour = reminderHour,
            zone = zone,
        )

        val id = plantDao.insert(
            MyPlantEntity(
                nickname = nickname.ifBlank { careSheet.commonNameFr ?: careSheet.scientificName },
                photoUri = photoUri,
                photoUrisJson = encodeStrings(photoUris),
                scientificName = careSheet.scientificName,
                normalizedBinomial = ScientificNameNormalizer
                    .normalize(careSheet.scientificName)?.binomial.orEmpty(),
                commonNameFr = careSheet.commonNameFr,
                family = careSheet.family,
                perenualId = careSheet.perenualId,
                careJson = HttpClientFactory.json.encodeToString(CareSheet.serializer(), careSheet),
                location = location.name,
                baseIntervalDays = careSheet.baseWateringIntervalDays,
                customIntervalDays = customIntervalDays,
                sunlightRawJson = encodeSunlight(careSheet.sunlightRaw),
                droughtTolerant = careSheet.droughtTolerant,
                lastWateredAt = lastWateredAt?.toEpochMilli(),
                nextDueAt = nextDueAt.toEpochMilli(),
                remindersEnabled = remindersEnabled,
                notes = null,
                createdAt = now.toEpochMilli(),
            )
        )

        if (wateredNow) {
            eventDao.insert(
                WateringEventEntity(
                    plantId = id,
                    wateredAt = now.toEpochMilli(),
                    source = WateringSource.MANUELLE.name,
                    note = null,
                    intervalAtTimeDays = plan.effectiveIntervalDays,
                )
            )
        }

        return id
    }

    /** Enregistre un arrosage et repousse l'échéance en conséquence. */
    suspend fun recordWatering(plantId: Long, source: WateringSource = WateringSource.MANUELLE) {
        val entity = plantDao.findById(plantId) ?: return
        val now = clock.instant()
        val plan = scheduleFor(entity).plan

        val nextDueAt = NextWateringCalculator.nextDue(
            lastWateredAt = now,
            createdAt = Instant.ofEpochMilli(entity.createdAt),
            intervalDays = plan.effectiveIntervalDays,
            reminderHour = settings.currentReminderHour(),
            zone = zone,
        )

        plantDao.recordWatering(plantId, now.toEpochMilli(), nextDueAt.toEpochMilli())
        eventDao.insert(
            WateringEventEntity(
                plantId = plantId,
                wateredAt = now.toEpochMilli(),
                source = source.name,
                note = null,
                intervalAtTimeDays = plan.effectiveIntervalDays,
            )
        )
    }

    /**
     * Annule un arrosage saisi par erreur.
     *
     * Ce n'est pas une simple suppression de ligne : le dernier arrosage de la plante et son
     * échéance sont recalculés depuis les événements restants. Sinon, effacer le plus récent
     * laisserait la plante avec une date de dernier arrosage qui n'existe plus nulle part.
     */
    suspend fun deleteWateringEvent(eventId: Long) {
        val event = eventDao.findById(eventId) ?: return
        eventDao.deleteById(eventId)

        val plant = plantDao.findById(event.plantId) ?: return
        val dernierRestant = eventDao.lastWateredAt(event.plantId)
        val plan = scheduleFor(plant).plan

        val nextDueAt = NextWateringCalculator.nextDue(
            lastWateredAt = dernierRestant?.let(Instant::ofEpochMilli),
            createdAt = Instant.ofEpochMilli(plant.createdAt),
            intervalDays = plan.effectiveIntervalDays,
            reminderHour = settings.currentReminderHour(),
            zone = zone,
        )

        plantDao.recordWatering(event.plantId, dernierRestant, nextDueAt.toEpochMilli())
    }

    /** Action « Reporter à demain » de la notification. */
    suspend fun snoozeOneDay(plantId: Long) {
        val entity = plantDao.findById(plantId) ?: return
        val next = NextWateringCalculator.snoozeOneDay(Instant.ofEpochMilli(entity.nextDueAt), zone)
        plantDao.updateNextDue(plantId, next.toEpochMilli())
    }

    suspend fun setCustomInterval(plantId: Long, days: Int?) {
        val entity = plantDao.findById(plantId) ?: return
        val updated = entity.copy(customIntervalDays = days)
        plantDao.update(updated)
        refreshNextDue(updated)
    }

    suspend fun setRemindersEnabled(plantId: Long, enabled: Boolean) {
        val entity = plantDao.findById(plantId) ?: return
        plantDao.update(entity.copy(remindersEnabled = enabled))
    }

    suspend fun rename(plantId: Long, nickname: String) {
        val entity = plantDao.findById(plantId) ?: return
        plantDao.update(entity.copy(nickname = nickname.trim().ifBlank { entity.nickname }))
    }

    suspend fun setLocation(plantId: Long, location: PlantLocation) {
        val entity = plantDao.findById(plantId) ?: return
        val updated = entity.copy(location = location.name)
        plantDao.update(updated)
        refreshNextDue(updated)
    }

    suspend fun delete(plantId: Long) {
        val entity = plantDao.findById(plantId) ?: return
        photoStorage.deleteAll(photosOf(entity))
        plantDao.delete(entity) // les arrosages suivent par cascade
    }

    /**
     * Photos d'une plante, couverture en tête.
     *
     * Le repli sur `photoUri` n'est pas décoratif : toutes les plantes enregistrées avant la
     * migration ont une galerie vide et leur unique photo dans l'ancienne colonne.
     */
    fun photosOf(entity: MyPlantEntity): List<String> =
        decodeStrings(entity.photoUrisJson).ifEmpty { listOfNotNull(entity.photoUri) }

    /** Historique complet d'une plante, pour l'export. */
    suspend fun historyOf(plantId: Long): List<WateringEventEntity> = eventDao.findForPlant(plantId)

    /**
     * Réinsère une plante venue d'une sauvegarde, avec son historique.
     *
     * La date de création d'origine est **conservée** : c'est elle qui, avec l'espèce, identifie
     * la plante d'un appareil à l'autre et empêche un second import de la dupliquer. L'échéance,
     * en revanche, est recalculée pour aujourd'hui — restaurer une échéance vieille de six mois
     * déclencherait une avalanche de rappels en retard.
     */
    suspend fun restore(
        plant: BackupPlant,
        photoUris: List<String>,
        events: List<WateringEventEntity>,
    ): Long {
        val location = plant.location.toLocation()
        val lastWateredAt = plant.lastWateredAtEpochMillis
            ?: events.maxOfOrNull { it.wateredAt }

        val sheet = decodeCareSheet(plant.careJson)
        val intervalDays = plant.customIntervalDays
            ?: sheet?.let {
                WateringIntervalCalculator.compute(
                    sheet = it,
                    location = location,
                    today = clock.instant().atZone(zone).toLocalDate(),
                ).effectiveIntervalDays
            }
            ?: plant.baseIntervalDays

        val nextDueAt = NextWateringCalculator.nextDue(
            lastWateredAt = lastWateredAt?.let(Instant::ofEpochMilli),
            createdAt = clock.instant(),
            intervalDays = intervalDays,
            reminderHour = settings.currentReminderHour(),
            zone = zone,
        )

        val id = plantDao.insert(
            MyPlantEntity(
                nickname = plant.nickname,
                photoUri = photoUris.firstOrNull(),
                photoUrisJson = encodeStrings(photoUris),
                scientificName = plant.scientificName,
                normalizedBinomial = plant.normalizedBinomial,
                commonNameFr = plant.commonNameFr,
                family = plant.family,
                perenualId = plant.perenualId,
                careJson = plant.careJson,
                location = location.name,
                baseIntervalDays = plant.baseIntervalDays,
                customIntervalDays = plant.customIntervalDays,
                sunlightRawJson = plant.sunlightRawJson,
                droughtTolerant = plant.droughtTolerant,
                lastWateredAt = lastWateredAt,
                nextDueAt = nextDueAt.toEpochMilli(),
                remindersEnabled = plant.remindersEnabled,
                notes = plant.notes,
                createdAt = plant.createdAtEpochMillis,
            )
        )

        events.forEach { eventDao.insert(it.copy(id = 0, plantId = id)) }
        return id
    }

    /** Plantes dont l'échéance est atteinte, pour le worker de rappel. */
    suspend fun findDue(): List<MyPlantEntity> = plantDao.findDue(clock.millis())

    suspend fun findAll(): List<MyPlantEntity> = plantDao.findAll()

    /**
     * Recalcule et persiste l'échéance : la saison a pu changer depuis le dernier passage,
     * ce qui modifie l'intervalle effectif sans que rien d'autre n'ait bougé.
     */
    suspend fun refreshNextDue(entity: MyPlantEntity) {
        val plan = scheduleFor(entity).plan
        val nextDueAt = NextWateringCalculator.nextDue(
            lastWateredAt = entity.lastWateredAt?.let(Instant::ofEpochMilli),
            createdAt = Instant.ofEpochMilli(entity.createdAt),
            intervalDays = plan.effectiveIntervalDays,
            reminderHour = settings.currentReminderHour(),
            zone = zone,
        )
        if (nextDueAt.toEpochMilli() != entity.nextDueAt) {
            plantDao.updateNextDue(entity.id, nextDueAt.toEpochMilli())
        }
    }

    /**
     * Réaligne les intervalles de base sur la base d'arrosage locale.
     *
     * Les plantes enregistrées avant l'arrivée de cette base portent toutes la valeur par défaut
     * de sept jours, figée en table : elles ne bénéficieraient jamais de la correction sans cette
     * reprise. Un réglage manuel de l'utilisateur reste prioritaire de toute façon, et n'est donc
     * pas touché.
     *
     * @return le nombre de plantes effectivement corrigées
     */
    suspend fun refreshBaseIntervalsFromReference(): Int {
        var corrigees = 0

        plantDao.findAll().forEach { entity ->
            val curated = WateringReference.lookup(
                name = ScientificNameNormalizer.normalize(entity.scientificName),
                family = entity.family,
                typeHint = if (entity.droughtTolerant == true) "succulente" else null,
            ) ?: return@forEach

            val base = WateringIntervalCalculator.resolveBase(
                curated = curated,
                benchmarkValue = null,
                benchmarkUnit = null,
                wateringEnum = null,
            )
            if (base.days == entity.baseIntervalDays) return@forEach

            // L'instantané de fiche est mis à jour lui aussi, pour que le détail de la plante
            // affiche le conseil et l'attribution comme une fiche fraîchement récupérée.
            val sheet = decodeCareSheet(entity.careJson)?.copy(
                baseWateringIntervalDays = base.days,
                baseIntervalSourceFr = base.sourceFr,
                hasWateringData = !base.isDefault,
                wateringAdviceFr = base.adviceFr,
                wateringPitfallFr = base.pitfallFr,
            )

            val updated = entity.copy(
                baseIntervalDays = base.days,
                careJson = sheet?.let {
                    HttpClientFactory.json.encodeToString(CareSheet.serializer(), it)
                } ?: entity.careJson,
            )
            plantDao.update(updated)
            refreshNextDue(updated)
            corrigees++
        }

        return corrigees
    }

    suspend fun refreshAllNextDue() {
        plantDao.findAll().forEach { refreshNextDue(it) }
    }

    fun decodeCareSheet(json: String?): CareSheet? = json?.let {
        runCatching { HttpClientFactory.json.decodeFromString(CareSheet.serializer(), it) }.getOrNull()
    }

    private fun encodeSunlight(values: List<String>): String = encodeStrings(values)

    private fun decodeSunlight(json: String?): List<String> = decodeStrings(json)

    private fun encodeStrings(values: List<String>): String =
        HttpClientFactory.json.encodeToString(ListSerializer(String.serializer()), values)

    private fun decodeStrings(json: String?): List<String> = json?.let {
        runCatching {
            HttpClientFactory.json.decodeFromString(ListSerializer(String.serializer()), it)
        }.getOrNull()
    }.orEmpty()

    private fun String.toLocation(): PlantLocation =
        runCatching { PlantLocation.valueOf(this) }.getOrDefault(PlantLocation.INTERIEUR)
}
