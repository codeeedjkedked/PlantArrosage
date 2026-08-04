package fr.plantarrosage.core.util

import java.text.Normalizer

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val WHITESPACE = Regex("\\s+")

/**
 * Retire les diacritiques (é → e, ü → u) en passant par la décomposition NFD.
 * Indispensable avant toute comparaison de noms : Pl@ntNet et Perenual ne les accentuent pas
 * de la même manière.
 */
fun String.stripDiacritics(): String =
    COMBINING_MARKS.replace(Normalizer.normalize(this, Normalizer.Form.NFD), "")

/** Minuscules, sans accents, espaces compactés, débarrassé des blancs de bord. */
fun String.normalizeForComparison(): String =
    stripDiacritics().lowercase().replace(WHITESPACE, " ").trim()

/** Découpe en tokens alphanumériques normalisés, en ignorant les tokens vides. */
fun String.significantTokens(): List<String> =
    normalizeForComparison()
        .split(Regex("[^\\p{Alnum}]+"))
        .filter { it.length >= 3 }
