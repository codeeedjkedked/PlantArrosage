package fr.plantarrosage.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MyPlantEntity::class, WateringEventEntity::class, SpeciesCareEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun myPlantDao(): MyPlantDao
    abstract fun wateringEventDao(): WateringEventDao
    abstract fun speciesCareDao(): SpeciesCareDao

    companion object {
        private const val NAME = "plantarrosage.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .build()
    }
}
