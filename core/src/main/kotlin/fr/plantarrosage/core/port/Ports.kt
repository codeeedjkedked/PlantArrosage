package fr.plantarrosage.core.port

import fr.plantarrosage.core.model.CareSheet
import fr.plantarrosage.core.model.DetailLevel
import fr.plantarrosage.core.model.MatchQuality
import java.time.Instant

/**
 * Interfaces que `:core` déclare et que le module Android implémente (Room, DataStore).
 *
 * C'est ce qui permet à toute la logique métier de rester dans un module JVM pur, testable
 * sans émulateur ni instrumentation.
 */

/** Fournit les clés API, quelle que soit leur provenance. */
interface ApiKeyProvider {
    suspend fun plantNetKey(): String?
    suspend fun perenualKey(): String?
}

/** Entrée de cache pour une espèce, positive comme négative. */
data class CachedSpeciesCare(
    val normalizedBinomial: String,
    val careSheet: CareSheet?,
    val matchQuality: MatchQuality,
    val detailLevel: DetailLevel,
    val fetchedAt: Instant,
)

/** Cache persistant des fiches d'espèces. */
interface SpeciesCareCache {
    suspend fun get(normalizedBinomial: String): CachedSpeciesCare?
    suspend fun put(entry: CachedSpeciesCare)
    suspend fun clear()
}

/**
 * Suit la consommation des quotas.
 *
 * Perenual n'accorde que cent requêtes par jour sur l'offre gratuite : compter localement permet
 * d'annoncer « quota atteint » en français avant que l'API ne réponde 429.
 */
interface QuotaTracker {
    /** Requêtes Perenual déjà consommées aujourd'hui. */
    suspend fun perenualCallsToday(): Int

    /** Incrémente le compteur Perenual du jour. */
    suspend fun recordPerenualCall()

    /** Identifications Pl@ntNet restantes, telles que l'API les a annoncées. */
    suspend fun plantNetRemaining(): Int?

    /** Mémorise le reliquat communiqué par Pl@ntNet. */
    suspend fun recordPlantNetRemaining(remaining: Int?)
}
