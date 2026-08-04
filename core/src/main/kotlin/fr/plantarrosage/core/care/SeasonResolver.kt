package fr.plantarrosage.core.care

import fr.plantarrosage.core.model.Season
import java.time.LocalDate
import java.time.Month

/**
 * Saison de l'hémisphère nord à une date donnée.
 *
 * Découpage volontairement calé sur le rythme d'arrosage des plantes et non sur les solstices :
 * novembre marque déjà le ralentissement de la croissance, juin l'accélération de l'évaporation.
 */
object SeasonResolver {

    fun seasonOf(date: LocalDate): Season = when (date.month) {
        Month.NOVEMBER, Month.DECEMBER, Month.JANUARY, Month.FEBRUARY -> Season.HIVER
        Month.MARCH, Month.APRIL, Month.MAY -> Season.PRINTEMPS
        Month.JUNE, Month.JULY, Month.AUGUST -> Season.ETE
        Month.SEPTEMBER, Month.OCTOBER -> Season.AUTOMNE
    }
}
