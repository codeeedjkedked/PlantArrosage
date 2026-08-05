package fr.plantarrosage.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MyPlantEntity::class, WateringEventEntity::class, SpeciesCareEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun myPlantDao(): MyPlantDao
    abstract fun wateringEventDao(): WateringEventDao
    abstract fun speciesCareDao(): SpeciesCareDao

    companion object {
        private const val NAME = "plantarrosage.db"

        /**
         * Ajout de la galerie de photos.
         *
         * Une vraie migration plutôt qu'une reconstruction destructive : la collection et
         * l'historique d'arrosage sont irremplaçables, ils ne se retéléchargent pas. La colonne
         * naît vide, et les plantes déjà enregistrées gardent simplement leur unique photo de
         * couverture.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE my_plants ADD COLUMN photoUrisJson TEXT")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
