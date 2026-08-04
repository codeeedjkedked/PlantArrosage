package fr.plantarrosage.core.plantnet

import fr.plantarrosage.core.net.HttpClientFactory
import fr.plantarrosage.core.support.Fixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlantNetDtoParsingTest {

    private fun decode(fixture: String): PlantNetResponseDto =
        HttpClientFactory.json.decodeFromString(
            PlantNetResponseDto.serializer(),
            Fixtures.plantNet(fixture),
        )

    @Test
    fun `décode une réponse complète`() {
        val dto = decode("identify_ok.json")

        assertEquals("Monstera deliciosa Liebm.", dto.bestMatch)
        assertEquals(3, dto.results.size)
        assertEquals(462, dto.remainingIdentificationRequests)
    }

    @Test
    fun `ignore les champs inconnus`() {
        // La fixture contient query, gbif, powo, version… aucun n'est déclaré côté DTO.
        val dto = decode("identify_ok.json")

        assertNotNull(dto.results.first().species)
    }

    @Test
    fun `extrait les taxons et les noms communs`() {
        val species = decode("identify_ok.json").results.first().species!!

        assertEquals("Monstera deliciosa", species.scientificNameWithoutAuthor)
        assertEquals("Monstera", species.genus?.scientificNameWithoutAuthor)
        assertEquals("Araceae", species.family?.scientificNameWithoutAuthor)
        assertEquals(listOf("Monstera", "Faux philodendron", "Plante gruyère"), species.commonNames)
    }

    @Test
    fun `décode une réponse sans résultat`() {
        val dto = decode("identify_no_match.json")

        assertTrue(dto.results.isEmpty())
        assertNull(dto.bestMatch)
    }

    @Test
    fun `décode une réponse minimale sans images ni noms communs`() {
        val dto = decode("identify_minimal.json")

        assertEquals(1, dto.results.size)
        assertTrue(dto.results.first().species!!.commonNames.isEmpty())
        assertTrue(dto.results.first().images.isEmpty())
        assertNull(dto.remainingIdentificationRequests)
    }

    // ---------- Mapping vers le domaine ----------

    @Test
    fun `classe les candidats par score décroissant`() {
        val result = PlantNetMapper.toDomain(decode("identify_ok.json"))

        assertEquals(
            listOf("Monstera deliciosa", "Monstera adansonii"),
            result.candidates.map { it.scientificName },
        )
        assertEquals(462, result.remainingRequests)
    }

    @Test
    fun `écarte les candidats au score négligeable`() {
        // La fixture contient Epipremnum aureum à 0,89 % : c'est du bruit, pas une proposition.
        val result = PlantNetMapper.toDomain(decode("identify_ok.json"))

        assertTrue(result.candidates.none { it.scientificName == "Epipremnum aureum" })
    }

    @Test
    fun `retient l'image de taille moyenne`() {
        val candidate = PlantNetMapper.toDomain(decode("identify_ok.json")).candidates.first()

        assertEquals("https://bs.plantnet.org/image/m/aaa.jpg", candidate.relatedImageUrl)
    }

    @Test
    fun `un candidat sans image reste exploitable`() {
        val dto = PlantNetResponseDto(
            results = listOf(
                PlantNetResultDto(
                    score = 0.42,
                    species = PlantNetSpeciesDto(scientificNameWithoutAuthor = "Epipremnum aureum"),
                    images = emptyList(),
                )
            )
        )

        val candidate = PlantNetMapper.toDomain(dto).candidates.single()

        assertEquals("Epipremnum aureum", candidate.scientificName)
        assertNull(candidate.relatedImageUrl)
    }

    @Test
    fun `se rabat sur la petite image quand la moyenne manque`() {
        val dto = PlantNetResponseDto(
            results = listOf(
                PlantNetResultDto(
                    score = 0.42,
                    species = PlantNetSpeciesDto(scientificNameWithoutAuthor = "Rosa gallica"),
                    images = listOf(
                        PlantNetImageDto(url = PlantNetImageUrlDto(small = "https://x/s.jpg")),
                    ),
                )
            )
        )

        assertEquals("https://x/s.jpg", PlantNetMapper.toDomain(dto).candidates.single().relatedImageUrl)
    }

    @Test
    fun `une réponse vide ne produit aucun candidat`() {
        val result = PlantNetMapper.toDomain(decode("identify_no_match.json"))

        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun `écarte les résultats sans nom scientifique`() {
        val dto = PlantNetResponseDto(
            results = listOf(
                PlantNetResultDto(score = 0.9, species = PlantNetSpeciesDto(scientificNameWithoutAuthor = null)),
                PlantNetResultDto(score = 0.8, species = PlantNetSpeciesDto(scientificNameWithoutAuthor = "  ")),
                PlantNetResultDto(score = 0.7, species = PlantNetSpeciesDto(scientificNameWithoutAuthor = "Rosa gallica")),
            )
        )

        val result = PlantNetMapper.toDomain(dto)

        assertEquals(listOf("Rosa gallica"), result.candidates.map { it.scientificName })
    }

    @Test
    fun `expose la bande de confiance`() {
        val candidates = PlantNetMapper.toDomain(decode("identify_ok.json")).candidates

        assertEquals(fr.plantarrosage.core.model.ConfidenceBand.BONNE, candidates[0].confidence)
        assertEquals(fr.plantarrosage.core.model.ConfidenceBand.FAIBLE, candidates[1].confidence)
    }
}
