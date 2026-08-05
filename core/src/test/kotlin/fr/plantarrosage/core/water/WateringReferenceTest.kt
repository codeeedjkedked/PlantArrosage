package fr.plantarrosage.core.water

import fr.plantarrosage.core.care.WateringIntervalCalculator
import fr.plantarrosage.core.matching.ScientificNameNormalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class WateringReferenceTest {

    private fun nom(raw: String) = ScientificNameNormalizer.normalize(raw)

    // ---------- Intégrité du fichier ----------

    @Test
    fun `le fichier se charge et n'est pas vide`() {
        assertTrue(WateringReference.entries.size > 200, "${WateringReference.entries.size} entrées")
        assertTrue(WateringReference.disclaimerFr.isNotBlank())
    }

    @Test
    fun `aucune clé n'est en double`() {
        val doublons = WateringReference.entries
            .groupBy { it.key }
            .filterValues { it.size > 1 }
            .keys

        assertTrue(doublons.isEmpty(), "clés en double : $doublons")
    }

    @Test
    fun `toutes les clés botaniques sont déjà normalisées`() {
        // Sinon la recherche échouerait silencieusement : le nom identifié est normalisé, la clé
        // du fichier doit l'être exactement de la même manière.
        val fautives = WateringReference.entries
            .filter { it.rank == WateringRank.ESPECE || it.rank == WateringRank.GENRE }
            .filter { entry -> nom(entry.key)?.binomial != entry.key }
            .map { it.key }

        assertTrue(fautives.isEmpty(), "clés non normalisées : $fautives")
    }

    @Test
    fun `tous les intervalles restent dans les bornes admises`() {
        val horsBornes = WateringReference.entries.filter {
            it.intervalDays < WateringIntervalCalculator.MIN_INTERVAL_DAYS ||
                it.intervalDays > WateringIntervalCalculator.MAX_INTERVAL_DAYS
        }

        assertTrue(horsBornes.isEmpty(), "hors bornes : ${horsBornes.map { it.key to it.intervalDays }}")
    }

    @Test
    fun `tout conseil est renseigné et rédigé en français`() {
        val motsAnglais = listOf(" water ", " the ", " soil ", " dry ", " plant ")

        WateringReference.entries.forEach { entry ->
            assertTrue(entry.adviceFr.length >= 20, "conseil trop court pour ${entry.key}")
            val minuscule = " ${entry.adviceFr.lowercase()} "
            motsAnglais.forEach { mot ->
                assertTrue(!minuscule.contains(mot), "« ${entry.key} » contient « $mot »")
            }
        }
    }

    @Test
    fun `les pièges renseignés sont substantiels`() {
        WateringReference.entries.mapNotNull { it.pitfallFr }.forEach { piege ->
            assertTrue(piege.length >= 20, "piège trop court : $piege")
        }
    }

    @Test
    fun `la tolérance à la sécheresse est cohérente avec l'intervalle`() {
        // Une plante annoncée très tolérante ne peut pas réclamer un arrosage tous les trois jours.
        val incoherentes = WateringReference.entries.filter {
            (it.droughtTolerance == DroughtTolerance.ELEVEE && it.intervalDays < 8) ||
                (it.droughtTolerance == DroughtTolerance.FAIBLE && it.intervalDays > 10)
        }

        assertTrue(incoherentes.isEmpty(), "incohérentes : ${incoherentes.map { it.key }}")
    }

    // ---------- Cascade de recherche ----------

    @Test
    fun `l'espèce l'emporte sur le genre`() {
        val entree = WateringReference.lookup(nom("Ficus lyrata"))

        assertEquals(WateringRank.ESPECE, entree?.rank)
        assertEquals("ficus lyrata", entree?.key)
    }

    @Test
    fun `le genre répond quand l'espèce est inconnue`() {
        val entree = WateringReference.lookup(nom("Ficus altissima"))

        assertEquals(WateringRank.GENRE, entree?.rank)
        assertEquals("ficus", entree?.key)
    }

    @Test
    fun `la famille prend le relais quand le genre est absent`() {
        val entree = WateringReference.lookup(nom("Gymnocalycium mihanovichii"), family = "Cactaceae")

        assertEquals(WateringRank.FAMILLE, entree?.rank)
        assertEquals("cactaceae", entree?.key)
    }

    @Test
    fun `le type sert de dernier recours`() {
        val entree = WateringReference.lookup(
            nom("Plante inconnue"),
            family = "Famillebidon",
            typeHint = "succulente",
        )

        assertEquals(WateringRank.TYPE, entree?.rank)
    }

    @Test
    fun `une espèce totalement inconnue ne renvoie rien`() {
        assertNull(WateringReference.lookup(nom("Zzzzzz inexistantus")))
    }

    @Test
    fun `un nom illisible ne fait pas échouer la recherche`() {
        assertNull(WateringReference.lookup(null))
    }

    @Test
    fun `une entrée de genre ne répond pas à une recherche d'espèce d'un autre genre`() {
        val entree = WateringReference.lookup(nom("Rosa gallica"))

        assertEquals("rosa", entree?.key)
        assertEquals(WateringRank.GENRE, entree?.rank)
    }

    // ---------- Attribution ----------

    @Test
    fun `l'attribution nomme le niveau de la donnée`() {
        val espece = WateringReference.lookup(nom("Ficus lyrata"))!!
        val genre = WateringReference.lookup(nom("Monstera adansonii"))!!

        assertTrue(WateringReference.attribution(espece).contains("Ficus lyrata"))
        assertTrue(WateringReference.attribution(genre).contains("genre"))
        assertTrue(WateringReference.attribution(genre).contains("PlantArrosage"))
    }

    // ---------- Couverture réelle ----------

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = [
        "Monstera deliciosa", "Ficus lyrata", "Ficus elastica", "Ficus benjamina",
        "Epipremnum aureum", "Philodendron scandens", "Zamioculcas zamiifolia",
        "Sansevieria trifasciata", "Spathiphyllum wallisii", "Chlorophytum comosum",
        "Dracaena marginata", "Calathea orbifolia", "Maranta leuconeura",
        "Pilea peperomioides", "Peperomia obtusifolia", "Aloe vera", "Crassula ovata",
        "Echeveria elegans", "Haworthia fasciata", "Sedum morganianum",
        "Phalaenopsis amabilis", "Nephrolepis exaltata", "Adiantum raddianum",
        "Howea forsteriana", "Chamaedorea elegans", "Dypsis lutescens",
        "Anthurium andraeanum", "Alocasia amazonica", "Begonia rex", "Hedera helix",
        "Tradescantia zebrina", "Schefflera arboricola", "Yucca elephantipes",
        "Aglaonema commutatum", "Dieffenbachia seguine", "Syngonium podophyllum",
        "Hoya carnosa", "Schlumbergera truncata", "Cyclamen persicum",
        "Rosa gallica", "Lavandula angustifolia", "Hydrangea macrophylla",
        "Rhododendron ponticum", "Camellia japonica", "Acer palmatum",
        "Hosta sieboldiana", "Iris germanica", "Paeonia lactiflora",
        "Tulipa gesneriana", "Narcissus pseudonarcissus", "Lilium candidum",
        "Ocimum basilicum", "Mentha spicata", "Thymus vulgaris",
        "Rosmarinus officinalis", "Petroselinum crispum", "Origanum vulgare",
        "Solanum lycopersicum", "Cucurbita pepo", "Lactuca sativa",
        "Capsicum annuum", "Fragaria vesca", "Vitis vinifera",
        "Prunus avium", "Malus domestica", "Citrus limon", "Olea europaea",
    ])
    fun `les espèces les plus courantes trouvent une entrée`(nomScientifique: String) {
        val entree = WateringReference.lookup(nom(nomScientifique))

        assertNotNull(entree, "aucune entrée pour $nomScientifique")
    }

    @Test
    fun `la couverture des plantes d'intérieur est majoritairement au genre ou mieux`() {
        val temoins = listOf(
            "Monstera deliciosa", "Epipremnum aureum", "Sansevieria trifasciata",
            "Calathea orbifolia", "Aloe vera", "Phalaenopsis amabilis",
            "Nephrolepis exaltata", "Howea forsteriana", "Hoya carnosa", "Pilea peperomioides",
        )

        val precises = temoins.count { nomScientifique ->
            WateringReference.lookup(nom(nomScientifique))?.rank in
                setOf(WateringRank.ESPECE, WateringRank.GENRE)
        }

        assertEquals(temoins.size, precises, "certaines retombent sur la famille ou le type")
    }
}
