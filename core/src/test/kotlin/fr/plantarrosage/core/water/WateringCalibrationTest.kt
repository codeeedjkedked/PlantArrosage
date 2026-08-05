package fr.plantarrosage.core.water

import fr.plantarrosage.core.care.WateringIntervalCalculator
import fr.plantarrosage.core.model.CareSheet
import fr.plantarrosage.core.model.DetailLevel
import fr.plantarrosage.core.model.MatchQuality
import fr.plantarrosage.core.model.PlantLocation
import fr.plantarrosage.core.matching.ScientificNameNormalizer
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Confronte la base curée aux fourchettes publiées par les sources horticoles.
 *
 * Les valeurs du fichier sont des **valeurs de printemps** : le calculateur les resserre l'été
 * (× 0,8) et les détend l'hiver (× 1,6). Vérifier la seule valeur du fichier ne dirait donc rien
 * de ce que l'utilisateur lit réellement en janvier ou en juillet — ce test contrôle le résultat
 * final, saison par saison.
 *
 * Les fourchettes ci-dessous ont été relevées sur des guides horticoles courants, en gardant
 * l'intervalle le plus large cité pour ne pas transformer un désaccord entre jardiniers en échec
 * de test.
 */
class WateringCalibrationTest {

    private val ete = LocalDate.of(2026, 7, 15)
    private val hiver = LocalDate.of(2026, 1, 15)

    /** Une espèce, et les bornes admises en été puis en hiver. */
    data class Attendu(
        val espece: String,
        val eteMin: Int,
        val eteMax: Int,
        val hiverMin: Int,
        val hiverMax: Int,
    ) {
        override fun toString(): String = espece
    }

    private fun ficheDe(espece: String): CareSheet {
        val entree = WateringReference.lookup(ScientificNameNormalizer.normalize(espece))
        requireNotNull(entree) { "« $espece » absent de la base" }

        val base = WateringIntervalCalculator.resolveBase(
            curated = entree,
            benchmarkValue = null,
            benchmarkUnit = null,
            wateringEnum = null,
        )

        return CareSheet(
            scientificName = espece,
            matchQuality = MatchQuality.EXACT,
            detailLevel = DetailLevel.FULL,
            baseWateringIntervalDays = base.days,
            baseIntervalSourceFr = base.sourceFr,
            baseIsCurated = base.isCurated,
            droughtTolerant = base.droughtTolerant,
        )
    }

    private fun jours(espece: String, date: LocalDate): Int =
        WateringIntervalCalculator.compute(
            sheet = ficheDe(espece),
            location = PlantLocation.INTERIEUR,
            today = date,
        ).effectiveIntervalDays

    @ParameterizedTest
    @MethodSource("temoins")
    fun `l'intervalle calculé reste dans la fourchette documentée`(attendu: Attendu) {
        val enEte = jours(attendu.espece, ete)
        val enHiver = jours(attendu.espece, hiver)

        assertAll(
            {
                assertTrue(
                    enEte in attendu.eteMin..attendu.eteMax,
                    "${attendu.espece} : $enEte j en été, attendu ${attendu.eteMin}–${attendu.eteMax}",
                )
            },
            {
                assertTrue(
                    enHiver in attendu.hiverMin..attendu.hiverMax,
                    "${attendu.espece} : $enHiver j en hiver, attendu ${attendu.hiverMin}–${attendu.hiverMax}",
                )
            },
        )
    }

    @Test
    fun `l'hiver espace toujours les arrosages par rapport à l'été`() {
        temoins().forEach { attendu ->
            val enEte = jours(attendu.espece, ete)
            val enHiver = jours(attendu.espece, hiver)
            assertTrue(enHiver > enEte, "${attendu.espece} : $enHiver j en hiver contre $enEte en été")
        }
    }

    @Test
    fun `une plante grasse et une plante de sous-bois ne se ressemblent en rien`() {
        // Le test qui dit le mieux à quoi sert la base : sans elle, les deux affichaient 7 jours.
        val grasse = jours("Sansevieria trifasciata", ete)
        val sousBois = jours("Adiantum raddianum", ete)

        assertTrue(grasse >= sousBois * 5, "$grasse contre $sousBois")
    }

    companion object {
        /**
         * Fourchettes relevées sur les guides horticoles, en juillet et en janvier, pour une
         * plante en pot à l'intérieur.
         */
        @JvmStatic
        fun temoins() = listOf(
            // Succulentes et plantes à réserves : arrosage rare, surarrosage mortel.
            Attendu("Sansevieria trifasciata", eteMin = 12, eteMax = 22, hiverMin = 24, hiverMax = 40),
            Attendu("Zamioculcas zamiifolia", eteMin = 12, eteMax = 22, hiverMin = 24, hiverMax = 45),
            Attendu("Aloe vera", eteMin = 10, eteMax = 21, hiverMin = 21, hiverMax = 40),

            // Feuillages d'intérieur classiques.
            Attendu("Epipremnum aureum", eteMin = 6, eteMax = 12, hiverMin = 12, hiverMax = 20),
            Attendu("Monstera deliciosa", eteMin = 6, eteMax = 12, hiverMin = 12, hiverMax = 22),
            Attendu("Ficus lyrata", eteMin = 6, eteMax = 12, hiverMin = 12, hiverMax = 20),
            Attendu("Spathiphyllum wallisii", eteMin = 4, eteMax = 9, hiverMin = 7, hiverMax = 14),
            Attendu("Phalaenopsis amabilis", eteMin = 5, eteMax = 10, hiverMin = 10, hiverMax = 16),

            // Marantacées et fougères : substrat qui ne doit jamais sécher de part en part.
            Attendu("Calathea orbifolia", eteMin = 4, eteMax = 8, hiverMin = 9, hiverMax = 14),
            Attendu("Maranta leuconeura", eteMin = 5, eteMax = 10, hiverMin = 10, hiverMax = 16),
            Attendu("Nephrolepis exaltata", eteMin = 2, eteMax = 5, hiverMin = 5, hiverMax = 9),

            // Aromatiques en pot.
            Attendu("Ocimum basilicum", eteMin = 2, eteMax = 4, hiverMin = 4, hiverMax = 7),
            Attendu("Rosmarinus officinalis", eteMin = 8, eteMax = 16, hiverMin = 18, hiverMax = 30),
        )
    }
}
