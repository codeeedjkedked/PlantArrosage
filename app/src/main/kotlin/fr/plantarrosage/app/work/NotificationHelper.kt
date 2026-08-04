package fr.plantarrosage.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import fr.plantarrosage.app.R
import fr.plantarrosage.app.ui.MainActivity

/** Construction et envoi des rappels d'arrosage. */
object NotificationHelper {

    const val CHANNEL_ID = "rappels_arrosage"
    private const val GROUP_KEY = "fr.plantarrosage.RAPPELS"
    private const val SUMMARY_ID = 1_000_000

    /** Au-delà, les notifications individuelles sont regroupées sous un résumé. */
    private const val GROUPING_THRESHOLD = 2

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(true)
        }

        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * @param due plantes à arroser, déjà filtrées
     */
    fun notifyDuePlants(context: Context, due: List<DuePlantNotification>) {
        if (due.isEmpty() || !hasPermission(context)) return

        val manager = NotificationManagerCompat.from(context)
        val grouped = due.size > GROUPING_THRESHOLD

        due.forEach { plant ->
            manager.safeNotify(context, plant.plantId.toInt(), buildIndividual(context, plant, grouped))
        }

        if (grouped) {
            manager.safeNotify(context, SUMMARY_ID, buildSummary(context, due))
        }
    }

    fun cancel(context: Context, plantId: Long) {
        NotificationManagerCompat.from(context).cancel(plantId.toInt())
    }

    private fun buildIndividual(
        context: Context,
        plant: DuePlantNotification,
        grouped: Boolean,
    ): android.app.Notification {
        val days = plant.daysSinceLastWatering
        val body = when {
            days == null -> context.getString(R.string.notification_body_never)
            // En français, la règle CLDR range 0 dans la forme « one » : sans ce cas explicite,
            // zéro jour s'afficherait « il y a un jour ».
            days <= 0L -> context.getString(R.string.notification_body_today)
            else -> context.resources.getQuantityString(
                R.plurals.notification_body_days,
                days.toInt(),
                days.toInt(),
            )
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title, plant.nickname))
            .setContentText(body)
            .setContentIntent(deepLinkIntent(context, plant.plantId))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .apply { if (grouped) setGroup(GROUP_KEY) }
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notification_action_watered),
                MarkWateredReceiver.wateredIntent(context, plant.plantId),
            )
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notification_action_snooze),
                MarkWateredReceiver.snoozeIntent(context, plant.plantId),
            )
            .build()
    }

    private fun buildSummary(
        context: Context,
        due: List<DuePlantNotification>,
    ): android.app.Notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(
            context.resources.getQuantityString(R.plurals.notification_summary, due.size, due.size)
        )
        .setStyle(
            NotificationCompat.InboxStyle().also { style ->
                due.take(6).forEach { style.addLine(it.nickname) }
            }
        )
        .setGroup(GROUP_KEY)
        .setGroupSummary(true)
        .setAutoCancel(true)
        .build()

    /**
     * `TaskStackBuilder` garantit qu'un retour depuis le détail ramène à l'accueil plutôt que de
     * sortir de l'application.
     */
    private fun deepLinkIntent(context: Context, plantId: Long): PendingIntent {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("plantarrosage://plante/$plantId"),
            context,
            MainActivity::class.java,
        )

        return TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(
                plantId.toInt(),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        } ?: PendingIntent.getActivity(
            context,
            plantId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Poster sans permission lève une SecurityException : on l'absorbe plutôt que de planter. */
    private fun NotificationManagerCompat.safeNotify(
        context: Context,
        id: Int,
        notification: android.app.Notification,
    ) {
        if (!hasPermission(context)) return
        runCatching { notify(id, notification) }
    }
}

/** Ce dont la notification a besoin, sans traîner toute l'entité. */
data class DuePlantNotification(
    val plantId: Long,
    val nickname: String,
    val daysSinceLastWatering: Long?,
)
