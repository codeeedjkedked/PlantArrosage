package fr.plantarrosage.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Prépare une photo avant envoi : redimensionnement, rotation EXIF appliquée dans les pixels,
 * compression JPEG.
 *
 * Une photo de smartphone moderne pèse plusieurs mégaoctets ; réduite ainsi elle tombe à
 * 200–400 Ko, ce qui évite les rejets 413 et raccourcit sensiblement l'attente sur réseau mobile.
 */
class ImagePreparer(private val context: Context) {

    companion object {
        const val MAX_EDGE_PX = 1600
        const val JPEG_QUALITY = 85
    }

    suspend fun prepare(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        // Passe de mesure. `decodeStream` rend délibérément `null` lorsque `inJustDecodeBounds`
        // est actif : il ne renseigne que outWidth/outHeight. Le succès se lit donc sur ces
        // dimensions, jamais sur la valeur retournée.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val streamOuvert = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
            true
        } ?: false

        if (!streamOuvert || bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return@withContext null
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@withContext null

        val oriented = applyExifRotation(uri, decoded)
        val scaled = scaleDown(oriented)

        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }

        // Chaque étape peut renvoyer le bitmap précédent inchangé ; on ne libère donc que les
        // instances réellement distinctes, et seulement après la compression.
        setOf(scaled, oriented, decoded).forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }

        bytes
    }

    /** Puissance de deux permettant de décoder sans dépasser inutilement la taille cible. */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var longestEdge = maxOf(width, height)
        while (longestEdge / 2 >= MAX_EDGE_PX) {
            longestEdge /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_EDGE_PX) return bitmap

        val ratio = MAX_EDGE_PX.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    /**
     * L'orientation EXIF est appliquée aux pixels : le JPEG réencodé ne conserve pas les
     * métadonnées, et Pl@ntNet identifierait sinon une photo couchée.
     */
    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
