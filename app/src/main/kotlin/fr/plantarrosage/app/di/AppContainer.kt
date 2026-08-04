package fr.plantarrosage.app.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import fr.plantarrosage.app.data.cache.RoomSpeciesCareCache
import fr.plantarrosage.app.data.db.AppDatabase
import fr.plantarrosage.app.data.media.ImagePreparer
import fr.plantarrosage.app.data.media.PhotoStorage
import fr.plantarrosage.app.data.prefs.SettingsRepository
import fr.plantarrosage.app.data.repo.IdentificationRepository
import fr.plantarrosage.app.data.repo.IdentificationSession
import fr.plantarrosage.app.data.repo.MyPlantsRepository
import fr.plantarrosage.core.net.HttpClientFactory
import fr.plantarrosage.core.perenual.PerenualClient
import fr.plantarrosage.core.plantnet.PlantNetClient
import fr.plantarrosage.core.service.SpeciesCareService
import io.ktor.client.engine.okhttp.OkHttp
import java.time.Clock

/**
 * Graphe de dépendances, construit à la main.
 *
 * Une dizaine de singletons et six écrans ne justifient pas Hilt : ce serait un plugin Gradle,
 * du KSP, une `HiltWorkerFactory` et des annotations partout pour remplacer les quelques lignes
 * ci-dessous.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val clock: Clock = Clock.systemDefaultZone()

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val photoStorage: PhotoStorage by lazy { PhotoStorage(appContext) }

    val imagePreparer: ImagePreparer by lazy { ImagePreparer(appContext) }

    private val httpClient by lazy { HttpClientFactory.create(OkHttp.create()) }

    private val plantNetClient by lazy {
        PlantNetClient(
            httpClient = httpClient,
            apiKeyProvider = settings,
            quotaTracker = settings,
        )
    }

    /** Exposé pour le bouton « Tester la clé » des Réglages. */
    val perenualClient: PerenualClient by lazy {
        PerenualClient(
            httpClient = httpClient,
            apiKeyProvider = settings,
            quotaTracker = settings,
        )
    }

    /** Une seule instance : les Réglages la vident, le service la lit. */
    val speciesCareCache: RoomSpeciesCareCache by lazy {
        RoomSpeciesCareCache(database.speciesCareDao())
    }

    val speciesCareService: SpeciesCareService by lazy {
        SpeciesCareService(
            perenualClient = perenualClient,
            cache = speciesCareCache,
            clock = clock,
        )
    }

    /** Partage le résultat d'identification entre les écrans du parcours. */
    val identificationSession: IdentificationSession by lazy { IdentificationSession() }

    val identificationRepository: IdentificationRepository by lazy {
        IdentificationRepository(
            client = plantNetClient,
            imagePreparer = imagePreparer,
            settings = settings,
        )
    }

    val myPlantsRepository: MyPlantsRepository by lazy {
        MyPlantsRepository(
            plantDao = database.myPlantDao(),
            eventDao = database.wateringEventDao(),
            settings = settings,
            photoStorage = photoStorage,
            clock = clock,
        )
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer non fourni — vérifiez CompositionLocalProvider dans MainActivity")
}
