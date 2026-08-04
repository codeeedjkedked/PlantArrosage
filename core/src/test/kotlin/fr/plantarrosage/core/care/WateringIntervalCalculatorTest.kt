package fr.plantarrosage.core.care

import fr.plantarrosage.core.model.PlantLocation
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class WateringIntervalCalculatorTest {

    private val printemps = LocalDate.of(2026, 4, 15) // multiplicateur saisonnier neutre
    private val hiver = LocalDate.of(2026, 1, 15)
    private val ete = LocalDate.of(2026, 7, 15)

    // ---------- Intervalle de base ----------

    @ParameterizedTest(name = "watering={0} → {1} jours")
    @CsvSource(
        "Frequent, 3",
        "Average, 7",
        "Minimum, 14",
        "None, 30",
        "frequent, 3",
        "AVERAGE, 7",
    )
    fun `l'énumération d'arrosage fournit un repli`(wateringEnum: String, expected: Int) {
        val (days, _) = WateringIntervalCalculator.baseIntervalDays(null, null, wateringEnum)

        assertEquals(expected, days)
    }

    @Test
    fun `le repère chiffré prime sur l'énumération`() {
        val (days, source) = WateringIntervalCalculator.baseIntervalDays("12-14", "days", "Frequent")

        assertEquals(13, days)
        assertTrue(source.contains("Perenual"))
    }

    @Test
    fun `sans aucune donnée on retombe sur la valeur par défaut`() {
        val (days, source) = WateringIntervalCalculator.baseIntervalDays(null, null, null)

        assertEquals(7, days)
        assertTrue(source.contains("défaut"))
    }

    @Test
    fun `une énumération inconnue retombe sur la valeur par défaut`() {
        val (days, _) = WateringIntervalCalculator.baseIntervalDays(null, null, "Sporadique")

        assertEquals(7, days)
    }

    // ---------- Facteurs ----------

    @Test
    fun `au printemps aucun facteur saisonnier n'est appliqué`() {
        val plan = compute(base = 10, today = printemps)

        assertEquals(10, plan.effectiveIntervalDays)
        assertTrue(plan.factors.none { it.labelFr.contains("printemps") })
    }

    @Test
    fun `l'hiver espace les arrosages`() {
        val plan = compute(base = 10, today = hiver)

        assertEquals(16, plan.effectiveIntervalDays) // 10 × 1,6
        assertTrue(plan.factors.any { it.labelFr.contains("hiver") })
    }

    @Test
    fun `l'été rapproche les arrosages`() {
        val plan = compute(base = 10, today = ete)

        assertEquals(8, plan.effectiveIntervalDays) // 10 × 0,8
    }

    @Test
    fun `l'extérieur rapproche les arrosages`() {
        val plan = compute(base = 10, today = printemps, location = PlantLocation.EXTERIEUR)

        assertEquals(9, plan.effectiveIntervalDays) // 10 × 0,85 = 8,5 → 9
        assertTrue(plan.factors.any { it.labelFr.contains("extérieur") })
    }

    @Test
    fun `une plante tolérante à la sécheresse est arrosée moins souvent`() {
        val plan = compute(base = 10, today = printemps, droughtTolerant = true)

        assertEquals(13, plan.effectiveIntervalDays) // 10 × 1,3
    }

    @Test
    fun `le plein soleil rapproche les arrosages`() {
        val plan = compute(base = 10, today = printemps, sunlight = listOf("full_sun"))

        assertEquals(9, plan.effectiveIntervalDays) // 10 × 0,9
    }

    @Test
    fun `l'ombre espace les arrosages`() {
        val plan = compute(base = 10, today = printemps, sunlight = listOf("full_shade"))

        assertEquals(12, plan.effectiveIntervalDays) // 10 × 1,2
    }

    @Test
    fun `le plein soleil l'emporte quand les deux expositions sont listées`() {
        val plan = compute(base = 10, today = printemps, sunlight = listOf("full_sun", "full_shade"))

        assertEquals(9, plan.effectiveIntervalDays)
        assertEquals(1, plan.factors.count { it.labelFr.contains("soleil") || it.labelFr.contains("ombre") })
    }

    @Test
    fun `les facteurs se combinent multiplicativement`() {
        val plan = compute(
            base = 10,
            today = hiver,
            location = PlantLocation.EXTERIEUR,
            droughtTolerant = true,
        )

        // 10 × 1,6 × 0,85 × 1,3 = 17,68 → 18
        assertEquals(18, plan.effectiveIntervalDays)
        assertEquals(3, plan.factors.size)
    }

    // ---------- Bornage ----------

    @Test
    fun `l'intervalle ne descend jamais sous deux jours`() {
        val plan = compute(base = 2, today = ete, location = PlantLocation.EXTERIEUR, sunlight = listOf("full_sun"))

        assertEquals(2, plan.effectiveIntervalDays)
    }

    @Test
    fun `l'intervalle ne dépasse jamais soixante jours`() {
        val plan = compute(base = 60, today = hiver, droughtTolerant = true)

        assertEquals(60, plan.effectiveIntervalDays)
    }

    // ---------- Surcharge utilisateur ----------

    @Test
    fun `le réglage manuel l'emporte sur le calcul`() {
        val plan = compute(base = 10, today = hiver, override = 4)

        assertEquals(4, plan.effectiveIntervalDays)
        assertTrue(plan.isOverridden)
    }

    @Test
    fun `le réglage manuel reste borné`() {
        assertEquals(60, compute(base = 10, today = printemps, override = 900).effectiveIntervalDays)
        assertEquals(2, compute(base = 10, today = printemps, override = 0).effectiveIntervalDays)
    }

    @Test
    fun `le réglage manuel n'efface pas les facteurs affichés`() {
        val plan = compute(base = 10, today = hiver, override = 4)

        assertTrue(plan.factors.any { it.labelFr.contains("hiver") })
    }

    // ---------- Explication ----------

    @Test
    fun `l'explication est en français et cite la base et les facteurs`() {
        val plan = compute(base = 7, today = hiver, location = PlantLocation.EXTERIEUR)
        val explication = plan.explanationFr()

        assertTrue(explication.startsWith("Tous les "), explication)
        assertTrue(explication.contains("base 7 j"), explication)
        assertTrue(explication.contains("hiver"), explication)
        assertTrue(explication.contains("extérieur"), explication)
    }

    @Test
    fun `l'explication signale un réglage personnalisé`() {
        val explication = compute(base = 7, today = hiver, override = 5).explanationFr()

        assertTrue(explication.contains("personnalisé"), explication)
        assertFalse(explication.contains("hiver"), explication)
    }

    @Test
    fun `tous les libellés de facteurs sont en français`() {
        val plan = compute(
            base = 10,
            today = hiver,
            location = PlantLocation.EXTERIEUR,
            droughtTolerant = true,
            sunlight = listOf("full_sun"),
        )

        val motsAnglais = listOf("winter", "outdoor", "drought", "sun", "shade")
        plan.factors.forEach { factor ->
            motsAnglais.forEach { mot ->
                assertFalse(factor.labelFr.lowercase().contains(mot), "${factor.labelFr} contient « $mot »")
            }
        }
    }

    private fun compute(
        base: Int,
        today: LocalDate,
        location: PlantLocation = PlantLocation.INTERIEUR,
        droughtTolerant: Boolean? = null,
        sunlight: List<String> = emptyList(),
        override: Int? = null,
    ) = WateringIntervalCalculator.compute(
        baseIntervalDays = base,
        baseSourceFr = "arrosage modéré",
        sunlightRaw = sunlight,
        droughtTolerant = droughtTolerant,
        location = location,
        today = today,
        userOverrideDays = override,
    )
}
