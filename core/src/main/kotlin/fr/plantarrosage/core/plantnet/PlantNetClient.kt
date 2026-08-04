package fr.plantarrosage.core.plantnet

import fr.plantarrosage.core.model.AppError
import fr.plantarrosage.core.model.IdentificationResult
import fr.plantarrosage.core.model.PlantNetProject
import fr.plantarrosage.core.model.PlantOrgan
import fr.plantarrosage.core.net.HttpClientFactory
import fr.plantarrosage.core.port.ApiKeyProvider
import fr.plantarrosage.core.port.QuotaTracker
import fr.plantarrosage.core.util.Outcome
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.client.request.post

/** Une photo à soumettre, avec l'organe qu'elle représente. */
data class PlantPhoto(
    val bytes: ByteArray,
    val fileName: String,
    val organ: PlantOrgan,
) {
    // ByteArray n'a pas d'égalité structurelle : on la fournit pour que les tests restent lisibles.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlantPhoto) return false
        return fileName == other.fileName && organ == other.organ && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int =
        31 * (31 * bytes.contentHashCode() + fileName.hashCode()) + organ.hashCode()
}

/**
 * Client d'identification Pl@ntNet.
 *
 * Une identification consomme une requête de quota quel que soit le nombre de photos envoyées —
 * jusqu'à cinq. Mieux vaut donc en envoyer plusieurs que de multiplier les identifications.
 */
class PlantNetClient(
    private val httpClient: HttpClient,
    private val apiKeyProvider: ApiKeyProvider,
    private val quotaTracker: QuotaTracker? = null,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    companion object {
        const val DEFAULT_BASE_URL = "https://my-api.plantnet.org/v2"
        const val MAX_IMAGES = 5
        private const val SERVICE = "Pl@ntNet"
    }

    suspend fun identify(
        photos: List<PlantPhoto>,
        project: PlantNetProject = PlantNetProject.ALL,
        nbResults: Int = 5,
    ): Outcome<IdentificationResult> {
        if (photos.isEmpty()) {
            return Outcome.Failure(AppError.BadRequest("aucune photo à identifier"))
        }

        val key = apiKeyProvider.plantNetKey()?.takeIf { it.isNotBlank() }
            ?: return Outcome.Failure(AppError.MissingApiKey(SERVICE))

        val retained = photos.take(MAX_IMAGES)

        val response: HttpResponse = try {
            httpClient.post("$baseUrl/identify/${project.apiValue}") {
                parameter("api-key", key)
                parameter("lang", "fr")
                parameter("nb-results", nbResults)
                parameter("include-related-images", true)
                parameter("no-reject", false)
                setBody(buildMultipart(retained))
            }
        } catch (e: Exception) {
            return Outcome.Failure(AppError.Network(e.message ?: e::class.simpleName.orEmpty()))
        }

        return when (response.status) {
            HttpStatusCode.OK -> decode(response)
            HttpStatusCode.BadRequest -> Outcome.Failure(
                AppError.BadRequest(response.shortBody())
            )
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
                Outcome.Failure(AppError.InvalidApiKey(SERVICE))
            HttpStatusCode.NotFound -> Outcome.Failure(AppError.NoMatch)
            HttpStatusCode.PayloadTooLarge -> Outcome.Failure(AppError.ImageTooLarge)
            HttpStatusCode.TooManyRequests -> Outcome.Failure(AppError.QuotaExceeded(SERVICE))
            else -> Outcome.Failure(AppError.ServiceUnavailable(SERVICE))
        }
    }

    private fun buildMultipart(photos: List<PlantPhoto>) = MultiPartFormDataContent(
        formData {
            // Les parties `images` et `organs` doivent rester alignées par index : Pl@ntNet
            // associe la n-ième photo au n-ième organe.
            photos.forEach { photo ->
                append(
                    key = "images",
                    value = photo.bytes,
                    headers = Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
                        append(HttpHeaders.ContentDisposition, "filename=\"${photo.fileName}\"")
                    },
                )
            }
            photos.forEach { photo ->
                append("organs", photo.organ.apiValue)
            }
        }
    )

    private suspend fun decode(response: HttpResponse): Outcome<IdentificationResult> {
        val raw = response.bodyAsText()

        val dto = try {
            HttpClientFactory.json.decodeFromString(PlantNetResponseDto.serializer(), raw)
        } catch (e: Exception) {
            return Outcome.Failure(AppError.Parsing(e.message ?: "décodage impossible"))
        }

        val result = PlantNetMapper.toDomain(dto)
        quotaTracker?.recordPlantNetRemaining(result.remainingRequests)

        return if (result.candidates.isEmpty()) {
            Outcome.Failure(AppError.NoMatch)
        } else {
            Outcome.Success(result)
        }
    }

    private suspend fun HttpResponse.shortBody(): String =
        runCatching { bodyAsText().take(200) }.getOrDefault("réponse illisible")
}
