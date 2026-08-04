package fr.plantarrosage.core.support

import fr.plantarrosage.core.net.HttpClientFactory
import fr.plantarrosage.core.port.ApiKeyProvider
import fr.plantarrosage.core.port.CachedSpeciesCare
import fr.plantarrosage.core.port.QuotaTracker
import fr.plantarrosage.core.port.SpeciesCareCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import java.util.concurrent.atomic.AtomicInteger

class FakeApiKeyProvider(
    private var plantNet: String? = "cle-plantnet",
    private var perenual: String? = "cle-perenual",
) : ApiKeyProvider {
    override suspend fun plantNetKey(): String? = plantNet
    override suspend fun perenualKey(): String? = perenual
}

class FakeQuotaTracker(
    private var perenualCalls: Int = 0,
) : QuotaTracker {
    var recordedPlantNetRemaining: Int? = null
        private set

    override suspend fun perenualCallsToday(): Int = perenualCalls
    override suspend fun recordPerenualCall() { perenualCalls++ }
    override suspend fun plantNetRemaining(): Int? = recordedPlantNetRemaining
    override suspend fun recordPlantNetRemaining(remaining: Int?) {
        recordedPlantNetRemaining = remaining
    }
}

/** Cache en mémoire, avec un compteur de lectures pour vérifier qu'on l'utilise vraiment. */
class FakeSpeciesCareCache : SpeciesCareCache {
    private val entries = mutableMapOf<String, CachedSpeciesCare>()

    val writes = AtomicInteger(0)

    override suspend fun get(normalizedBinomial: String): CachedSpeciesCare? = entries[normalizedBinomial]

    override suspend fun put(entry: CachedSpeciesCare) {
        writes.incrementAndGet()
        entries[entry.normalizedBinomial] = entry
    }

    override suspend fun clear() = entries.clear()

    fun seed(entry: CachedSpeciesCare) { entries[entry.normalizedBinomial] = entry }
}

/** Enregistre chaque requête sortante pour que les tests puissent les inspecter. */
class RecordingMockEngine(
    private val handler: MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
) {
    val requests = mutableListOf<HttpRequestData>()

    val callCount: Int get() = requests.size

    fun client(): HttpClient = HttpClientFactory.create(
        MockEngine { request ->
            requests += request
            handler(request)
        }
    )
}

fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

fun MockRequestHandleScope.respondStatus(status: HttpStatusCode) =
    respondError(status, "erreur simulée")
