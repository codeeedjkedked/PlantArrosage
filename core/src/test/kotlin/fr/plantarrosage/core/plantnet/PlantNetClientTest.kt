package fr.plantarrosage.core.plantnet

import fr.plantarrosage.core.model.AppError
import fr.plantarrosage.core.model.PlantNetProject
import fr.plantarrosage.core.model.PlantOrgan
import fr.plantarrosage.core.support.FakeApiKeyProvider
import fr.plantarrosage.core.support.FakeQuotaTracker
import fr.plantarrosage.core.support.Fixtures
import fr.plantarrosage.core.support.RecordingMockEngine
import fr.plantarrosage.core.support.respondJson
import fr.plantarrosage.core.support.respondStatus
import fr.plantarrosage.core.util.Outcome
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlantNetClientTest {

    private val photo = PlantPhoto(
        bytes = byteArrayOf(1, 2, 3, 4),
        fileName = "feuille.jpg",
        organ = PlantOrgan.LEAF,
    )

    private fun client(
        engine: RecordingMockEngine,
        quotaTracker: FakeQuotaTracker = FakeQuotaTracker(),
        apiKey: String? = "cle-plantnet",
    ) = PlantNetClient(
        httpClient = engine.client(),
        apiKeyProvider = FakeApiKeyProvider(plantNet = apiKey),
        quotaTracker = quotaTracker,
        baseUrl = "https://my-api.plantnet.org/v2",
    )

    private fun okEngine() = RecordingMockEngine {
        respondJson(Fixtures.plantNet("identify_ok.json"))
    }

    @Test
    fun `identifie une plante et rend les candidats`() = runTest {
        val engine = okEngine()

        val result = client(engine).identify(listOf(photo))

        assertInstanceOf(Outcome.Success::class.java, result)
        val value = (result as Outcome.Success).value
        assertEquals("Monstera deliciosa", value.candidates.first().scientificName)
        assertEquals(462, value.remainingRequests)
    }

    @Test
    fun `poste sur le projet demandé`() = runTest {
        val engine = okEngine()

        client(engine).identify(listOf(photo), project = PlantNetProject.WEUROPE)

        val request = engine.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.encodedPath.endsWith("/v2/identify/weurope"), request.url.toString())
    }

    @Test
    fun `transmet la clé et les paramètres attendus`() = runTest {
        val engine = okEngine()

        client(engine).identify(listOf(photo), nbResults = 3)

        val params = engine.requests.single().url.parameters
        assertEquals("cle-plantnet", params["api-key"])
        assertEquals("fr", params["lang"])
        assertEquals("3", params["nb-results"])
        assertEquals("true", params["include-related-images"])
        assertEquals("false", params["no-reject"])
    }

    @Test
    fun `construit un corps multipart`() = runTest {
        val engine = okEngine()

        client(engine).identify(listOf(photo))

        assertInstanceOf(MultiPartFormDataContent::class.java, engine.requests.single().body)
    }

    @Test
    fun `envoie autant d'organes que d'images, dans le même ordre`() = runTest {
        val engine = okEngine()
        val photos = listOf(
            photo,
            PlantPhoto(byteArrayOf(9), "fleur.jpg", PlantOrgan.FLOWER),
            PlantPhoto(byteArrayOf(8), "ecorce.jpg", PlantOrgan.BARK),
        )

        client(engine).identify(photos)

        val body = engine.requests.single().body as MultiPartFormDataContent
        val rendered = renderMultipart(body)

        assertEquals(3, Regex("name=\"?images\"?").findAll(rendered).count(), rendered)
        assertEquals(3, Regex("name=\"?organs\"?").findAll(rendered).count(), rendered)

        // L'ordre des organes doit refléter celui des photos : Pl@ntNet les apparie par index.
        val organs = Regex("(leaf|flower|bark|fruit|auto)").findAll(rendered).map { it.value }.toList()
        assertEquals(listOf("leaf", "flower", "bark"), organs)
    }

    @Test
    fun `n'envoie jamais plus de cinq images`() = runTest {
        val engine = okEngine()
        val photos = (1..8).map { PlantPhoto(byteArrayOf(it.toByte()), "p$it.jpg", PlantOrgan.AUTO) }

        client(engine).identify(photos)

        val rendered = renderMultipart(engine.requests.single().body as MultiPartFormDataContent)
        assertEquals(PlantNetClient.MAX_IMAGES, Regex("name=\"?images\"?").findAll(rendered).count())
    }

    @Test
    fun `mémorise le quota restant annoncé`() = runTest {
        val tracker = FakeQuotaTracker()

        client(okEngine(), quotaTracker = tracker).identify(listOf(photo))

        assertEquals(462, tracker.plantNetRemaining())
    }

    // ---------- Erreurs ----------

    @Test
    fun `sans clé API aucune requête n'est émise`() = runTest {
        val engine = okEngine()

        val result = client(engine, apiKey = null).identify(listOf(photo))

        assertEquals(0, engine.callCount)
        assertInstanceOf(AppError.MissingApiKey::class.java, (result as Outcome.Failure).error)
    }

    @Test
    fun `sans photo aucune requête n'est émise`() = runTest {
        val engine = okEngine()

        val result = client(engine).identify(emptyList())

        assertEquals(0, engine.callCount)
        assertInstanceOf(AppError.BadRequest::class.java, (result as Outcome.Failure).error)
    }

    @Test
    fun `une réponse vide est signalée comme absence de correspondance`() = runTest {
        val engine = RecordingMockEngine { respondJson(Fixtures.plantNet("identify_no_match.json")) }

        val result = client(engine).identify(listOf(photo))

        assertEquals(AppError.NoMatch, (result as Outcome.Failure).error)
    }

    @Test
    fun `un 401 signale une clé invalide`() = runTest {
        val engine = RecordingMockEngine { respondStatus(HttpStatusCode.Unauthorized) }

        val result = client(engine).identify(listOf(photo))

        assertInstanceOf(AppError.InvalidApiKey::class.java, (result as Outcome.Failure).error)
    }

    @Test
    fun `un 404 signale une absence de correspondance`() = runTest {
        val engine = RecordingMockEngine { respondStatus(HttpStatusCode.NotFound) }

        assertEquals(AppError.NoMatch, (client(engine).identify(listOf(photo)) as Outcome.Failure).error)
    }

    @Test
    fun `un 413 signale une image trop lourde`() = runTest {
        val engine = RecordingMockEngine { respondStatus(HttpStatusCode.PayloadTooLarge) }

        assertEquals(
            AppError.ImageTooLarge,
            (client(engine).identify(listOf(photo)) as Outcome.Failure).error,
        )
    }

    @Test
    fun `un 429 signale un quota atteint`() = runTest {
        val engine = RecordingMockEngine { respondStatus(HttpStatusCode.TooManyRequests) }

        assertInstanceOf(
            AppError.QuotaExceeded::class.java,
            (client(engine).identify(listOf(photo)) as Outcome.Failure).error,
        )
    }

    @Test
    fun `un 400 remonte le détail de la requête refusée`() = runTest {
        val engine = RecordingMockEngine { respondStatus(HttpStatusCode.BadRequest) }

        assertInstanceOf(
            AppError.BadRequest::class.java,
            (client(engine).identify(listOf(photo)) as Outcome.Failure).error,
        )
    }

    @Test
    fun `un 500 signale un service indisponible`() = runTest {
        val engine = RecordingMockEngine { respondStatus(HttpStatusCode.InternalServerError) }

        assertInstanceOf(
            AppError.ServiceUnavailable::class.java,
            (client(engine).identify(listOf(photo)) as Outcome.Failure).error,
        )
    }

    @Test
    fun `une panne réseau est rapportée comme telle`() = runTest {
        val engine = RecordingMockEngine { throw java.io.IOException("connexion refusée") }

        assertInstanceOf(
            AppError.Network::class.java,
            (client(engine).identify(listOf(photo)) as Outcome.Failure).error,
        )
    }

    @Test
    fun `un corps illisible est signalé comme erreur de décodage`() = runTest {
        val engine = RecordingMockEngine { respondJson("{ ceci n'est pas du json") }

        assertInstanceOf(
            AppError.Parsing::class.java,
            (client(engine).identify(listOf(photo)) as Outcome.Failure).error,
        )
    }

    /**
     * Sérialise le corps multipart pour pouvoir l'inspecter.
     * L'encodage ISO-8859-1 conserve les octets bruts sans les altérer.
     */
    private suspend fun renderMultipart(content: MultiPartFormDataContent): String = coroutineScope {
        val channel = ByteChannel(autoFlush = true)
        launch {
            content.writeTo(channel)
            channel.flushAndClose()
        }
        String(channel.readRemaining().readByteArray(), Charsets.ISO_8859_1)
    }
}
