package com.oxlo.gallary.data.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.oxlo.gallary.data.MediaRepository
import com.oxlo.gallary.data.db.FaceEntity
import com.oxlo.gallary.data.db.GallaryDatabase
import com.oxlo.gallary.data.db.MediaItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * On-device face detection with ML Kit. Detected faces are cropped, cached and
 * stored so the People screen can tag them with names.
 */
class FaceAnalyzer(
    private val context: Context,
    private val db: GallaryDatabase,
    private val repository: MediaRepository
) {

    private val facesDir = File(context.filesDir, "faces").apply { mkdirs() }

    private val _progress = MutableStateFlow<String?>(null)
    val progress: StateFlow<String?> = _progress.asStateFlow()

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.06f)
                .build()
        )
    }

    /** Detects faces in one item. Returns the number of faces found. */
    suspend fun analyze(item: MediaItemEntity): Int = withContext(Dispatchers.Default) {
        if (item.isVideo) return@withContext 0
        val bitmap = loadUprightBitmap(item) ?: return@withContext 0
        val faces = try {
            detector.process(InputImage.fromBitmap(bitmap, 0)).awaitResult()
        } catch (error: Exception) {
            bitmap.recycle()
            return@withContext 0
        }

        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        db.peopleDao().deleteFacesForMedia(item.id)
        oldCropsFor(item.id).forEach { it.delete() }

        val entities = faces.mapIndexedNotNull { index, face ->
            val box = face.boundingBox
            val crop = cropFace(bitmap, box) ?: return@mapIndexedNotNull null
            val cropFile = File(facesDir, "face_${item.id}_$index.jpg")
            runCatching {
                cropFile.outputStream().use { crop.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            }
            val descriptor = descriptorOf(crop)
            crop.recycle()

            FaceEntity(
                mediaId = item.id,
                personId = null,
                left = (box.left / width).coerceIn(0f, 1f),
                top = (box.top / height).coerceIn(0f, 1f),
                right = (box.right / width).coerceIn(0f, 1f),
                bottom = (box.bottom / height).coerceIn(0f, 1f),
                smileProbability = face.smilingProbability ?: 0f,
                eyesOpenProbability = face.leftEyeOpenProbability ?: 0f,
                cropPath = cropFile.absolutePath,
                descriptor = descriptor
            )
        }

        if (entities.isNotEmpty()) {
            db.peopleDao().insertFaces(entities)
        }
        db.mediaDao().setFaceCount(item.id, entities.size)
        bitmap.recycle()
        entities.size
    }

    /** Analyses up to [limit] photos that have not been scanned yet. */
    suspend fun scanMissing(limit: Int = 300, onProgress: (Int, Int) -> Unit = { _, _ -> }): Int =
        withContext(Dispatchers.IO) {
            val pending = db.mediaDao().itemsWithoutFaces(limit).filter { !it.isVideo }
            var done = 0
            _progress.value = "Scanning ${pending.size} photos…"
            pending.forEach { item ->
                runCatching { analyze(item) }
                done++
                _progress.value = "Detecting faces · $done/${pending.size}"
                onProgress(done, pending.size)
            }
            _progress.value = null
            done
        }

    fun close() {
        runCatching { detector.close() }
    }

    private fun oldCropsFor(mediaId: Long): List<File> =
        facesDir.listFiles { file -> file.name.startsWith("face_${mediaId}_") }?.toList().orEmpty()

    private suspend fun loadUprightBitmap(item: MediaItemEntity): Bitmap? {
        val orientation = readOrientation(item)
        val bitmap = decodeSampled(item) ?: return null
        if (orientation == ExifInterface.ORIENTATION_NORMAL) return bitmap
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { if (it != bitmap) bitmap.recycle() }
        }.getOrDefault(bitmap)
    }

    private suspend fun decodeSampled(item: MediaItemEntity): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(item)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0) return null

        var sample = 1
        while (options.outWidth / sample > MAX_DIMENSION || options.outHeight / sample > MAX_DIMENSION) {
            sample *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return openStream(item)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
    }

    private suspend fun readOrientation(item: MediaItemEntity): Int {
        return runCatching {
            openStream(item)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    private suspend fun openStream(item: MediaItemEntity): InputStream? = runCatching {
        if (item.isRemote) {
            val file = repository.localFileFor(item)
            file?.inputStream()
        } else {
            context.contentResolver.openInputStream(Uri.parse(item.uri))
        }
    }.getOrNull()

    private fun cropFace(bitmap: Bitmap, box: android.graphics.Rect): Bitmap? {
        val padding = (box.width() * 0.25f).toInt()
        val left = (box.left - padding).coerceAtLeast(0)
        val top = (box.top - padding).coerceAtLeast(0)
        val right = (box.right + padding).coerceAtMost(bitmap.width)
        val bottom = (box.bottom + padding).coerceAtMost(bitmap.height)
        if (right <= left || bottom <= top) return null
        return runCatching {
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        }.getOrNull()
    }

    /** 8x8 grayscale fingerprint, cheap but good enough to suggest groups. */
    private fun descriptorOf(crop: Bitmap): String {
        val small = Bitmap.createScaledBitmap(crop, 8, 8, true)
        val pixels = IntArray(64)
        small.getPixels(pixels, 0, 8, 0, 0, 8, 8)
        if (small != crop) small.recycle()
        return pixels.joinToString(",") {
            val r = (it shr 16) and 0xFF
            val g = (it shr 8) and 0xFF
            val b = it and 0xFF
            ((r * 299 + g * 587 + b * 114) / 1000).toString()
        }
    }

    companion object {
        private const val MAX_DIMENSION = 1600

        /** Mean absolute difference between two descriptors, 0..255. */
        fun distance(a: String, b: String): Int {
            val left = a.split(',').mapNotNull { it.toIntOrNull() }
            val right = b.split(',').mapNotNull { it.toIntOrNull() }
            if (left.size != right.size || left.isEmpty()) return Int.MAX_VALUE
            return left.indices.sumOf { abs(left[it] - right[it]) } / left.size
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
}

/** Groups unnamed faces that look alike so they can be tagged in one go. */
object FaceClusterer {

    data class Cluster(val faces: List<FaceEntity>, val coverPath: String?)

    fun cluster(faces: List<FaceEntity>, threshold: Int = 26): List<Cluster> {
        val clusters = mutableListOf<MutableList<FaceEntity>>()

        faces.forEach { face ->
            val descriptor = face.descriptor
            if (descriptor.isNullOrBlank()) return@forEach
            val match = clusters.firstOrNull { cluster ->
                val reference = cluster.first().descriptor
                reference != null && FaceAnalyzer.distance(reference, descriptor) <= threshold
            }
            if (match != null) match += face else clusters += mutableListOf(face)
        }

        return clusters
            .map { Cluster(it, it.firstOrNull { face -> face.cropPath != null }?.cropPath) }
            .sortedByDescending { it.faces.size }
    }
}
