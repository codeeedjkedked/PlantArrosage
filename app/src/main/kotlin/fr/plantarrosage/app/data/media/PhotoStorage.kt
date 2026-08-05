package fr.plantarrosage.app.data.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stockage des photos, entièrement dans l'espace privé de l'application.
 *
 * Les captures transitent par le cache (elles ne servent qu'à l'identification) ; seule la photo
 * retenue pour une plante enregistrée est conservée durablement. L'original pleine résolution
 * n'est jamais gardé.
 */
class PhotoStorage(private val context: Context) {

    private val captureDir: File
        get() = File(context.cacheDir, "captures").apply { mkdirs() }

    private val photoDir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    private val authority: String
        get() = "${context.packageName}.fileprovider"

    /** Crée un fichier vide et son URI partageable, à passer à l'intent de capture. */
    fun createCaptureTarget(): Pair<File, Uri> {
        val file = File(captureDir, "capture_${UUID.randomUUID()}.jpg")
        return file to FileProvider.getUriForFile(context, authority, file)
    }

    /** Enregistre durablement les octets préparés et rend l'URI à stocker en base. */
    suspend fun persist(bytes: ByteArray): Uri = withContext(Dispatchers.IO) {
        val file = File(photoDir, "plante_${UUID.randomUUID()}.jpg")
        file.writeBytes(bytes)
        FileProvider.getUriForFile(context, authority, file)
    }

    /** Variante pour une galerie entière, dans l'ordre reçu. */
    suspend fun persistAll(photos: List<ByteArray>): List<Uri> = photos.map { persist(it) }

    /** Supprime la photo d'une plante effacée. Une URI inconnue est ignorée sans bruit. */
    suspend fun delete(uriString: String?) = withContext(Dispatchers.IO) {
        if (uriString.isNullOrBlank()) return@withContext
        val name = Uri.parse(uriString).lastPathSegment ?: return@withContext
        File(photoDir, name).takeIf { it.exists() }?.delete()
        Unit
    }

    /** Supprime tout un lot. Les doublons sont sans effet, `delete` étant idempotent. */
    suspend fun deleteAll(uriStrings: Collection<String?>) = uriStrings.forEach { delete(it) }

    /**
     * Écrit des octets déjà nommés, tels qu'ils sortent d'une archive de sauvegarde.
     *
     * L'import doit retrouver exactement les noms de fichiers cités par le JSON, sinon les URI
     * restaurées pointeraient dans le vide. C'est la seule raison pour laquelle ce point d'entrée
     * accepte un nom au lieu d'en tirer un au sort.
     */
    suspend fun restore(fileName: String, bytes: ByteArray): Uri = withContext(Dispatchers.IO) {
        val safeName = File(fileName).name
        val file = File(photoDir, safeName)
        file.writeBytes(bytes)
        FileProvider.getUriForFile(context, authority, file)
    }

    /** Lit une photo enregistrée, pour l'ajouter à une archive. */
    suspend fun read(uriString: String?): Pair<String, ByteArray>? = withContext(Dispatchers.IO) {
        if (uriString.isNullOrBlank()) return@withContext null
        val name = Uri.parse(uriString).lastPathSegment ?: return@withContext null
        val file = File(photoDir, name).takeIf { it.exists() } ?: return@withContext null
        name to file.readBytes()
    }

    /** Vide les captures temporaires, appelé au démarrage. */
    suspend fun clearCaptures() = withContext(Dispatchers.IO) {
        captureDir.listFiles()?.forEach { it.delete() }
        Unit
    }
}
