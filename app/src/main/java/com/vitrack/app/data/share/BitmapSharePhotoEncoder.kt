package com.vitrack.app.data.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Re-encodes a stored photo for a link: long side at most [MAX_SIDE_PX], JPEG quality 80, no metadata. */
class BitmapSharePhotoEncoder @Inject constructor() : SharePhotoEncoder {
    override suspend fun encode(file: File): ByteArray? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE_PX) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@withContext null
        val longSide = max(decoded.width, decoded.height)
        val bitmap = if (longSide > MAX_SIDE_PX) {
            val scale = MAX_SIDE_PX.toFloat() / longSide
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true)
                .also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private companion object {
        const val MAX_SIDE_PX = 1280
    }
}
