package fr.plantarrosage.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Replanifie le rappel quotidien après un redémarrage ou une mise à jour de l'application. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        ReminderScheduler.schedule(context.applicationContext)
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
