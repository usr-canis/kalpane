package dev.kalpane.ai

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import dev.kalpane.data.ShapeKind
import dev.kalpane.data.Stroke
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Languages offered for handwriting; tags are ML Kit model identifiers. */
enum class HandwritingLanguage(val label: String, val tag: String) {
    English("English", "en-US"),
    Kannada("ಕನ್ನಡ", "kn"),
}

class RecognitionException(message: String) : Exception(message)

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}

/**
 * On-device handwriting and shape recognition with ML Kit Digital Ink. Models are downloaded
 * once (a few MB each) and recognition then runs offline.
 */
object InkRecognizer {
    private const val SHAPES = "zxx-Zsym-x-shapes"
    private val clients = HashMap<String, DigitalInkRecognizer>()

    private fun model(tag: String): DigitalInkRecognitionModel {
        val id = DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag)
            ?: throw RecognitionException("Handwriting model $tag is not available.")
        return DigitalInkRecognitionModel.builder(id).build()
    }

    suspend fun isDownloaded(tag: String): Boolean = RemoteModelManager.getInstance().isModelDownloaded(model(tag)).await()

    /** Downloads the model if needed. */
    suspend fun ensure(tag: String) {
        val m = model(tag)
        val manager = RemoteModelManager.getInstance()
        if (!manager.isModelDownloaded(m).await()) {
            try {
                manager.download(m, DownloadConditions.Builder().build()).await()
            } catch (e: Exception) {
                throw RecognitionException("Could not download the recognition model. Connect to the internet and try again.")
            }
        }
    }

    private fun client(tag: String) = synchronized(clients) {
        clients.getOrPut(tag) { DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model(tag)).build()) }
    }

    /** Strokes in page units to ML Kit ink; times are synthesised in drawing order. */
    private fun ink(strokes: List<Stroke>): Ink {
        val builder = Ink.builder()
        var t = 0L
        for (s in strokes) {
            val sb = Ink.Stroke.builder()
            for (i in 0 until s.count) { sb.addPoint(Ink.Point.create(s.x(i), s.y(i), t)); t += 10 }
            builder.addStroke(sb.build())
            t += 200
        }
        return builder.build()
    }

    /** Recognises handwriting; strokes are taken top-to-bottom, left-to-right by line. */
    suspend fun text(strokes: List<Stroke>, language: HandwritingLanguage): String? {
        if (strokes.isEmpty()) return null
        ensure(language.tag)
        val result = client(language.tag).recognize(ink(strokes)).await()
        return result.candidates.firstOrNull()?.text?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Classifies a single stroke as a shape we can draw, or null. */
    suspend fun shape(stroke: Stroke): ShapeKind? {
        ensure(SHAPES)
        val label = client(SHAPES).recognize(ink(listOf(stroke))).await().candidates.firstOrNull()?.text?.uppercase() ?: return null
        return when {
            "ARROW" in label -> ShapeKind.Arrow
            "LINE" in label -> ShapeKind.Line
            "RECT" in label || "SQUARE" in label -> ShapeKind.Rectangle
            "ELLIPSE" in label || "CIRCLE" in label -> ShapeKind.Ellipse
            else -> null
        }
    }
}
