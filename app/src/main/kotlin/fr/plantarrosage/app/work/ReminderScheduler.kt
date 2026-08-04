package fr.plantarrosage.app.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Planification du rappel quotidien.
 *
 * Pas d'alarme exacte : arroser une plante n'est pas critique à la minute, et
 * `SCHEDULE_EXACT_ALARM` impose une justification auprès du Play Store qu'on s'épargne
 * volontiers. Une fenêtre souple de trois heures suffit largement.
 */
object ReminderScheduler {

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WateringReminderWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS,
            flexTimeInterval = 3,
            flexTimeIntervalUnit = TimeUnit.HOURS,
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WateringReminderWorker.UNIQUE_NAME,
            // UPDATE plutôt que KEEP : appeler cette méthode au démarrage ou après un changement
            // d'heure de rappel doit remplacer la planification existante, pas l'ignorer.
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WateringReminderWorker.UNIQUE_NAME)
    }
}
