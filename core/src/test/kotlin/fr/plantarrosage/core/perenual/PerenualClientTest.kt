package fr.plantarrosage.core.perenual

import fr.plantarrosage.core.model.AppError
import fr.plantarrosage.core.support.FakeApiKeyProvider
import fr.plantarrosage.core.support.FakeQuotaTracker
import fr.plantarrosage.core.support.Fixtures
import fr.plantarrosage.core.support.RecordingMockEngine
import fr.plantarrosage.core.support.respondJson
import fr.plantarrosage.core.support.respondStatus
import fr.plantarrosage.core.util.Outcome
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PerenualClientTest {

    private fun client(
        engine: RecordingMockEngine,
        quotaTracker: FakeQuotaTracker = FakeQuotaTracker(),
        apiKey: String? = "cle-perenual",
    ) = PerenualClient(
        httpClient = engine.client(),
        apiKeyProvider = FakeApiKeyProvider(perenual = apiKey),
        quotaTracker = quotaTracker,
        baseUrl = "https://perenual.com/api",
    )

    private fun listEngine() = RecordingMockEngine {
        respondJson(Fixtures.perenual("species_list_monstera.json"))
    }

    @Test
    fun `recherche une espèce par nom`() = runTest {
        val engine = listEngine()

        val result = client(engine).searchSpecies("monstera deliciosa")

        val dto = (result as Outcome.Success).value
        assertEquals(2, dto.data.size)
        assertEquals(1786, dto.data.first().id)
    }

    @Test
    fun `transmet la clé et la requête`() = runTest {
        val engine = listEngine()

        client(engine).searchSpecies("monstera deliciosa", page = 2)

        val request = engine.requests.single()
        assertTrue(request.url.encodedPath.endsWith("/api/species-list"), request.url.toString())
        assertEquals("cle-perenual", request.url.parameters["key"])
        assertEquals("monstera deliciosa", request.url.parameters["q"])
        assertEquals("2", request.url.parameters["page"])
    }

    @Test
    fun `interroge le bon chemin pour les détails`() = runTest {
        val engine = RecordingMockEngine {
            respondJson(Fixtures.perenual("species_details_monstera.json"))
        }

        client(engine).speciesDetails(1786)

        assertTrue(
            engine.requests.single().url.encodedPath.endsWith("/api/species/details/1786"),
            engine.requests.single().url.toString(),
        )
    }

    @Test
    fun `interroge le bon chemin pour le guide d'entretien`() = runTest {
        val engine = RecordingMockEngine { respondJson(Fixtures.perenual("care_guide_monstera.json")) }

        client(engine).careGuide(1786)

        val request = engine.requests.single()
        assertTrue(request.url.encodedPath.endsWith("/api/species-care-guide-list"))
        assertEquals("1786", request.url.parameters["species_id"])
    }

    @Test
    fun `le préfixe de base est configurable`() = runTest {
        val engine = listEngine()
        val v2 = PerenualClient(
            httpClient = engine.client(),
            apiKeyProvider = FakeApiKeyProvider(),
            quotaTracker = FakeQuotaTracker(),
            baseUrl = "https://perenual.com/api/v2",
        )

        v2.searchSpecies("rosa")

        assertTrue(engine.requests.single().url.encodedPath.contains("/api/v2/"))
    }

    // ---------- Garde-fous de quota ----------

    @Test
    fun `une espèce hors du palier gratuit ne consomme aucune requête`() = runTest {
        val engine = listEngine()

        val result = client(engine).speciesDetails(PerenualLimits.FREE_TIER_MAX_SPECIES_ID + 1)

        assertEquals(0, engine.callCount)
        assertInstanceOf(AppError.QuotaExceeded::class.java, (result as Outcome.Failure).error)
    }

    @Test
    fun `le guide d'entretien respecte la même limite`() = runTest {
        val engine = listEngine()

        client(engine).careGuide(5000)

        assertEquals(0, engine.callCount)
    }

    @Test
    fun `le compteur local coupe avant le quota réel`() = runTest {
        val engine = listEngine()
        val tracker = FakeQuotaTracker(perenualCalls = PerenualLimits.DAILY_REQUEST_SAFETY_STOP)

        val result = client(engine, quotaTracker = tracker).searchSpecies("rosa")

        assertEquals(0, engine.callCount)
        assertInstanceOf(AppError.QuotaExceeded::class.java, (result as Outcome.Failure).error)
    }

    @Test
    fun `chaque appel émis incrémente le compteur`() = runTest {
        val engine = listEngine()
        val tracker = FakeQuotaTracker()
        val perenual = client(engine, quotaTracker = tracker)

        perenual.searchSpecies("rosa")
        perenual.searchSpecies("aloe")

        assertEquals(2, tracker.perenualCallsToday())
    }

    // ---------- Erreurs ----------

    @Test
    fun `sans clé API aucune requête n'est émise`() = runTest {
        val engine = listEngine()

        val result = client(engine, apiKey = null).searchSpecies("rosa")

        assertEquals(0, engine.callCount)
        assertInstanceOf(AppError.MissingApiKey::class.java, (result as Outcome.Failure).error)
    }

    @Test
    fun `un 401 signale une clé invalide`() = runTest {
        val engine = RecordingMockEngine { respondStatus(HttpStatusCode.Unauthorized) }

        assertInstanceOf(
            AppError.InvalidApiKey::class.java,
            (client(engine).searchSpecies("rosa") as Outcome.Failure).error,
        )
    }

    @Test
    fun `un 429 signale un quota atteint`() = runTest {
        val engine = RecordingMockEngine { respondStatus(HttpStatusCode.TooManyRequests) }

        assertInstanceOf(
            AppError.QuotaExceeded::class.java,
            (client(engine).searchSpecies("rosa") as Outcome.Failure).error,
        )
    }

    @Test
    fun `un 500 signale un service indisponible`() = runTest {
        val engine = RecordingMockEngine { respondStatus(HttpStatusCode.InternalServerError) }

        assertInstanceOf(
            AppError.ServiceUnavailable::class.java,
            (client(engine).searchSpecies("rosa") as Outcome.Failure).error,
        )
    }

    @Test
    fun `une panne réseau est rapportée comme telle`() = runTest {
        val engine = RecordingMockEngine { throw java.net.SocketTimeoutException("délai dépassé") }

        assertInstanceOf(
            AppError.Network::class.java,
            (client(engine).searchSpecies("rosa") as Outcome.Failure).error,
        )
    }

    @Test
    fun `un corps illisible est signalé comme erreur de décodage`() = runTest {
        val engine = RecordingMockEngine { respondJson("<html>maintenance</html>") }

        assertInstanceOf(
            AppError.Parsing::class.java,
            (client(engine).searchSpecies("rosa") as Outcome.Failure).error,
        )
    }
}
