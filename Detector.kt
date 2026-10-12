package id.sawitvision.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Satu kotak deteksi. Koordinat dinormalisasi 0..1 terhadap foto asli. */
data class Detection(
    val cls: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val conf: Int,
)

object Cls {
    const val JANJANG = "janjang"
    const val BRONDOL = "brondol"
    const val KOSONG = "janjang_kosong"
    /** Urutan indeks kelas HARUS sama dengan saat model dilatih (lihat offscreen.js ekstensi). */
    val NAMES = listOf(JANJANG, BRONDOL, KOSONG)
    fun label(c: String) = when (c) {
        JANJANG -> "Janjang"
        BRONDOL -> "Brondol"
        KOSONG -> "Janjang kosong"
        else -> c
    }
    fun short(c: String) = when (c) {
        JANJANG -> "JJG"
        BRONDOL -> "BRD"
        KOSONG -> "JANGKOS"
        else -> c
    }
}

/**
 * Pembungkus model sawit_detector.tflite (YOLO26n, end-to-end / tanpa NMS):
 *  - input  : 1x3x640x640 float32 (RGB 0..1, letterbox abu-abu 114)
 *  - output : 300 baris x [x1, y1, x2, y2, skor, kelas] dalam piksel 640x640
 * Logika pra/pasca-proses disalin dari offscreen.js ekstensi Chrome.
 */
class Detector(context: Context) : AutoCloseable {
    private val interpreter: Interpreter
    private val nchw: Boolean
    private val size: Int
    private val outShape: IntArray
    private val outFloats: Int

    init {
        val fd = context.assets.openFd(MODEL_ASSET)
        val mapped = fd.use {
            FileInputStream(it.fileDescriptor).channel.map(
                FileChannel.MapMode.READ_ONLY, it.startOffset, it.declaredLength,
            )
        }
        interpreter = Interpreter(mapped, Interpreter.Options().setNumThreads(4))
        val inShape = interpreter.getInputTensor(0).shape()
        nchw = inShape.size == 4 && inShape[1] == 3
        size = if (nchw) inShape[2] else inShape[1]
        outShape = interpreter.getOutputTensor(0).shape()
        outFloats = outShape.fold(1) { a, b -> a * b }
    }

    @Synchronized
    fun detect(bmp: Bitmap, confThreshold: Float): List<Detection> {
        val ow = bmp.width
        val oh = bmp.height
        val scale = min(size / ow.toFloat(), size / oh.toFloat())
        val nw = (ow * scale).roundToInt()
        val nh = (oh * scale).roundToInt()
        val padL = (size - nw) / 2
        val padT = (size - nh) / 2

        val canvasBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBmp)
        canvas.drawColor(Color.rgb(114, 114, 114))
        canvas.drawBitmap(bmp, null, Rect(padL, padT, padL + nw, padT + nh), Paint(Paint.FILTER_BITMAP_FLAG))
        val px = IntArray(size * size)
        canvasBmp.getPixels(px, 0, size, 0, 0, size, size)
        canvasBmp.recycle()

        val input = ByteBuffer.allocateDirect(3 * size * size * 4).order(ByteOrder.nativeOrder())
        val fb = input.asFloatBuffer()
        val plane = size * size
        for (i in 0 until plane) {
            val p = px[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            if (nchw) {
                fb.put(i, r); fb.put(plane + i, g); fb.put(2 * plane + i, b)
            } else {
                fb.put(i * 3, r); fb.put(i * 3 + 1, g); fb.put(i * 3 + 2, b)
            }
        }

        val output = ByteBuffer.allocateDirect(outFloats * 4).order(ByteOrder.nativeOrder())
        interpreter.run(input, output)
        output.rewind()
        val out = FloatArray(outFloats)
        output.asFloatBuffer().get(out)

        // Bentuk umum [1, 300, 6]; dukung juga [1, 6, 300] (transpos) kalau model berbeda.
        val a = if (outShape.size >= 2) outShape[outShape.size - 2] else 300
        val b = if (outShape.isNotEmpty()) outShape[outShape.size - 1] else 6
        val transposed = (b != 6 && a == 6)
        val rows = if (transposed) b else a
        fun v(row: Int, k: Int) = if (transposed) out[k * rows + row] else out[row * 6 + k]

        val boxes = ArrayList<Detection>()
        for (i in 0 until rows) {
            val conf = v(i, 4)
            if (conf < confThreshold) continue
            val cls = Cls.NAMES.getOrNull(v(i, 5).roundToInt()) ?: continue
            val x1 = ((v(i, 0) - padL) / scale).coerceIn(0f, ow.toFloat())
            val y1 = ((v(i, 1) - padT) / scale).coerceIn(0f, oh.toFloat())
            val x2 = ((v(i, 2) - padL) / scale).coerceIn(0f, ow.toFloat())
            val y2 = ((v(i, 3) - padT) / scale).coerceIn(0f, oh.toFloat())
            val w = x2 - x1
            val h = y2 - y1
            if (w <= 0f || h <= 0f) continue
            boxes += Detection(cls, x1 / ow, y1 / oh, w / ow, h / oh, (conf * 100).roundToInt())
        }
        return dedup(boxes)
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        private const val MODEL_ASSET = "sawit_detector.tflite"
        /** Ambang keyakinan bawaan 10%, sama dengan ekstensi. */
        const val DEFAULT_CONF = 0.10f

        private fun iou(a: Detection, b: Detection): Float {
            val ix = max(0f, min(a.x + a.w, b.x + b.w) - max(a.x, b.x))
            val iy = max(0f, min(a.y + a.h, b.y + b.h) - max(a.y, b.y))
            val inter = ix * iy
            val union = a.w * a.h + b.w * b.h - inter
            return if (union > 0f) inter / union else 0f
        }

        /** Buang kotak sekelas yang IoU-nya > 0.45 (sama dengan dedupOverlappingBoxes di ekstensi). */
        fun dedup(boxes: List<Detection>, thr: Float = 0.45f): List<Detection> {
            val kept = ArrayList<Detection>()
            for (c in boxes.sortedByDescending { it.conf }) {
                if (kept.none { it.cls == c.cls && iou(it, c) > thr }) kept += c
            }
            return kept
        }
    }
}
