package fr.plantarrosage.core.perenual

import fr.plantarrosage.core.net.HttpClientFactory
import fr.plantarrosage.core.support.Fixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PerenualDtoParsingTest {

    private fun <T> decode(
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
        fixture: String,
    ): T = HttpClientFactory.json.decodeFromString(deserializer, Fixtures.perenual(fixture))

    // ---------- species-list ----------

    @Test
    fun `décode une liste d'espèces`() {
        val dto = decode(PerenualSpeciesListDto.serializer(), "species_list_monstera.json")

        assertEquals(2, dto.data.size)
        assertEquals(1786, dto.data.first().id)
        assertEquals("Swiss cheese plant", dto.data.first().commonName)
        assertEquals(listOf("Monstera deliciosa"), dto.data.first().scientificName)
    }

    @Test
    fun `accepte sunlight en tableau comme en chaîne`() {
        val dto = decode(PerenualSpeciesListDto.serializer(), "species_list_monstera.json")

        assertEquals(listOf("part shade", "filtered shade"), dto.data[0].sunlight)
        assertEquals(listOf("part shade"), dto.data[1].sunlight)
    }

    @Test
    fun `tolère une image absente`() {
        val dto = decode(PerenualSpeciesListDto.serializer(), "species_list_monstera.json")

        assertNull(dto.data[1].defaultImage)
        assertEquals(
            "https://perenual.com/storage/species_image/1786_monstera_deliciosa/regular/x.jpg",
            dto.data[0].defaultImage?.bestUrl,
        )
    }

    @Test
    fun `décode une liste vide`() {
        val dto = decode(PerenualSpeciesListDto.serializer(), "species_list_empty.json")

        assertTrue(dto.data.isEmpty())
        assertEquals(0, dto.total)
    }

    // ---------- species/details ----------

    @Test
    fun `décode une fiche détaillée`() {
        val dto = decode(PerenualSpeciesDetailsDto.serializer(), "species_details_monstera.json")

        assertEquals(1786, dto.id)
        assertEquals("Araceae", dto.family)
        assertEquals("Average", dto.watering)
        assertEquals("Medium", dto.careLevel)
        assertEquals(listOf("March", "April"), dto.pruningMonth)
        assertEquals(listOf("Stem cutting", "Air layering", "Seed"), dto.propagation)
    }

    @Test
    fun `dépouille le repère d'arrosage doublement échappé`() {
        val dto = decode(PerenualSpeciesDetailsDto.serializer(), "species_details_monstera.json")

        assertEquals("\"7-10\"", dto.wateringGeneralBenchmark?.value)
        assertEquals("days", dto.wateringGeneralBenchmark?.unit)

        // Le nettoyage effectif est du ressort du parseur de repère.
        val benchmark = fr.plantarrosage.core.care.BenchmarkParser.parse(
            dto.wateringGeneralBenchmark?.value,
            dto.wateringGeneralBenchmark?.unit,
        )
        assertEquals(9, benchmark?.days)
    }

    @Test
    fun `accepte la toxicité en entier`() {
        val dto = decode(PerenualSpeciesDetailsDto.serializer(), "species_details_monstera.json")

        assertEquals(true, dto.poisonousToHumans)
        assertEquals(true, dto.poisonousToPets)
        assertEquals(false, dto.droughtTolerant)
    }

    @Test
    fun `accepte la toxicité en booléen comme en chaîne`() {
        val dto = decode(PerenualSpeciesDetailsDto.serializer(), "species_details_free_tier_quirks.json")

        assertEquals(true, dto.poisonousToHumans) // true
        assertEquals(true, dto.poisonousToPets) // "1"
        assertEquals(false, dto.droughtTolerant) // "0"
    }

    @Test
    fun `accepte scientific_name en chaîne simple`() {
        val dto = decode(PerenualSpeciesDetailsDto.serializer(), "species_details_free_tier_quirks.json")

        assertEquals(listOf("Spathiphyllum wallisii"), dto.scientificName)
    }

    @Test
    fun `accepte une rusticité numérique`() {
        val dto = decode(PerenualSpeciesDetailsDto.serializer(), "species_details_free_tier_quirks.json")

        assertEquals("11", dto.hardiness?.min)
        assertEquals("12", dto.hardiness?.max)
    }

    @Test
    fun `décode le repère d'arrosage même s'il arrive en chaîne`() {
        val dto = decode(PerenualSpeciesDetailsDto.serializer(), "species_details_free_tier_quirks.json")

        assertEquals("upgrade required", dto.wateringGeneralBenchmark?.value)
        assertNull(
            fr.plantarrosage.core.care.BenchmarkParser.parse(
                dto.wateringGeneralBenchmark?.value,
                dto.wateringGeneralBenchmark?.unit,
            )
        )
    }

    @Test
    fun `accepte pruning_month en chaîne sentinelle`() {
        val dto = decode(PerenualSpeciesDetailsDto.serializer(), "species_details_free_tier_quirks.json")

        assertEquals(listOf("upgrade required"), dto.pruningMonth)
    }

    // ---------- care guide ----------

    @Test
    fun `décode un guide d'entretien`() {
        val dto = decode(PerenualCareGuideListDto.serializer(), "care_guide_monstera.json")

        assertEquals(1, dto.data.size)
        assertEquals(3, dto.data.first().section.size)
        assertEquals(
            listOf("watering", "sunlight", "pruning"),
            dto.data.first().section.map { it.type },
        )
    }

    // ---------- mapping ----------

    @Test
    fun `la sentinelle du palier gratuit ne remonte jamais dans la fiche`() {
        val entries = PerenualMapper.toEntries(
            decode(PerenualSpeciesListDto.serializer(), "species_list_monstera.json")
        )
        val details = decode(PerenualSpeciesDetailsDto.serializer(), "species_details_free_tier_quirks.json")

        val sheet = PerenualMapper.toFullSheet(
            candidate = candidate(),
            entry = entries.first(),
            details = details,
            guide = null,
            matchQuality = fr.plantarrosage.core.model.MatchQuality.EXACT,
        )

        val champs = listOfNotNull(
            sheet.description,
            sheet.maintenanceFr,
            sheet.wateringBenchmarkFr,
        ) + sheet.pruningMonthsFr

        champs.forEach { valeur ->
            assertFalse(valeur.lowercase().contains("upgrade"), "« $valeur » laisse fuiter la sentinelle")
        }
    }

    @Test
    fun `construit une fiche complète traduite`() {
        val entries = PerenualMapper.toEntries(
            decode(PerenualSpeciesListDto.serializer(), "species_list_monstera.json")
        )
        val sheet = PerenualMapper.toFullSheet(
            candidate = candidate(),
            entry = entries.first(),
            details = decode(PerenualSpeciesDetailsDto.serializer(), "species_details_monstera.json"),
            guide = decode(PerenualCareGuideListDto.serializer(), "care_guide_monstera.json"),
            matchQuality = fr.plantarrosage.core.model.MatchQuality.EXACT,
        )

        assertEquals("Arrosage modéré", sheet.wateringFr)
        assertEquals(9, sheet.baseWateringIntervalDays)
        assertEquals(listOf("Mi-ombre", "Ombre filtrée"), sheet.sunlightFr)
        assertEquals("Vivace", sheet.cycleFr)
        assertEquals("Moyen", sheet.careLevelFr)
        assertEquals("Croissance rapide", sheet.growthRateFr)
        assertEquals("Zones USDA 10 à 12", sheet.hardinessFr)
        assertEquals(listOf("mars", "avril"), sheet.pruningMonthsFr)
        assertEquals(listOf("Bouture de tige", "Marcottage aérien", "Semis"), sheet.propagationFr)
        assertEquals(true, sheet.poisonousToPets)
        assertEquals(3, sheet.guideSections.size)
        assertEquals("Arrosage", sheet.guideSections.first().titleFr)
    }

    @Test
    fun `les textes libres restent dans leur langue d'origine et le déclarent`() {
        val entries = PerenualMapper.toEntries(
            decode(PerenualSpeciesListDto.serializer(), "species_list_monstera.json")
        )
        val sheet = PerenualMapper.toFullSheet(
            candidate = candidate(),
            entry = entries.first(),
            details = decode(PerenualSpeciesDetailsDto.serializer(), "species_details_monstera.json"),
            guide = null,
            matchQuality = fr.plantarrosage.core.model.MatchQuality.EXACT,
        )

        assertEquals("en", sheet.descriptionLanguage)
        assertTrue(sheet.description!!.startsWith("Monstera deliciosa is a striking"))
    }

    @Test
    fun `une fiche résumée reste exploitable pour l'arrosage`() {
        val entries = PerenualMapper.toEntries(
            decode(PerenualSpeciesListDto.serializer(), "species_list_monstera.json")
        )

        val sheet = PerenualMapper.toSummarySheet(
            candidate = candidate(),
            entry = entries.first(),
            matchQuality = fr.plantarrosage.core.model.MatchQuality.EXACT,
        )

        assertEquals(fr.plantarrosage.core.model.DetailLevel.SUMMARY, sheet.detailLevel)
        assertEquals(7, sheet.baseWateringIntervalDays) // « Average »
        assertEquals("Arrosage modéré", sheet.wateringFr)
        assertNull(sheet.description)
    }

    private fun candidate() = fr.plantarrosage.core.model.IdentificationCandidate(
        scientificName = "Monstera deliciosa",
        genus = "Monstera",
        family = "Araceae",
        commonNames = listOf("Monstera", "Faux philodendron"),
        score = 0.87,
    )
}
