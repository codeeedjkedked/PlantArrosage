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

    /** Photo de couverture, déjà préparée en pleine qualité. */
    var photoBytes: ByteArray? = null
        private set

    /**
     * Toutes les photos préparées, couverture comprise, prêtes à être enregistrées avec la plante.
     *
     * On les prépare dès l'identification et non au moment de l'enregistrement : les URI de
     * capture pointent vers le cache, que le système peut vider à tout moment entre les deux
     * écrans.
     */
    var galleryBytes: List<ByteArray> = emptyList()
        private set

    /**
     * Photos prises par l'utilisateur, conservées pour l'écran de résultats.
     * Les afficher à côté des clichés de référence est ce qui permet de trancher entre deux
     * espèces voisines.
     */
    var userPhotoUris: List<String> = emptyList()
        private set

    fun store(
        result: IdentificationResult,
        photoBytes: ByteArray?,
        galleryBytes: List<ByteArray>,
        userPhotoUris: List<String>,
    ) {
        _result.value = result
        this.photoBytes = photoBytes
        this.galleryBytes = galleryBytes
        this.userPhotoUris = userPhotoUris
    }

    fun candidateAt(index: Int): IdentificationCandidate? =
        _result.value?.candidates?.getOrNull(index)

    fun clear() {
        _result.value = null
        photoBytes = null
        galleryBytes = emptyList()
        userPhotoUris = emptyList()
    }
}
