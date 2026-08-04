package fr.plantarrosage.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.plantarrosage.app.PlantArrosageApplication
import fr.plantarrosage.core.care.NextWateringCalculator
import java.time.Instant

/**
 * Passe quotidienne sur la collection.
 *
 * Elle commence par **recalculer les échéances** : la saison a pu changer depuis le dernier
 * passage, ce qui modifie l'intervalle effectif sans qu'aucune donnée n'ait bougé. Ce n'est
 * qu'ensuite qu'elle notifie.
 */
class WateringReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? PlantArrosageApplication)?.container
            ?: return Result.retry()

        val repository = container.myPlantsRepository
        val clock = container.clock

        return try {
            // Recalculer d'abord : la saison a pu changer, donc l'intervalle effectif aussi.
            repository.refreshAllNextDue()

            val due = repository.findDue().map { entity ->
                DuePlantNotification(
                    plantId = entity.id,
                    nickname = entity.nickname,
                    daysSinceLastWatering = NextWateringCalculator.daysSinceLastWatering(
                        lastWateredAt = entity.lastWateredAt?.let(Instant::ofEpochMilli),
                        now = clock.instant(),
                        zone = clock.zone,
                    ),
                )
            }

            NotificationHelper.notifyDuePlants(applicationContext, due)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "rappel-arrosage-quotidien"
    }
}
