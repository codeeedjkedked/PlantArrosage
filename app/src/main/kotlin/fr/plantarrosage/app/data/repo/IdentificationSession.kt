package fr.plantarrosage.app.data.repo

import fr.plantarrosage.core.model.IdentificationCandidate
import fr.plantarrosage.core.model.IdentificationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Résultat d'identification en cours, partagé par les écrans Résultats, Fiche et Ajout.
 *
 * Les routes ne transportent qu'un index : faire transiter une liste de candidats et une photo
 * compressée par les arguments de navigation serait à la fois lourd et fragile. La session vit
 * dans le conteneur applicatif et se vide dès qu'une plante est enregistrée.
 */
class IdentificationSession {

    private val _result = MutableStateFlow<IdentificationResult?>(null)
    val result: StateFlow<IdentificationResult?> = _result.asStateFlow()

    /** Photo retenue pour la plante, déjà préparée. */
    var photoBytes: ByteArray? = null
        private set

    fun store(result: IdentificationResult, photoBytes: ByteArray?) {
        _result.value = result
        this.photoBytes = photoBytes
    }

    fun candidateAt(index: Int): IdentificationCandidate? =
        _result.value?.candidates?.getOrNull(index)

    fun clear() {
        _result.value = null
        photoBytes = null
    }
}
