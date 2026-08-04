package fr.plantarrosage.core.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Configuration commune des clients HTTP.
 *
 * Le moteur est un paramètre : le module Android injecte OkHttp, les tests injectent `MockEngine`.
 * C'est ce qui permet aux vraies classes clientes de vivre dans `:core` et d'être testées sans
 * ouvrir de socket.
 */
object HttpClientFactory {

    /**
     * Tolérance maximale au décodage : les deux APIs ajoutent des champs, en omettent d'autres,
     * et renvoient parfois des types incohérents. Un champ inattendu ne doit jamais faire échouer
     * une fiche entière.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }

    fun create(
        engine: HttpClientEngine,
        requestTimeoutMillis: Long = 30_000,
        connectTimeoutMillis: Long = 15_000,
        configure: HttpClientConfig<*>.() -> Unit = {},
    ): HttpClient = HttpClient(engine) {
        expectSuccess = false

        install(ContentNegotiation) {
            json(HttpClientFactory.json)
        }

        install(HttpTimeout) {
            this.requestTimeoutMillis = requestTimeoutMillis
            this.connectTimeoutMillis = connectTimeoutMillis
            socketTimeoutMillis = requestTimeoutMillis
        }

        configure()
    }
}
