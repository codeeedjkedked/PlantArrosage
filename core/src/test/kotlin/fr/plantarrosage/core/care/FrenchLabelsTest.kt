package fr.plantarrosage.core.care

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class FrenchLabelsTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
        "Frequent, Arrosage fréquent",
        "Average, Arrosage modéré",
        "Minimum, Arrosage rare",
        "None, Pas d'arrosage",
        "frequent, Arrosage fréquent",
        "FREQUENT, Arrosage fréquent",
    )
    fun `traduit toutes les valeurs d'arrosage documentées`(raw: String, expected: String) {
        assertEquals(expected, FrenchLabels.watering(raw))
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
        "full_sun, Plein soleil",
        "full sun, Plein soleil",
        "part_shade, Mi-ombre",
        "part shade, Mi-ombre",
        "sun-part_shade, Soleil à mi-ombre",
        "full_shade, Ombre",
        "deep shade, Ombre dense",
    )
    fun `traduit toutes les expositions documentées`(raw: String, expected: String) {
        assertEquals(expected, FrenchLabels.sunlight(raw))
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
        "Perennial, Vivace",
        "Annual, Annuelle",
        "Biennial, Bisannuelle",
        "Herbaceous Perennial, Vivace herbacée",
    )
    fun `traduit les cycles`(raw: String, expected: String) {
        assertEquals(expected, FrenchLabels.cycle(raw))
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource("Low, Facile", "Medium, Moyen", "Moderate, Moyen", "High, Exigeant")
    fun `traduit les niveaux d'entretien`(raw: String, expected: String) {
        assertEquals(expected, FrenchLabels.careLevel(raw))
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource("Low, Croissance lente", "Moderate, Croissance moyenne", "High, Croissance rapide")
    fun `traduit les vitesses de croissance`(raw: String, expected: String) {
        assertEquals(expected, FrenchLabels.growthRate(raw))
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource("Low, Entretien réduit", "Moderate, Entretien moyen", "High, Entretien soutenu")
    fun `traduit les charges d'entretien`(raw: String, expected: String) {
        assertEquals(expected, FrenchLabels.maintenance(raw))
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
        "Cutting, Bouturage",
        "Layering, Marcottage",
        "Division, Division",
        "Seed, Semis",
        "Grafting, Greffage",
        "Air layering, Marcottage aérien",
    )
    fun `traduit les modes de multiplication`(raw: String, expected: String) {
        assertEquals(expected, FrenchLabels.propagation(raw))
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource("January, janvier", "May, mai", "August, août", "December, décembre")
    fun `traduit les mois`(raw: String, expected: String) {
        assertEquals(expected, FrenchLabels.month(raw))
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource("watering, Arrosage", "sunlight, Exposition", "pruning, Taille")
    fun `traduit les sections du guide`(raw: String, expected: String) {
        assertEquals(expected, FrenchLabels.guideSection(raw))
    }

    // ---------- Robustesse ----------

    @Test
    fun `une valeur inconnue est renvoyée telle quelle plutôt que perdue`() {
        assertEquals("Sporadique", FrenchLabels.watering("Sporadique"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["upgrade required", "Upgrade Required"])
    fun `la sentinelle du palier gratuit est traitée comme une absence`(raw: String) {
        assertNull(FrenchLabels.watering(raw))
        assertNull(FrenchLabels.careLevel(raw))
    }

    @Test
    fun `une valeur absente ne produit rien`() {
        assertNull(FrenchLabels.watering(null))
        assertNull(FrenchLabels.sunlight(""))
        assertNull(FrenchLabels.cycle("   "))
    }

    @Test
    fun `aucun libellé traduit ne laisse passer de mot anglais`() {
        val labels = listOfNotNull(
            FrenchLabels.watering("Frequent"),
            FrenchLabels.sunlight("full_sun"),
            FrenchLabels.cycle("Perennial"),
            FrenchLabels.careLevel("Low"),
            FrenchLabels.growthRate("High"),
            FrenchLabels.maintenance("Moderate"),
            FrenchLabels.propagation("Cutting"),
        )
        val motsAnglais = listOf("frequent", "sun", "perennial", "low", "high", "moderate", "cutting")

        labels.forEach { label ->
            motsAnglais.forEach { mot ->
                assertFalse(label.lowercase().contains(mot), "« $label » contient « $mot »")
            }
        }
    }

    // ---------- Rusticité et toxicité ----------

    @Test
    fun `rend la plage de rusticité`() {
        assertEquals("Zones USDA 9 à 11", FrenchLabels.hardiness("9", "11"))
        assertEquals("Zone USDA 9", FrenchLabels.hardiness("9", "9"))
        assertEquals("Zone USDA 9", FrenchLabels.hardiness("9", null))
        assertEquals("Zone USDA 11", FrenchLabels.hardiness(null, "11"))
        assertNull(FrenchLabels.hardiness(null, null))
    }

    @Test
    fun `rend la toxicité de façon explicite`() {
        assertEquals(
            "Toxique pour les humains et les animaux domestiques",
            FrenchLabels.toxicity(true, true),
        )
        assertEquals("Toxique pour les humains", FrenchLabels.toxicity(true, false))
        assertEquals("Toxique pour les animaux domestiques", FrenchLabels.toxicity(false, true))
        assertEquals("Aucune toxicité connue", FrenchLabels.toxicity(false, false))
    }

    @Test
    fun `une toxicité inconnue n'affirme rien`() {
        assertNull(FrenchLabels.toxicity(null, null))
    }
}
