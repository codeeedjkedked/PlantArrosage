package fr.plantarrosage.app.data.cache

import fr.plantarrosage.app.data.db.SpeciesCareDao
import fr.plantarrosage.app.data.db.SpeciesCareEntity
import fr.plantarrosage.core.model.CareSheet
import fr.plantarrosage.core.model.DetailLevel
import fr.plantarrosage.core.model.MatchQuality
import fr.plantarrosage.core.net.HttpClientFactory
import fr.plantarrosage.core.port.CachedSpeciesCare
import fr.plantarrosage.core.port.SpeciesCareCache
import java.time.Instant

/** Implémentation Room du cache déclaré par `:core`. */
class RoomSpeciesCareCache(
    private val dao: SpeciesCareDao,
) : SpeciesCareCache {

    override suspend fun get(normalizedBinomial: String): CachedSpeciesCare? {
        val entity = dao.find(normalizedBinomial) ?: return null

        return CachedSpeciesCare(
            normalizedBinomial = entity.normalizedBinomial,
            careSheet = entity.careJson?.let(::decodeSheet),
            matchQuality = entity.matchQuality.toMatchQuality(),
            detailLevel = entity.detailLevel.toDetailLevel(),
            fetchedAt = Instant.ofEpochMilli(entity.fetchedAt),
        )
    }

    override suspend fun put(entry: CachedSpeciesCare) {
        dao.upsert(
            SpeciesCareEntity(
                normalizedBinomial = entry.normalizedBinomial,
                perenualId = entry.careSheet?.perenualId,
                matchedScientificName = entry.careSheet?.scientificName,
                matchQuality = entry.matchQuality.name,
                detailLevel = entry.detailLevel.name,
                careJson = entry.careSheet?.let(::encodeSheet),
                imageUrl = entry.careSheet?.imageUrl,
                fetchedAt = entry.fetchedAt.toEpochMilli(),
            )
        )
    }

    override suspend fun clear() = dao.clear()

    suspend fun size(): Int = dao.count()

    private fun encodeSheet(sheet: CareSheet): String =
        HttpClientFactory.json.encodeToString(CareSheet.serializer(), sheet)

    /**
     * Une fiche illisible (schéma modifié entre deux versions de l'app) est traitée comme absente
     * plutôt que comme une panne : on refera simplement l'appel.
     */
    private fun decodeSheet(json: String): CareSheet? = runCatching {
        HttpClientFactory.json.decodeFromString(CareSheet.serializer(), json)
    }.getOrNull()

    private fun String.toMatchQuality(): MatchQuality =
        runCatching { MatchQuality.valueOf(this) }.getOrDefault(MatchQuality.NONE)

    private fun String.toDetailLevel(): DetailLevel =
        runCatching { DetailLevel.valueOf(this) }.getOrDefault(DetailLevel.NONE)
}
