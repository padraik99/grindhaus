package com.padraik99.grindhaus.data

import android.content.Context
import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.padraik99.grindhaus.R
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.GraphicsMode

/** The camera can't run here, but everything after it can: shrink, straighten, store, delete. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LabelPhotosTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Registers the app's FileProvider with its real paths file, as Android does on the phone. */
    @Before
    fun registerFileProvider() {
        val info = ProviderInfo().apply {
            authority = "${context.packageName}.files"
            grantUriPermissions = true
            exported = false
            metaData = Bundle().apply { putInt("android.support.FILE_PROVIDER_PATHS", R.xml.file_paths) }
        }
        Robolectric.buildContentProvider(FileProvider::class.java).create(info)
    }

    @Test
    fun theCameraIsHandedAContentUriInsideTheAppsCaptureFolder() {
        val (temp, uri) = LabelPhotos.newCapture(context)
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.files", uri.authority)
        assertTrue(temp.parentFile!!.path.endsWith("cache/capture"))
    }

    /** A stand-in for a camera shot: a landscape JPEG of the given size and EXIF orientation. */
    private fun fakeCameraShot(width: Int, height: Int, orientation: Int = ExifInterface.ORIENTATION_NORMAL): File {
        val (temp, _) = LabelPhotos.newCapture(context)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(temp.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return temp
    }

    private fun sizeOf(name: String): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(LabelPhotos.file(context, name).path, o)
        return o.outWidth to o.outHeight
    }

    @Test
    fun aFullSizeShotIsShrunkToAtMost1600PixelsAndTheTempFileRemoved() = runBlocking {
        val temp = fakeCameraShot(4000, 3000)
        val name = LabelPhotos.keep(context, temp)!!
        val (w, h) = sizeOf(name)
        assertEquals(1600, w)
        assertEquals(1200, h)
        assertFalse("temporary capture should be gone", temp.exists())
    }

    @Test
    fun aSidewaysShotIsTurnedUpright() = runBlocking {
        val name = LabelPhotos.keep(context, fakeCameraShot(2000, 1000, ExifInterface.ORIENTATION_ROTATE_90))!!
        val (w, h) = sizeOf(name)
        assertTrue("expected portrait, got ${w}x$h", h > w)
    }

    @Test
    fun aSmallShotIsKeptAtItsOwnSize() = runBlocking {
        val name = LabelPhotos.keep(context, fakeCameraShot(800, 600))!!
        assertEquals(800 to 600, sizeOf(name))
    }

    @Test
    fun aCancelledCaptureLeavesNothingBehind() = runBlocking {
        val (temp, _) = LabelPhotos.newCapture(context)
        assertNull(LabelPhotos.keep(context, temp))
    }

    @Test
    fun deletingRemovesThePhoto() = runBlocking {
        val name = LabelPhotos.keep(context, fakeCameraShot(800, 600))!!
        LabelPhotos.delete(context, name)
        assertFalse(LabelPhotos.file(context, name).exists())
    }
}
