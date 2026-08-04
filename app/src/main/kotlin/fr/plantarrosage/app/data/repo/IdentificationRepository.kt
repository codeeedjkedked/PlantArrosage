package fr.plantarrosage.app.data.repo

import android.net.Uri
import fr.plantarrosage.app.data.media.ImagePreparer
import fr.plantarrosage.app.data.prefs.SettingsRepository
import fr.plantarrosage.core.model.AppError
import fr.plantarrosage.core.model.IdentificationResult
import fr.plantarrosage.core.model.PlantOrgan
import fr.plantarrosage.core.plantnet.PlantNetClient
import fr.plantarrosage.core.plantnet.PlantPhoto
import fr.plantarrosage.core.util.Outcome
import kotlinx.coroutines.flow.first

/** Une photo choisie par l'utilisateur, avec l'organe qu'il a indiqué. */
data class SelectedPhoto(val uri: Uri, val organ: PlantOrgan)

/** Fait le pont entre les URI Android et le client Pl@ntNet, qui ne connaît que des octets. */
class IdentificationRepository(
    private val client: PlantNetClient,
    private val imagePreparer: ImagePreparer,
    private val settings: SettingsRepository,
) {

    suspend fun identify(photos: List<SelectedPhoto>): Outcome<IdentificationResult> {
        if (photos.isEmpty()) {
            return Outcome.Failure(AppError.BadRequest("aucune photo sélectionnée"))
        }

        val prepared = photos.mapIndexedNotNull { index, selected ->
            imagePreparer.prepare(selected.uri)?.let { bytes ->
                PlantPhoto(bytes = bytes, fileName = "photo_$index.jpg", organ = selected.organ)
            }
        }

        if (prepared.isEmpty()) {
            return Outcome.Failure(AppError.BadRequest("photos illisibles"))
        }

        return client.identify(photos = prepared, project = settings.project.first())
    }
}
