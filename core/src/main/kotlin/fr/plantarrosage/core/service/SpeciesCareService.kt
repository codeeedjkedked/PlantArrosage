package fr.plantarrosage.core.service

import fr.plantarrosage.core.matching.ScientificNameNormalizer
import fr.plantarrosage.core.matching.SpeciesMatcher
import fr.plantarrosage.core.model.AppError
import fr.plantarrosage.core.model.CareSheet
import fr.plantarrosage.core.model.DetailLevel
import fr.plantarrosage.core.model.IdentificationCandidate
import fr.plantarrosage.core.model.MatchQuality
import fr.plantarrosage.core.model.NormalizedName
import fr.plantarrosage.core.model.SpeciesSubject
import fr.plantarrosage.core.model.toSubject
import fr.plantarrosage.core.matching.SpeciesListEntry
import fr.plantarrosage.core.perenual.PerenualClient
import fr.plantarrosage.core.perenual.PerenualLimits
import fr.plantarrosage.core.perenual.PerenualMapper
import fr.plantarrosage.core.port.CachedSpeciesCare
import fr.plantarrosage.core.port.SpeciesCareCache
import fr.plantarrosage.core.care.WateringIntervalCalculator
import fr.plantarrosage.core.util.Outcome
import fr.plantarrosage.core.water.WateringReference
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fiche d'entretien résolue, avec ce qu'il faut pour que l'écran soit honnête sur sa provenance.
 */
data class CareSheetResult(
    val sheet: CareSheet,
    /** Vrai si la fiche vient du cache et non du réseau. */
    val fromCache: Boolean = false,
    /** Vrai si le cache a été servi au-delà de sa durée de validité, faute de mieux. */
    val stale: Boolean = false,
    /** Incident non bloquant à signaler à l'utilisateur (quota, réseau…). */
    val warning: AppError? = null,
)

/**
 * Orchestre la récupération d'une fiche d'entretien : appariement, cache, appels Perenual,
 * puis dégradation contrôlée.
 *
 * Trois contraintes gouvernent ce code, toutes issues du palier gratuit Perenual (cent requêtes
 * par jour, détails réservés aux espèces 1–3000) :
 *
 *  - **le cache est obligatoire**, y compris pour les échecs — sans cache négatif, chaque
 *    ré-identification d'une espèce inconnue redépenserait deux requêtes ;
 *  - **au plus deux requêtes** par espèce : la recherche binomiale, puis un unique repli sur le
 *    genre ;
 *  - **jamais d'impasse** : même sans aucune donnée, une fiche de repli est renvoyée pour que la
 *    plante reste enregistrable et les rappels fonctionnels.
 */
class SpeciesCareService(
    private val perenualClient: PerenualClient,
    private val cache: SpeciesCareCache,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    /** Une clé en cours de résolution bloque les appels concurrents sur la même espèce. */
    private val keyLocks = mutableMapOf<String, Mutex>()
    private val keyLocksGuard = Mutex()

    /**
     * Recherche d'espèces par nom, pour l'écran de recherche et l'ajout manuel.
     * Une requête Perenual, comptabilisée comme les autres.
     */
    suspend fun searchSpecies(query: String): Outcome<List<SpeciesListEntry>> {
        val terme = query.trim()
        if (terme.length < 2) return Outcome.Success(emptyList())

        return when (val response = perenualClient.searchSpecies(terme)) {
            is Outcome.Success -> Outcome.Success(PerenualMapper.toEntries(response.value))
            is Outcome.Failure -> response
        }
    }

    /**
     * Variante pour une identification Pl@ntNet : reporte sur la fiche les renvois vers GBIF et
     * POWO, que seul Pl@ntNet fournit.
     */
    suspend fun careSheetFor(candidate: IdentificationCandidate): CareSheetResult {
        val result = careSheetFor(candidate.toSubject())
        return result.copy(
            sheet = result.sheet.copy(gbifId = candidate.gbifId, powoId = candidate.powoId)
        )
    }

    suspend fun careSheetFor(subject: SpeciesSubject): CareSheetResult {
        val normalized = ScientificNameNormalizer.normalize(subject.scientificName)
            ?: return CareSheetResult(withLocalWatering(CareSheet.fallbackFrom(subject), subject))

        val lock = keyLocksGuard.withLock { keyLocks.getOrPut(normalized.binomial) { Mutex() } }

        return lock.withLock {
            // Relecture du cache après acquisition : un appel concurrent vient peut-être de le
            // remplir, auquel cas il n'y a plus rien à demander au réseau.
            readFreshCache(subject, normalized)?.let { return@withLock it }
            resolve(subject, normalized)
        }
    }

    // ------------------------------------------------------------------ cache

    private suspend fun readFreshCache(
        subject: SpeciesSubject,
        normalized: NormalizedName,
    ): CareSheetResult? {
        val cached = cache.get(normalized.binomial) ?: return null
        if (!isFresh(cached)) return null

        return CareSheetResult(
            sheet = cached.careSheet ?: withLocalWatering(CareSheet.fallbackFrom(subject), subject),
            fromCache = true,
        )
    }

    /**
     * Complète une fiche avec la base d'arrosage locale.
     *
     * Appliqué même aux fiches de repli : une espèce inconnue de Perenual peut parfaitement être
     * documentée chez nous, et c'est tout l'intérêt d'une source qui ne dépend pas du réseau.
     */
    private fun withLocalWatering(sheet: CareSheet, subject: SpeciesSubject): CareSheet {
        val normalized = ScientificNameNormalizer.normalize(sheet.scientificName)
        val curated = WateringReference.lookup(
            name = normalized,
            family = sheet.family ?: subject.family,
            typeHint = if (sheet.droughtTolerant == true) "succulente" else null,
        ) ?: return sheet

        val base = WateringIntervalCalculator.resolveBase(
            curated = curated,
            benchmarkValue = null,
            benchmarkUnit = null,
            wateringEnum = sheet.wateringRaw,
        )

        return sheet.copy(
            baseWateringIntervalDays = base.days,
            baseIntervalSourceFr = base.sourceFr,
            hasWateringData = !base.isDefault,
            wateringAdviceFr = base.adviceFr ?: sheet.wateringAdviceFr,
            wateringPitfallFr = base.pitfallFr ?: sheet.wateringPitfallFr,
            droughtTolerant = sheet.droughtTolerant ?: base.droughtTolerant,
        )
    }

    private fun isFresh(cached: CachedSpeciesCare): Boolean {
        val ttlDays = if (cached.matchQuality == MatchQuality.NONE) {
            PerenualLimits.NEGATIVE_CACHE_TTL_DAYS
        } else {
            PerenualLimits.POSITIVE_CACHE_TTL_DAYS
        }
        val age = Duration.between(cached.fetchedAt, clock.instant())
        return age < Duration.ofDays(ttlDays)
    }

    private suspend fun store(normalized: NormalizedName, sheet: CareSheet?, quality: MatchQuality) {
        cache.put(
            CachedSpeciesCare(
                normalizedBinomial = normalized.binomial,
                careSheet = sheet,
                matchQuality = quality,
                detailLevel = sheet?.detailLevel ?: DetailLevel.NONE,
                fetchedAt = clock.instant(),
            )
        )
    }

    // ---------------------------------------------------------------- réseau

    private suspend fun resolve(
        subject: SpeciesSubject,
        normalized: NormalizedName,
    ): CareSheetResult {
        // 0. L'espèce vient d'une recherche : son identifiant Perenual est déjà connu, inutile de
        //    redépenser une requête de recherche pour le retrouver.
        subject.knownPerenualId?.let { perenualId ->
            val entry = SpeciesListEntry(
                id = perenualId,
                commonName = subject.bestCommonName,
                scientificNames = listOf(subject.scientificName),
                imageUrl = subject.imageUrl,
            )
            return buildFromEntry(subject, normalized, entry, MatchQuality.EXACT)
        }

        // 1. Recherche sur le binôme.
        val binomialSearch = perenualClient.searchSpecies(normalized.binomial)
        if (binomialSearch is Outcome.Failure) {
            return degradeOnFailure(subject, normalized, binomialSearch.error)
        }

        val entries = PerenualMapper.toEntries((binomialSearch as Outcome.Success).value)
        var match = SpeciesMatcher.match(normalized, entries, subject.commonNames)

        // 2. Repli unique sur le genre — jamais de troisième requête, le budget est trop étroit.
        if (match == null && !normalized.isGenusOnly) {
            when (val genusSearch = perenualClient.searchSpecies(normalized.genus)) {
                is Outcome.Success -> {
                    val genusEntries = PerenualMapper.toEntries(genusSearch.value)
                    match = SpeciesMatcher.match(normalized, genusEntries, subject.commonNames)
                }
                is Outcome.Failure -> {
                    return degradeOnFailure(subject, normalized, genusSearch.error)
                }
            }
        }

        // 3. Aucune correspondance : on mémorise l'absence et on rend une fiche de repli.
        if (match == null) {
            store(normalized, null, MatchQuality.NONE)
            return CareSheetResult(sheet = withLocalWatering(CareSheet.fallbackFrom(subject), subject))
        }

        return buildFromEntry(subject, normalized, match.entry, match.quality)
    }

    /**
     * Construit la fiche depuis une entrée Perenual retenue : détails complets si l'espèce est
     * dans le périmètre de l'offre gratuite, sinon fiche résumée — qui est un état de plein
     * droit, pas un échec.
     */
    private suspend fun buildFromEntry(
        subject: SpeciesSubject,
        normalized: NormalizedName,
        entry: SpeciesListEntry,
        quality: MatchQuality,
    ): CareSheetResult {
        val curated = WateringReference.lookup(
            name = normalized,
            family = subject.family,
            typeHint = null,
        )

        if (PerenualLimits.supportsDetails(entry.id)) {
            val details = perenualClient.speciesDetails(entry.id)
            if (details is Outcome.Success) {
                val guide = (perenualClient.careGuide(entry.id) as? Outcome.Success)?.value
                val sheet = PerenualMapper.toFullSheet(
                    subject = subject,
                    entry = entry,
                    details = details.value,
                    guide = guide,
                    matchQuality = quality,
                    curated = curated,
                )
                store(normalized, sheet, quality)
                return CareSheetResult(sheet = sheet)
            }
        }

        val summary = PerenualMapper.toSummarySheet(subject, entry, quality, curated)
        store(normalized, summary, quality)
        return CareSheetResult(sheet = summary)
    }

    /**
     * Quota épuisé ou réseau coupé : on sert le cache même périmé plutôt que rien, et on le dit.
     */
    private suspend fun degradeOnFailure(
        subject: SpeciesSubject,
        normalized: NormalizedName,
        error: AppError,
    ): CareSheetResult {
        val cached = cache.get(normalized.binomial)
        if (cached?.careSheet != null) {
            return CareSheetResult(
                sheet = cached.careSheet,
                fromCache = true,
                stale = true,
                warning = error,
            )
        }

        return CareSheetResult(
            sheet = withLocalWatering(CareSheet.fallbackFrom(subject), subject),
            warning = error,
        )
    }
}
