package fr.plantarrosage.core.perenual

import fr.plantarrosage.core.model.AppError
import fr.plantarrosage.core.net.HttpClientFactory
import fr.plantarrosage.core.port.ApiKeyProvider
import fr.plantarrosage.core.port.QuotaTracker
import fr.plantarrosage.core.util.Outcome
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.DeserializationStrategy

/**
 * Client de la base d'entretien Perenual.
 *
 * Chaque appel est précédé d'une vérification de quota locale : l'offre gratuite plafonne à cent
 * requêtes par jour, et il vaut mieux prévenir l'utilisateur en français que lui laisser
 * découvrir un 429.
 */
class PerenualClient(
    private val httpClient: HttpClient,
    private val apiKeyProvider: ApiKeyProvider,
    private val quotaTracker: QuotaTracker,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    companion object {
        /** Perenual expose aussi `/api/v2/` selon le palier de clé : le préfixe est paramétrable. */
        const val DEFAULT_BASE_URL = "https://perenual.com/api"
        private const val SERVICE = "Perenual"
    }

    suspend fun searchSpecies(query: String, page: Int = 1): Outcome<PerenualSpeciesListDto> =
        request(PerenualSpeciesListDto.serializer()) {
            httpClient.get("$baseUrl/species-list") {
                parameter("key", it)
                parameter("q", query)
                parameter("page", page)
            }
        }

    suspend fun speciesDetails(speciesId: Int): Outcome<PerenualSpeciesDetailsDto> {
        if (!PerenualLimits.supportsDetails(speciesId)) {
            // Inutile de dépenser une requête pour recevoir un refus.
            return Outcome.Failure(AppError.QuotaExceeded(SERVICE))
        }
        return request(PerenualSpeciesDetailsDto.serializer()) {
            httpClient.get("$baseUrl/species/details/$speciesId") {
                parameter("key", it)
            }
        }
    }

    suspend fun careGuide(speciesId: Int): Outcome<PerenualCareGuideListDto> {
        if (!PerenualLimits.supportsDetails(speciesId)) {
            return Outcome.Failure(AppError.QuotaExceeded(SERVICE))
        }
        return request(PerenualCareGuideListDto.serializer()) {
            httpClient.get("$baseUrl/species-care-guide-list") {
                parameter("key", it)
                parameter("species_id", speciesId)
            }
        }
    }

    private suspend fun <T> request(
        deserializer: DeserializationStrategy<T>,
        call: suspend (apiKey: String) -> HttpResponse,
    ): Outcome<T> {
        val key = apiKeyProvider.perenualKey()?.takeIf { it.isNotBlank() }
            ?: return Outcome.Failure(AppError.MissingApiKey(SERVICE))

        if (quotaTracker.perenualCallsToday() >= PerenualLimits.DAILY_REQUEST_SAFETY_STOP) {
            return Outcome.Failure(AppError.QuotaExceeded(SERVICE))
        }

        val response = try {
            call(key)
        } catch (e: Exception) {
            return Outcome.Failure(AppError.Network(e.message ?: e::class.simpleName.orEmpty()))
        }

        quotaTracker.recordPerenualCall()

        return when (response.status) {
            HttpStatusCode.OK -> decode(deserializer, response)
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
                Outcome.Failure(AppError.InvalidApiKey(SERVICE))
            HttpStatusCode.TooManyRequests -> Outcome.Failure(AppError.QuotaExceeded(SERVICE))
            HttpStatusCode.NotFound -> Outcome.Failure(AppError.NoMatch)
            HttpStatusCode.BadRequest ->
                Outcome.Failure(AppError.BadRequest(runCatching { response.bodyAsText().take(200) }
                    .getOrDefault("requête refusée")))
            else -> Outcome.Failure(AppError.ServiceUnavailable(SERVICE))
        }
    }

    private suspend fun <T> decode(
        deserializer: DeserializationStrategy<T>,
        response: HttpResponse,
    ): Outcome<T> = try {
        Outcome.Success(HttpClientFactory.json.decodeFromString(deserializer, response.bodyAsText()))
    } catch (e: Exception) {
        Outcome.Failure(AppError.Parsing(e.message ?: "décodage impossible"))
    }
}
