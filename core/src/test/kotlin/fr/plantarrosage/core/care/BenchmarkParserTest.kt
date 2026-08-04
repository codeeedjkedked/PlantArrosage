package fr.plantarrosage.core.care

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

class BenchmarkParserTest {

    @Test
    fun `lit une plage en jours et prend le milieu`() {
        val result = BenchmarkParser.parse("7-10", "days")

        assertEquals(9, result?.days) // 8,5 arrondi
        assertEquals("repère Perenual : 7 à 10 jours", result?.labelFr)
    }

    @Test
    fun `dépouille les guillemets échappés renvoyés par l'API`() {
        val result = BenchmarkParser.parse("\"7-10\"", "days")

        assertEquals(9, result?.days)
    }

    @Test
    fun `dépouille aussi les antislashs`() {
        val result = BenchmarkParser.parse("\\\"5-7\\\"", "days")

        assertEquals(6, result?.days)
    }

    @Test
    fun `lit une valeur simple`() {
        val result = BenchmarkParser.parse("5", "days")

        assertEquals(5, result?.days)
        assertEquals("repère Perenual : 5 jours", result?.labelFr)
    }

    @Test
    fun `convertit les semaines en jours`() {
        val result = BenchmarkParser.parse("2-3", "weeks")

        assertEquals(18, result?.days) // 2,5 semaines = 17,5 j
        assertEquals("repère Perenual : 14 à 21 jours", result?.labelFr)
    }

    @Test
    fun `convertit les mois en jours`() {
        assertEquals(30, BenchmarkParser.parse("1", "months")?.days)
    }

    @Test
    fun `accepte le tiret cadratin comme séparateur de plage`() {
        assertEquals(9, BenchmarkParser.parse("7–10", "days")?.days)
    }

    @Test
    fun `accepte le mot to comme séparateur de plage`() {
        assertEquals(9, BenchmarkParser.parse("7 to 10", "days")?.days)
    }

    @Test
    fun `accepte une virgule décimale`() {
        assertEquals(8, BenchmarkParser.parse("7,5", "days")?.days)
    }

    @Test
    fun `traite l'unité absente comme des jours`() {
        assertEquals(9, BenchmarkParser.parse("7-10", null)?.days)
    }

    @ParameterizedTest
    @ValueSource(strings = ["average", "n/a", "unknown", "--", "days"])
    fun `une valeur sans nombre ne produit rien`(value: String) {
        assertNull(BenchmarkParser.parse(value, "days"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["upgrade required", "Upgrade Required", "please upgrade your plan"])
    fun `la sentinelle du palier gratuit ne produit rien`(value: String) {
        assertNull(BenchmarkParser.parse(value, "days"))
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["   "])
    fun `une valeur absente ne produit rien`(value: String?) {
        assertNull(BenchmarkParser.parse(value, "days"))
    }

    @Test
    fun `une valeur arrondissant à zéro jour ne produit rien`() {
        assertNull(BenchmarkParser.parse("0", "days"))
    }

    @Test
    fun `ne lève jamais d'exception sur une entrée aberrante`() {
        val aberrantes = listOf("∞", "7-", "-10", "{}", "[7,10]", "7 10 12 14")

        aberrantes.forEach { value ->
            // Le contrat est « pas d'exception » : la valeur retournée importe peu.
            assertTrue(runCatching { BenchmarkParser.parse(value, "days") }.isSuccess, value)
        }
    }
}
