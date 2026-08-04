package fr.plantarrosage.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

class SourceLinksTest {

    @Test
    fun `construit le lien GBIF`() {
        assertEquals(
            "https://www.gbif.org/species/5329113",
            SourceLinks.gbif("5329113")?.url,
        )
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["   ", "abc", "urn:lsid:xyz"])
    fun `un identifiant GBIF non numérique ne produit pas de lien`(raw: String?) {
        assertNull(SourceLinks.gbif(raw))
    }

    @Test
    fun `construit le lien POWO`() {
        assertEquals(
            "https://powo.science.kew.org/taxon/urn:lsid:ipni.org:names:86258-1",
            SourceLinks.powo("urn:lsid:ipni.org:names:86258-1")?.url,
        )
    }

    @Test
    fun `construit le lien Perenual`() {
        assertEquals(
            "https://perenual.com/plant-species-database-search-finder/species/1786",
            SourceLinks.perenual(1786)?.url,
        )
    }

    @Test
    fun `un identifiant Perenual absent ou nul ne produit pas de lien`() {
        assertNull(SourceLinks.perenual(null))
        assertNull(SourceLinks.perenual(0))
        assertNull(SourceLinks.perenual(-3))
    }

    @Test
    fun `construit le lien Wikipédia avec des underscores`() {
        assertEquals(
            "https://fr.wikipedia.org/wiki/Monstera_deliciosa",
            SourceLinks.wikipediaFr("Monstera deliciosa")?.url,
        )
    }

    @Test
    fun `encode les caractères spéciaux d'un nom d'espèce`() {
        val url = SourceLinks.wikipediaFr("Rosa × damascena")!!.url

        // L'espace doit devenir %20 et non « + », qui serait un caractère littéral dans un chemin.
        assertTrue(url.startsWith("https://fr.wikipedia.org/wiki/Rosa_"), url)
        assertTrue(!url.contains("+"), url)
        assertTrue(!url.contains(" "), url)
    }

    @Test
    fun `construit le lien Pl@ntNet`() {
        val url = SourceLinks.plantNet("Monstera deliciosa")!!.url

        assertEquals(
            "https://identify.plantnet.org/fr/k-world-flora/species/Monstera%20deliciosa/data",
            url,
        )
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["   "])
    fun `un nom vide ne produit aucun lien`(raw: String?) {
        assertNull(SourceLinks.wikipediaFr(raw))
        assertNull(SourceLinks.plantNet(raw))
    }

    @Test
    fun `rassemble tous les liens disponibles`() {
        val liens = SourceLinks.forSheet(
            scientificName = "Monstera deliciosa",
            perenualId = 1786,
            gbifId = "5329113",
            powoId = "urn:lsid:ipni.org:names:86258-1",
        )

        assertEquals(5, liens.size)
        assertEquals(
            listOf("Pl@ntNet", "Wikipédia", "GBIF", "POWO (Kew)", "Perenual"),
            liens.map { it.sourceFr },
        )
    }

    @Test
    fun `omet silencieusement les liens indisponibles`() {
        val liens = SourceLinks.forSheet(
            scientificName = "Monstera deliciosa",
            perenualId = null,
            gbifId = null,
            powoId = null,
        )

        assertEquals(listOf("Pl@ntNet", "Wikipédia"), liens.map { it.sourceFr })
    }

    @Test
    fun `sans nom d'espèce il ne reste que les liens par identifiant`() {
        val liens = SourceLinks.forSheet(
            scientificName = null,
            perenualId = 1786,
            gbifId = "5329113",
            powoId = null,
        )

        assertEquals(listOf("GBIF", "Perenual"), liens.map { it.sourceFr })
    }

    @Test
    fun `chaque lien porte un libellé français`() {
        val liens = SourceLinks.forSheet("Monstera deliciosa", 1786, "5329113", "urn:x")

        liens.forEach { lien ->
            assertTrue(lien.labelFr.isNotBlank(), "libellé vide pour ${lien.sourceFr}")
            assertTrue(lien.url.startsWith("https://"), lien.url)
        }
    }
}
