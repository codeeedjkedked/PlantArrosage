package fr.plantarrosage.core.care

import fr.plantarrosage.core.model.Season
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SeasonResolverTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
        "2026-01-15, HIVER",
        "2026-02-28, HIVER",
        "2026-03-01, PRINTEMPS",
        "2026-05-31, PRINTEMPS",
        "2026-06-01, ETE",
        "2026-08-31, ETE",
        "2026-09-01, AUTOMNE",
        "2026-10-31, AUTOMNE",
        "2026-11-01, HIVER",
        "2026-12-31, HIVER",
    )
    fun `associe chaque date à sa saison`(date: String, expected: Season) {
        assertEquals(expected, SeasonResolver.seasonOf(LocalDate.parse(date)))
    }

    @Test
    fun `les multiplicateurs suivent le rythme végétatif`() {
        // L'hiver espace les arrosages, l'été les rapproche.
        assertEquals(1.6, Season.HIVER.multiplier)
        assertEquals(1.0, Season.PRINTEMPS.multiplier)
        assertEquals(0.8, Season.ETE.multiplier)
        assertEquals(1.2, Season.AUTOMNE.multiplier)
    }

    @Test
    fun `couvre les douze mois sans trou`() {
        val couverts = (1..12).map { month ->
            SeasonResolver.seasonOf(LocalDate.of(2026, month, 15))
        }

        assertEquals(12, couverts.size)
        assertEquals(setOf(Season.HIVER, Season.PRINTEMPS, Season.ETE, Season.AUTOMNE), couverts.toSet())
    }
}
