package com.vitrack.app.data.weight

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Progress photos kept in app storage, on the phone only. */
interface PhotoStore {
    fun file(fileName: String): File

    /** A new content Uri the camera can write a photo to. */
    fun newCaptureUri(): String

    /**
     * Copies a photo into app storage: resized so the long side is at most
     * [MAX_SIDE_PX], rotated upright, and re-encoded, which drops EXIF data
     * such as location. Returns the stored file name.
     */
    suspend fun import(uri: String): String

    suspend fun delete(fileNames: List<String>)

    companion object {
        const val MAX_SIDE_PX = 1600
    }
}

@Singleton
class AppPhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : PhotoStore {

    private val photoDir get() = File(context.filesDir, "weight_photos").apply { mkdirs() }
    private val captureDir get() = File(context.cacheDir, "camera").apply { mkdirs() }
    private val authority get() = "${context.packageName}.fileprovider"

    override fun file(fileName: String): File = File(photoDir, fileName)

    override fun newCaptureUri(): String {
        val file = File(captureDir, "capture-${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, authority, file).toString()
    }

    override suspend fun import(uri: String): String = withContext(Dispatchers.IO) {
        val source = Uri.parse(uri)
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source)) { decoder, info, _ ->
            val longSide = max(info.size.width, info.size.height)
            if (longSide > PhotoStore.MAX_SIDE_PX) {
                val scale = PhotoStore.MAX_SIDE_PX.toFloat() / longSide
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val name = "${UUID.randomUUID()}.jpg"
        file(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bitmap.recycle()
        // Camera captures are temporary; remove them once copied.
        if (source.authority == authority) {
            File(captureDir, source.lastPathSegment.orEmpty()).delete()
        }
        name
    }

    override suspend fun delete(fileNames: List<String>) = withContext(Dispatchers.IO) {
        fileNames.forEach { file(it).delete() }
    }
}
