package fr.plantarrosage.core.care

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NextWateringCalculatorTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val reminderHour = 9

    private fun instantAt(date: String, hour: Int = 12): Instant =
        LocalDateTime.of(LocalDate.parse(date), java.time.LocalTime.of(hour, 0))
            .atZone(paris)
            .toInstant()

    private fun localDateOf(instant: Instant): LocalDate = instant.atZone(paris).toLocalDate()

    @Test
    fun `l'échéance part du dernier arrosage`() {
        val next = NextWateringCalculator.nextDue(
            lastWateredAt = instantAt("2026-04-10"),
            createdAt = instantAt("2026-01-01"),
            intervalDays = 7,
            reminderHour = reminderHour,
            zone = paris,
        )

        assertEquals(LocalDate.parse("2026-04-17"), localDateOf(next))
    }

    @Test
    fun `sans arrosage connu l'échéance part de la date d'ajout`() {
        val next = NextWateringCalculator.nextDue(
            lastWateredAt = null,
            createdAt = instantAt("2026-04-10"),
            intervalDays = 5,
            reminderHour = reminderHour,
            zone = paris,
        )

        assertEquals(LocalDate.parse("2026-04-15"), localDateOf(next))
    }

    @Test
    fun `l'échéance est posée à l'heure de rappel choisie`() {
        val next = NextWateringCalculator.nextDue(
            lastWateredAt = instantAt("2026-04-10"),
            createdAt = instantAt("2026-01-01"),
            intervalDays = 7,
            reminderHour = 18,
            zone = paris,
        )

        assertEquals(18, next.atZone(paris).hour)
    }

    @Test
    fun `une heure de rappel aberrante est ramenée dans les bornes`() {
        val next = NextWateringCalculator.nextDue(
            lastWateredAt = instantAt("2026-04-10"),
            createdAt = instantAt("2026-01-01"),
            intervalDays = 7,
            reminderHour = 42,
            zone = paris,
        )

        assertEquals(23, next.atZone(paris).hour)
    }

    @Test
    fun `le passage à l'heure d'été ne décale pas la date d'échéance`() {
        // En 2027, la France passe à l'heure d'été le 28 mars.
        val next = NextWateringCalculator.nextDue(
            lastWateredAt = instantAt("2027-03-25"),
            createdAt = instantAt("2027-01-01"),
            intervalDays = 7,
            reminderHour = reminderHour,
            zone = paris,
        )

        assertEquals(LocalDate.parse("2027-04-01"), localDateOf(next))
        assertEquals(reminderHour, next.atZone(paris).hour)
    }

    @Test
    fun `le retour à l'heure d'hiver ne décale pas la date d'échéance`() {
        // En 2026, la France repasse à l'heure d'hiver le 25 octobre.
        val next = NextWateringCalculator.nextDue(
            lastWateredAt = instantAt("2026-10-22"),
            createdAt = instantAt("2026-01-01"),
            intervalDays = 7,
            reminderHour = reminderHour,
            zone = paris,
        )

        assertEquals(LocalDate.parse("2026-10-29"), localDateOf(next))
        assertEquals(reminderHour, next.atZone(paris).hour)
    }

    @Test
    fun `une année bissextile est correctement franchie`() {
        val next = NextWateringCalculator.nextDue(
            lastWateredAt = instantAt("2028-02-26"),
            createdAt = instantAt("2028-01-01"),
            intervalDays = 4,
            reminderHour = reminderHour,
            zone = paris,
        )

        assertEquals(LocalDate.parse("2028-03-01"), localDateOf(next))
    }

    @Test
    fun `le fuseau est bien celui qu'on injecte`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val next = NextWateringCalculator.nextDue(
            lastWateredAt = instantAt("2026-04-10"),
            createdAt = instantAt("2026-01-01"),
            intervalDays = 7,
            reminderHour = reminderHour,
            zone = tokyo,
        )

        assertEquals(reminderHour, next.atZone(tokyo).hour)
    }

    // ---------- Compteurs ----------

    @Test
    fun `compte les jours restants avant l'échéance`() {
        val days = NextWateringCalculator.daysUntil(
            nextDueAt = instantAt("2026-04-17", hour = 9),
            now = instantAt("2026-04-15", hour = 20),
            zone = paris,
        )

        assertEquals(2, days)
    }

    @Test
    fun `un retard est compté négativement`() {
        val days = NextWateringCalculator.daysUntil(
            nextDueAt = instantAt("2026-04-10", hour = 9),
            now = instantAt("2026-04-13", hour = 8),
            zone = paris,
        )

        assertEquals(-3, days)
    }

    @Test
    fun `compte les jours depuis le dernier arrosage`() {
        val days = NextWateringCalculator.daysSinceLastWatering(
            lastWateredAt = instantAt("2026-04-01"),
            now = instantAt("2026-04-10"),
            zone = paris,
        )

        assertEquals(9, days)
    }

    @Test
    fun `sans arrosage connu il n'y a pas de compte`() {
        assertNull(NextWateringCalculator.daysSinceLastWatering(null, instantAt("2026-04-10"), paris))
    }

    @Test
    fun `une échéance passée ou présente est due`() {
        val now = instantAt("2026-04-15", hour = 12)

        assertTrue(NextWateringCalculator.isDue(instantAt("2026-04-15", hour = 9), now))
        assertTrue(NextWateringCalculator.isDue(now, now))
        assertFalse(NextWateringCalculator.isDue(instantAt("2026-04-15", hour = 18), now))
    }

    @Test
    fun `le report décale d'une journée en conservant l'heure`() {
        val due = instantAt("2026-04-15", hour = 9)
        val snoozed = NextWateringCalculator.snoozeOneDay(due, paris)

        assertEquals(LocalDate.parse("2026-04-16"), localDateOf(snoozed))
        assertEquals(9, snoozed.atZone(paris).hour)
    }

    // ---------- Rendu ----------

    @ParameterizedTest(name = "{0} jours → {1}")
    @CsvSource(
        value = [
            "-5 | En retard de 5 jours",
            "-1 | En retard d'un jour",
            "0  | À arroser aujourd'hui",
            "1  | Demain",
            "4  | Dans 4 jours",
        ],
        delimiter = '|',
    )
    fun `rend un texte français lisible`(days: Long, expected: String) {
        assertEquals(expected.trim(), NextWateringCalculator.humanReadableFr(days))
    }
}
