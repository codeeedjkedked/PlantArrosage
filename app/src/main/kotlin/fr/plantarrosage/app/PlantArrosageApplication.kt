package fr.plantarrosage.app

import android.app.Application
import fr.plantarrosage.app.di.AppContainer
import fr.plantarrosage.app.work.NotificationHelper
import fr.plantarrosage.app.work.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PlantArrosageApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        NotificationHelper.createChannel(this)
        // Idempotent : la politique UPDATE remplace simplement la planification existante.
        ReminderScheduler.schedule(this)

        applicationScope.launch {
            container.photoStorage.clearCaptures()

            // Les plantes enregistrées avant l'arrivée de la base d'arrosage locale portent
            // encore la valeur par défaut : on les réaligne une fois au démarrage.
            runCatching { container.myPlantsRepository.refreshBaseIntervalsFromReference() }
        }
    }
}
