package fr.plantarrosage.core.service

import fr.plantarrosage.core.model.AppError
import fr.plantarrosage.core.model.CareSheet
import fr.plantarrosage.core.model.DetailLevel
import fr.plantarrosage.core.model.IdentificationCandidate
import fr.plantarrosage.core.model.MatchQuality
import fr.plantarrosage.core.perenual.PerenualClient
import fr.plantarrosage.core.perenual.PerenualLimits
import fr.plantarrosage.core.port.CachedSpeciesCare
import fr.plantarrosage.core.support.FakeApiKeyProvider
import fr.plantarrosage.core.support.FakeQuotaTracker
import fr.plantarrosage.core.support.FakeSpeciesCareCache
import fr.plantarrosage.core.support.Fixtures
import fr.plantarrosage.core.support.RecordingMockEngine
import fr.plantarrosage.core.support.respondJson
import fr.plantarrosage.core.support.respondStatus
import io.ktor.http.HttpStatusCode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeciesCareServiceTest {

    private val now: Instant = Instant.parse("2026-04-15T10:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private val monstera = IdentificationCandidate(
        scientificName = "Monstera deliciosa",
        genus = "Monstera",
        family = "Araceae",
        commonNames = listOf("Monstera", "Faux philodendron"),
        score = 0.87,
    )

    private fun service(
        engine: RecordingMockEngine,
        cache: FakeSpeciesCareCache = FakeSpeciesCareCache(),
        quotaTracker: FakeQuotaTracker = FakeQuotaTracker(),
    ) = SpeciesCareService(
        perenualClient = PerenualClient(
            httpClient = engine.client(),
            apiKeyProvider = FakeApiKeyProvider(),
            quotaTracker = quotaTracker,
            baseUrl = "https://perenual.com/api",
        ),
        cache = cache,
        clock = clock,
    )

    /** Moteur qui répond selon le chemin demandé, comme le ferait Perenual. */
    private fun fullEngine(
        listFixture: String = "species_list_monstera.json",
        detailsFixture: String? = "species_details_monstera.json",
        guideFixture: String? = "care_guide_monstera.json",
    ) = RecordingMockEngine { request ->
        val path = request.url.encodedPath
        when {
            path.contains("species-list") -> respondJson(Fixtures.perenual(listFixture))
            path.contains("species/details") ->
                detailsFixture?.let { respondJson(Fixtures.perenual(it)) }
                    ?: respondStatus(HttpStatusCode.InternalServerError)
            path.contains("species-care-guide-list") ->
                guideFixture?.let { respondJson(Fixtures.perenual(it)) }
                    ?: respondStatus(HttpStatusCode.InternalServerError)
            else -> respondStatus(HttpStatusCode.NotFound)
        }
    }

    // ---------- Chemin nominal ----------

    @Test
    fun `construit une fiche complète depuis le réseau`() = runTest {
        val engine = fullEngine()

        val result = service(engine).careSheetFor(monstera)

        assertEquals(DetailLevel.FULL, result.sheet.detailLevel)
        assertEquals(MatchQuality.EXACT, result.sheet.matchQuality)
        assertEquals(1786, result.sheet.perenualId)
        assertEquals(9, result.sheet.baseWateringIntervalDays)
        assertFalse(result.fromCache)
        assertEquals(3, engine.callCount) // liste + détails + guide
    }

    @Test
    fun `mémorise la fiche obtenue`() = runTest {
        val cache = FakeSpeciesCareCache()

        service(fullEngine(), cache).careSheetFor(monstera)

        val cached = cache.get("monstera deliciosa")
        assertNotNull(cached)
        assertEquals(MatchQuality.EXACT, cached!!.matchQuality)
        assertEquals(DetailLevel.FULL, cached.detailLevel)
    }

    @Test
    fun `un guide indisponible n'empêche pas la fiche complète`() = runTest {
        val engine = fullEngine(guideFixture = null)

        val result = service(engine).careSheetFor(monstera)

        assertEquals(DetailLevel.FULL, result.sheet.detailLevel)
        assertTrue(result.sheet.guideSections.isEmpty())
    }

    @Test
    fun `des détails indisponibles rabattent sur la fiche résumée`() = runTest {
        val engine = fullEngine(detailsFixture = null)

        val result = service(engine).careSheetFor(monstera)

        assertEquals(DetailLevel.SUMMARY, result.sheet.detailLevel)
        assertEquals(7, result.sheet.baseWateringIntervalDays) // « Average »
    }

    // ---------- Cache ----------

    @Test
    fun `une fiche en cache évite tout appel réseau`() = runTest {
        val engine = fullEngine()
        val cache = FakeSpeciesCareCache().apply {
            seed(
                CachedSpeciesCare(
                    normalizedBinomial = "monstera deliciosa",
                    careSheet = sheet(DetailLevel.FULL, MatchQuality.EXACT),
                    matchQuality = MatchQuality.EXACT,
                    detailLevel = DetailLevel.FULL,
                    fetchedAt = now.minusSeconds(3600),
                )
            )
        }

        val result = service(engine, cache).careSheetFor(monstera)

        assertEquals(0, engine.callCount)
        assertTrue(result.fromCache)
        assertFalse(result.stale)
    }

    @Test
    fun `un cache négatif évite lui aussi tout appel réseau`() = runTest {
        val engine = fullEngine()
        val cache = FakeSpeciesCareCache().apply {
            seed(
                CachedSpeciesCare(
                    normalizedBinomial = "monstera deliciosa",
                    careSheet = null,
                    matchQuality = MatchQuality.NONE,
                    detailLevel = DetailLevel.NONE,
                    fetchedAt = now.minusSeconds(3600),
                )
            )
        }

        val result = service(engine, cache).careSheetFor(monstera)

        assertEquals(0, engine.callCount)
        assertTrue(result.sheet.isFallback)
        assertTrue(result.fromCache)
    }

    @Test
    fun `un cache positif périmé déclenche un nouvel appel`() = runTest {
        val engine = fullEngine()
        val cache = FakeSpeciesCareCache().apply {
            seed(
                CachedSpeciesCare(
                    normalizedBinomial = "monstera deliciosa",
                    careSheet = sheet(DetailLevel.FULL, MatchQuality.EXACT),
                    matchQuality = MatchQuality.EXACT,
                    detailLevel = DetailLevel.FULL,
                    fetchedAt = now.minusSeconds((PerenualLimits.POSITIVE_CACHE_TTL_DAYS + 1) * 86_400),
                )
            )
        }

        val result = service(engine, cache).careSheetFor(monstera)

        assertTrue(engine.callCount > 0)
        assertFalse(result.fromCache)
    }

    @Test
    fun `un cache négatif périmé plus tôt déclenche un nouvel appel`() = runTest {
        val engine = fullEngine()
        val ageJours = PerenualLimits.NEGATIVE_CACHE_TTL_DAYS + 1
        val cache = FakeSpeciesCareCache().apply {
            seed(
                CachedSpeciesCare(
                    normalizedBinomial = "monstera deliciosa",
                    careSheet = null,
                    matchQuality = MatchQuality.NONE,
                    detailLevel = DetailLevel.NONE,
                    fetchedAt = now.minusSeconds(ageJours * 86_400),
                )
            )
        }

        service(engine, cache).careSheetFor(monstera)

        assertTrue(engine.callCount > 0)
    }

    @Test
    fun `deux demandes concurrentes ne déclenchent qu'un seul aller-retour`() = runTest {
        val engine = fullEngine()
        val service = service(engine)

        coroutineScope {
            listOf(
                async { service.careSheetFor(monstera) },
                async { service.careSheetFor(monstera) },
                async { service.careSheetFor(monstera) },
            ).awaitAll()
        }

        // Trois requêtes au total : liste + détails + guide, pour la première demande seulement.
        assertEquals(3, engine.callCount)
    }

    // ---------- Dégradation ----------

    @Test
    fun `sans correspondance la fiche de repli reste exploitable`() = runTest {
        val engine = RecordingMockEngine { respondJson(Fixtures.perenual("species_list_empty.json")) }

        val result = service(engine).careSheetFor(monstera)

        assertTrue(result.sheet.isFallback)
        assertEquals("Monstera deliciosa", result.sheet.scientificName)
        assertEquals("Monstera", result.sheet.commonNameFr)
        assertEquals(CareSheet.DEFAULT_INTERVAL_DAYS, result.sheet.baseWateringIntervalDays)
    }

    @Test
    fun `une absence de correspondance est mémorisée pour ne pas redépenser le quota`() = runTest {
        val engine = RecordingMockEngine { respondJson(Fixtures.perenual("species_list_empty.json")) }
        val cache = FakeSpeciesCareCache()

        service(engine, cache).careSheetFor(monstera)

        val cached = cache.get("monstera deliciosa")
        assertEquals(MatchQuality.NONE, cached?.matchQuality)
    }

    @Test
    fun `le repli sur le genre n'ajoute qu'une seule requête`() = runTest {
        var appels = 0
        val engine = RecordingMockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.contains("species-list") -> {
                    appels++
                    // La recherche binomiale ne rend rien, la recherche du genre rend un voisin.
                    if (request.url.parameters["q"] == "monstera") {
                        respondJson(Fixtures.perenual("species_list_monstera.json"))
                    } else {
                        respondJson(Fixtures.perenual("species_list_empty.json"))
                    }
                }
                path.contains("species/details") ->
                    respondJson(Fixtures.perenual("species_details_monstera.json"))
                else -> respondJson(Fixtures.perenual("care_guide_monstera.json"))
            }
        }

        val inconnue = monstera.copy(scientificName = "Monstera inconnue")
        val result = service(engine).careSheetFor(inconnue)

        assertEquals(2, appels) // binôme puis genre, jamais plus
        assertEquals(MatchQuality.GENUS, result.sheet.matchQuality)
        assertTrue(result.sheet.isGenusApproximation)
    }

    @Test
    fun `un taxon au genre seul ne déclenche pas de seconde recherche`() = runTest {
        val engine = RecordingMockEngine { respondJson(Fixtures.perenual("species_list_empty.json")) }

        service(engine).careSheetFor(monstera.copy(scientificName = "Sedum sp."))

        assertEquals(1, engine.callCount)
    }

    @Test
    fun `quota épuisé sert le cache périmé en le signalant`() = runTest {
        val engine = fullEngine()
        val tracker = FakeQuotaTracker(perenualCalls = PerenualLimits.DAILY_REQUEST_SAFETY_STOP)
        val cache = FakeSpeciesCareCache().apply {
            seed(
                CachedSpeciesCare(
                    normalizedBinomial = "monstera deliciosa",
                    careSheet = sheet(DetailLevel.FULL, MatchQuality.EXACT),
                    matchQuality = MatchQuality.EXACT,
                    detailLevel = DetailLevel.FULL,
                    fetchedAt = now.minusSeconds((PerenualLimits.POSITIVE_CACHE_TTL_DAYS + 1) * 86_400),
                )
            )
        }

        val result = service(engine, cache, tracker).careSheetFor(monstera)

        assertTrue(result.fromCache)
        assertTrue(result.stale)
        assertInstanceOf(AppError.QuotaExceeded::class.java, result.warning)
        assertEquals(0, engine.callCount)
    }

    @Test
    fun `quota épuisé sans cache rend quand même une fiche de repli`() = runTest {
        val engine = fullEngine()
        val tracker = FakeQuotaTracker(perenualCalls = PerenualLimits.DAILY_REQUEST_SAFETY_STOP)

        val result = service(engine, quotaTracker = tracker).careSheetFor(monstera)

        assertTrue(result.sheet.isFallback)
        assertInstanceOf(AppError.QuotaExceeded::class.java, result.warning)
    }

    @Test
    fun `une panne réseau rend une fiche de repli et signale l'incident`() = runTest {
        val engine = RecordingMockEngine { throw java.io.IOException("réseau coupé") }

        val result = service(engine).careSheetFor(monstera)

        assertTrue(result.sheet.isFallback)
        assertInstanceOf(AppError.Network::class.java, result.warning)
    }

    @Test
    fun `un nom scientifique inexploitable rend directement une fiche de repli`() = runTest {
        val engine = fullEngine()

        val result = service(engine).careSheetFor(monstera.copy(scientificName = "   "))

        assertEquals(0, engine.callCount)
        assertTrue(result.sheet.isFallback)
    }

    @Test
    fun `chaque échelon de dégradation rend une fiche enregistrable`() = runTest {
        val echelons = listOf(
            "complète" to fullEngine(),
            "résumée" to fullEngine(detailsFixture = null),
            "repli" to RecordingMockEngine { respondJson(Fixtures.perenual("species_list_empty.json")) },
            "hors ligne" to RecordingMockEngine { throw java.io.IOException("coupé") },
        )

        echelons.forEach { (nom, engine) ->
            val result = service(engine).careSheetFor(monstera)

            // Le contrat : toujours un nom d'espèce et un intervalle utilisable pour les rappels.
            assertTrue(result.sheet.scientificName.isNotBlank(), nom)
            assertTrue(result.sheet.baseWateringIntervalDays >= 2, nom)
        }
    }

    private fun sheet(detailLevel: DetailLevel, quality: MatchQuality) = CareSheet(
        scientificName = "Monstera deliciosa",
        matchQuality = quality,
        detailLevel = detailLevel,
        baseWateringIntervalDays = 9,
    )
}
