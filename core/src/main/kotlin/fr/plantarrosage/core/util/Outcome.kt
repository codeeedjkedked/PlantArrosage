package fr.plantarrosage.core.util

import fr.plantarrosage.core.model.AppError

/**
 * Résultat explicite d'une opération faillible.
 *
 * On n'utilise pas [kotlin.Result] : il encapsule des [Throwable], alors qu'ici l'échec est une
 * valeur métier ([AppError]) qui porte déjà son message français.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value

fun <T> Outcome<T>.errorOrNull(): AppError? = (this as? Outcome.Failure)?.error
