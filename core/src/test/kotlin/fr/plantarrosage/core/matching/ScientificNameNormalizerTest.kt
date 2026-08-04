package fr.plantarrosage.core.matching

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class ScientificNameNormalizerTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
        value = [
            "Monstera deliciosa Liebm.        | monstera deliciosa",
            "Monstera deliciosa               | monstera deliciosa",
            "Aloe vera (L.) Burm.f.           | aloe vera",
            "Ficus elastica 'Robusta'         | ficus elastica",
            "Lavandula angustifolia subsp. angustifolia | lavandula angustifolia",
            "Capsicum annuum var. glabriusculum         | capsicum annuum",
            "Épipremnum aureum                | epipremnum aureum",
            "  Pilea   peperomioides          | pilea peperomioides",
            "Chlorophytum comosum (Thunb.) Jacques | chlorophytum comosum",
        ],
        delimiter = '|',
    )
    fun `réduit un nom enrichi à son binôme`(raw: String, expected: String) {
        val result = ScientificNameNormalizer.normalize(raw.trim())
        assertEquals(expected.trim(), result?.binomial)
    }

    @Test
    fun `retire un cultivar entre guillemets droits`() {
        val result = ScientificNameNormalizer.normalize("Ficus elastica \"Variegata\"")

        assertEquals("ficus elastica", result?.binomial)
    }

    @Test
    fun `retire le marqueur d'hybride et le signale`() {
        val result = ScientificNameNormalizer.normalize("Rosa × damascena")

        assertEquals("rosa damascena", result?.binomial)
        assertTrue(result!!.isHybrid)
    }

    @Test
    fun `reconnaît un marqueur d'hybride collé à l'épithète`() {
        val result = ScientificNameNormalizer.normalize("Rosa ×damascena")

        assertEquals("rosa damascena", result?.binomial)
        assertTrue(result!!.isHybrid)
    }

    @Test
    fun `reconnaît un nothogenre en tête`() {
        val result = ScientificNameNormalizer.normalize("× Cupressocyparis leylandii")

        assertEquals("cupressocyparis leylandii", result?.binomial)
        assertTrue(result!!.isHybrid)
    }

    @Test
    fun `un nom non hybride ne porte pas le drapeau`() {
        assertFalse(ScientificNameNormalizer.normalize("Monstera deliciosa")!!.isHybrid)
    }

    @ParameterizedTest
    @ValueSource(strings = ["Sedum sp.", "Sedum spp.", "Sedum sp", "Sedum indet."])
    fun `un taxon indéterminé ne garde que le genre`(raw: String) {
        val result = ScientificNameNormalizer.normalize(raw)

        assertEquals("sedum", result?.binomial)
        assertNull(result?.epithet)
        assertTrue(result!!.isGenusOnly)
    }

    @Test
    fun `un genre seul reste exploitable`() {
        val result = ScientificNameNormalizer.normalize("Monstera")

        assertEquals("monstera", result?.genus)
        assertNull(result?.epithet)
        assertTrue(result!!.isGenusOnly)
    }

    @Test
    fun `une initiale d'autorité n'est pas prise pour une épithète`() {
        val result = ScientificNameNormalizer.normalize("Aloe vera L.")

        assertEquals("aloe vera", result?.binomial)
    }

    @Test
    fun `un genre suivi d'une seule initiale reste un genre seul`() {
        val result = ScientificNameNormalizer.normalize("Aloe L.")

        assertEquals("aloe", result?.binomial)
        assertNull(result?.epithet)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "   \t  "])
    fun `une entrée vide ne produit rien`(raw: String) {
        assertNull(ScientificNameNormalizer.normalize(raw))
    }

    @Test
    fun `une entrée sans lettre ne produit rien`() {
        assertNull(ScientificNameNormalizer.normalize("123 456"))
    }

    @Test
    fun `la normalisation est idempotente`() {
        val once = ScientificNameNormalizer.normalize("Monstera deliciosa Liebm.")!!.binomial
        val twice = ScientificNameNormalizer.normalize(once)!!.binomial

        assertEquals(once, twice)
    }

    @Test
    fun `conserve la chaîne d'origine pour l'affichage`() {
        val result = ScientificNameNormalizer.normalize("  Monstera deliciosa Liebm.  ")

        assertEquals("Monstera deliciosa Liebm.", result?.original)
    }
}
