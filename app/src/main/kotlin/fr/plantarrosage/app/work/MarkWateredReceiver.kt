package fr.plantarrosage.app.work

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.plantarrosage.app.PlantArrosageApplication
import fr.plantarrosage.app.data.repo.WateringSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Traite les actions des notifications sans ouvrir l'application.
 *
 * « Marquer comme arrosé » depuis la barre de notifications doit rester un geste d'une seconde :
 * c'est le chemin le plus fréquent, et l'obliger à passer par l'écran serait de la friction pure.
 */
class MarkWateredReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val plantId = intent.getLongExtra(EXTRA_PLANT_ID, -1L)
        if (plantId <= 0) return

        val container = (context.applicationContext as? PlantArrosageApplication)?.container ?: return
        val repository = container.myPlantsRepository
        val action = intent.action

        // goAsync() maintient le processus vivant le temps de l'écriture en base.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_WATERED -> repository.recordWatering(plantId, WateringSource.NOTIFICATION)
                    ACTION_SNOOZE -> repository.snoozeOneDay(plantId)
                }
                NotificationHelper.cancel(context, plantId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_WATERED = "fr.plantarrosage.action.WATERED"
        private const val ACTION_SNOOZE = "fr.plantarrosage.action.SNOOZE"
        private const val EXTRA_PLANT_ID = "plantId"

        fun wateredIntent(context: Context, plantId: Long): PendingIntent =
            pendingIntent(context, plantId, ACTION_WATERED, requestOffset = 0)

        fun snoozeIntent(context: Context, plantId: Long): PendingIntent =
            pendingIntent(context, plantId, ACTION_SNOOZE, requestOffset = 1)

        private fun pendingIntent(
            context: Context,
            plantId: Long,
            action: String,
            requestOffset: Int,
        ): PendingIntent {
            val intent = Intent(context, MarkWateredReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_PLANT_ID, plantId)
            }
            // Deux actions par plante : le requestCode doit les distinguer, sinon la seconde
            // écrase la première.
            return PendingIntent.getBroadcast(
                context,
                plantId.toInt() * 2 + requestOffset,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
