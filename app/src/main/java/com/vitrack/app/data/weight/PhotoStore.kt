package com.vitrack.app.data.weight

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Photos kept in app storage, on the phone only. Each feature has its own folder. */
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

    /** When the photo at [uri] was taken, if the system knows; read before import drops it. */
    suspend fun takenAtMillis(uri: String): Long? = null

    companion object {
        const val MAX_SIDE_PX = 1600
    }
}

/** A [PhotoStore] in `files/<folder>`; one per feature (see DataModule). */
class AppPhotoStore(
    private val context: Context,
    private val folder: String,
) : PhotoStore {

    private val photoDir get() = File(context.filesDir, folder).apply { mkdirs() }
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

    /** The photo picker and MediaStore expose DATE_TAKEN; camera captures don't, so they use now. */
    override suspend fun takenAtMillis(uri: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(Uri.parse(uri), arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it > 0 } else null }
        }.getOrNull()
    }
}
