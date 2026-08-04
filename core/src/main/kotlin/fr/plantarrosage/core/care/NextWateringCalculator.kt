package fr.plantarrosage.core.care

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Détermine quand la plante doit être arrosée la prochaine fois.
 *
 * Le calcul passe par [LocalDate.plusDays] et non par une arithmétique en millisecondes : c'est
 * ce qui garantit qu'un changement d'heure ou une année bissextile ne décale pas l'échéance.
 * Le fuseau est un paramètre explicite pour que les tests soient reproductibles.
 */
object NextWateringCalculator {

    /**
     * @param lastWateredAt dernier arrosage connu ; `null` si la plante n'a jamais été arrosée
     * @param createdAt date d'ajout de la plante, utilisée comme ancre par défaut
     * @param intervalDays intervalle effectif issu de [WateringIntervalCalculator]
     * @param reminderHour heure locale à laquelle l'échéance est posée
     */
    fun nextDue(
        lastWateredAt: Instant?,
        createdAt: Instant,
        intervalDays: Int,
        reminderHour: Int,
        zone: ZoneId,
    ): Instant {
        val anchor = (lastWateredAt ?: createdAt).atZone(zone).toLocalDate()
        return anchor
            .plusDays(intervalDays.toLong())
            .atTime(LocalTime.of(reminderHour.coerceIn(0, 23), 0))
            .atZone(zone)
            .toInstant()
    }

    /**
     * Nombre de jours calendaires entre aujourd'hui et l'échéance.
     * Négatif quand la plante est en retard, `0` quand elle est à arroser aujourd'hui.
     */
    fun daysUntil(nextDueAt: Instant, now: Instant, zone: ZoneId): Long {
        val due = nextDueAt.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(today, due)
    }

    /** Nombre de jours écoulés depuis le dernier arrosage, ou `null` s'il n'y en a jamais eu. */
    fun daysSinceLastWatering(lastWateredAt: Instant?, now: Instant, zone: ZoneId): Long? {
        if (lastWateredAt == null) return null
        val last = lastWateredAt.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(last, today)
    }

    fun isDue(nextDueAt: Instant, now: Instant): Boolean = !nextDueAt.isAfter(now)

    /** Repousse l'échéance d'une journée, action « Reporter à demain » de la notification. */
    fun snoozeOneDay(nextDueAt: Instant, zone: ZoneId): Instant =
        nextDueAt.atZone(zone).toLocalDate()
            .plusDays(1)
            .atTime(nextDueAt.atZone(zone).toLocalTime())
            .atZone(zone)
            .toInstant()

    /** Rendu français court, utilisé sur les cartes de l'accueil. */
    fun humanReadableFr(daysUntil: Long): String = when {
        daysUntil < -1 -> "En retard de ${-daysUntil} jours"
        daysUntil == -1L -> "En retard d'un jour"
        daysUntil == 0L -> "À arroser aujourd'hui"
        daysUntil == 1L -> "Demain"
        else -> "Dans $daysUntil jours"
    }
}
