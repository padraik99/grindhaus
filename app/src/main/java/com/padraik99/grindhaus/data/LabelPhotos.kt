package com.padraik99.grindhaus.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Label photos live in the app's private storage, shrunk to a sensible size.
 * The camera writes a full-size picture to a temporary file first; [keep] then
 * scales it down, straightens it, and moves it into the labels folder.
 */
object LabelPhotos {

    private const val MAX_EDGE_PX = 1600
    private const val JPEG_QUALITY = 85

    fun labelsDir(context: Context): File = File(context.filesDir, "labels").apply { mkdirs() }

    fun file(context: Context, name: String): File = File(labelsDir(context), name)

    /** A fresh temporary file and the content URI the camera app writes to. */
    fun newCapture(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "capture").apply { mkdirs() }
        val temp = File(dir, "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", temp)
        return temp to uri
    }

    /** Shrinks [temp] into the labels folder and returns the new file's name. */
    suspend fun keep(context: Context, temp: File): String? = withContext(Dispatchers.IO) {
        if (!temp.exists() || temp.length() == 0L) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(temp.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE_PX) sample *= 2
        val decoded = BitmapFactory.decodeFile(temp.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@withContext null
        val upright = rotateUpright(decoded, ExifInterface(temp.path))
        val scale = MAX_EDGE_PX.toFloat() / max(upright.width, upright.height)
        val sized = if (scale < 1f) {
            upright.scale((upright.width * scale).toInt(), (upright.height * scale).toInt())
        } else {
            upright
        }
        val name = "${UUID.randomUUID()}.jpg"
        file(context, name).outputStream().use { sized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        temp.delete()
        name
    }

    fun delete(context: Context, name: String?) {
        if (name != null) file(context, name).delete()
    }

    private fun rotateUpright(bitmap: Bitmap, exif: ExifInterface): Bitmap {
        val degrees = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
