package fr.plantarrosage.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Une plante de la collection.
 *
 * `careJson` est un **instantané** de la fiche au moment de l'enregistrement, en plus du cache
 * d'espèces. C'est volontaire : la plante enregistrée doit rester consultable indéfiniment, même
 * si le cache est vidé, si l'API change ou si la clé expire.
 *
 * `baseIntervalDays` est conservé plutôt que l'intervalle effectif : celui-ci est recalculé à
 * chaque évaluation, de sorte que le rythme se détend seul en hiver.
 */
@Entity(
    tableName = "my_plants",
    indices = [Index("normalizedBinomial"), Index("nextDueAt")],
)
data class MyPlantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String,
    val photoUri: String?,
    val scientificName: String,
    val normalizedBinomial: String,
    val commonNameFr: String?,
    val family: String?,
    val perenualId: Int?,
    val careJson: String?,
    val location: String,
    val baseIntervalDays: Int,
    val customIntervalDays: Int?,
    val sunlightRawJson: String?,
    val droughtTolerant: Boolean?,
    val lastWateredAt: Long?,
    val nextDueAt: Long,
    val remindersEnabled: Boolean,
    val notes: String?,
    val createdAt: Long,
)

/** Un arrosage effectué. Sert d'historique et permet de recalculer l'échéance de façon fiable. */
@Entity(
    tableName = "watering_events",
    foreignKeys = [
        ForeignKey(
            entity = MyPlantEntity::class,
            parentColumns = ["id"],
            childColumns = ["plantId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("plantId")],
)
data class WateringEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long,
    val wateredAt: Long,
    /** `MANUELLE` ou `NOTIFICATION`. */
    val source: String,
    val note: String?,
    /** Intervalle en vigueur ce jour-là, utile pour comprendre l'historique a posteriori. */
    val intervalAtTimeDays: Int,
)

/**
 * Cache des fiches d'espèces, clé = binôme normalisé.
 *
 * Les entrées **négatives** (`matchQuality = NONE`, `careJson = null`) sont aussi importantes que
 * les positives : sans elles, chaque ré-identification d'une espèce absente de Perenual
 * redépenserait deux requêtes sur les cent quotidiennes.
 */
@Entity(tableName = "species_care")
data class SpeciesCareEntity(
    @PrimaryKey val normalizedBinomial: String,
    val perenualId: Int?,
    val matchedScientificName: String?,
    val matchQuality: String,
    val detailLevel: String,
    val careJson: String?,
    val imageUrl: String?,
    val fetchedAt: Long,
)
