package fr.plantarrosage.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MyPlantDao {

    /** Les plantes en retard remontent naturellement en tête grâce au tri sur l'échéance. */
    @Query("SELECT * FROM my_plants ORDER BY nextDueAt ASC")
    fun observeAll(): Flow<List<MyPlantEntity>>

    @Query("SELECT * FROM my_plants WHERE id = :id")
    fun observeById(id: Long): Flow<MyPlantEntity?>

    @Query("SELECT * FROM my_plants WHERE id = :id")
    suspend fun findById(id: Long): MyPlantEntity?

    @Query("SELECT * FROM my_plants")
    suspend fun findAll(): List<MyPlantEntity>

    @Query("SELECT * FROM my_plants WHERE remindersEnabled = 1 AND nextDueAt <= :now ORDER BY nextDueAt ASC")
    suspend fun findDue(now: Long): List<MyPlantEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(plant: MyPlantEntity): Long

    @Update
    suspend fun update(plant: MyPlantEntity)

    @Delete
    suspend fun delete(plant: MyPlantEntity)

    @Query("UPDATE my_plants SET lastWateredAt = :wateredAt, nextDueAt = :nextDueAt WHERE id = :id")
    suspend fun recordWatering(id: Long, wateredAt: Long?, nextDueAt: Long)

    @Query("UPDATE my_plants SET nextDueAt = :nextDueAt WHERE id = :id")
    suspend fun updateNextDue(id: Long, nextDueAt: Long)
}

@Dao
interface WateringEventDao {

    @Query("SELECT * FROM watering_events WHERE plantId = :plantId ORDER BY wateredAt DESC LIMIT :limit")
    fun observeForPlant(plantId: Long, limit: Int = 50): Flow<List<WateringEventEntity>>

    @Query("SELECT * FROM watering_events WHERE id = :id")
    suspend fun findById(id: Long): WateringEventEntity?

    /** Dernier arrosage restant après une suppression, ou `null` s'il n'en reste aucun. */
    @Query("SELECT MAX(wateredAt) FROM watering_events WHERE plantId = :plantId")
    suspend fun lastWateredAt(plantId: Long): Long?

    @Insert
    suspend fun insert(event: WateringEventEntity): Long

    @Query("DELETE FROM watering_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM watering_events WHERE plantId = :plantId")
    suspend fun deleteForPlant(plantId: Long)
}

@Dao
interface SpeciesCareDao {

    @Query("SELECT * FROM species_care WHERE normalizedBinomial = :binomial")
    suspend fun find(binomial: String): SpeciesCareEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SpeciesCareEntity)

    @Query("DELETE FROM species_care")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM species_care")
    suspend fun count(): Int
}
