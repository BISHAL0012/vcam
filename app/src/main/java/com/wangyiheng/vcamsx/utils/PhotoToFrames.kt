package com.wangyiheng.vcamsx.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import android.view.Surface
import com.wangyiheng.vcamsx.MainHook
import com.wangyiheng.vcamsx.MainHook.Companion.context
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Live Photo (v1.6): turns a still photo into slowly moving camera
 * frames, so a photo looks alive in a call instead of frozen.
 *
 * Motion is a gentle Ken Burns effect: slow zoom in/out, a drifting
 * pan and a tiny sway (about +/-1.2 degrees). It is NOT real 3D: the
 * photo never blinks, smiles or turns its head like MYCAM Reality.
 *
 * Mirrors VideoToFrames' two outputs:
 *  - byte path (Camera1, e.g. imo): NV21 frames into
 *    MainHook.data_buffer at the target preview size.
 *  - surface path (Camera2, e.g. Telegram): draws frames onto the
 *    given Surface with Canvas.
 * Runs at ~15 fps: the motion is slow, so 15 fps is smooth enough
 * and halves the CPU cost of the per-frame NV21 conversion.
 */
class PhotoToFrames : Runnable {

    @Volatile
    private var stop = false

    @Volatile
    var failed = false
        private set

    private var photoPath: Any? = null
    private var thread: Thread? = null
    private var playSurf: Surface? = null

    @Volatile
    var targetWidth: Int = 0
    @Volatile
    var targetHeight: Int = 0

    fun setTargetSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            targetWidth = width
            targetHeight = height
        }
    }

    fun set_surface(surface: Surface) {
        playSurf = surface
    }

    // Kept for API parity with VideoToFrames: Live Photo always
    // produces NV21 on the byte path.
    fun setSaveFrames(@Suppress("UNUSED_PARAMETER") format: OutputImageFormat) {
    }

    fun stopDecode() {
        stop = true
    }

    fun isRunning(): Boolean {
        return thread?.isAlive == true && !stop && !failed
    }

    fun decode(photoPath: Any) {
        this.photoPath = photoPath
        if (thread == null) {
            thread = Thread(this, "photo-frames").apply {
                start()
            }
        }
    }

    override fun run() {
        try {
            val uri = when (val p = photoPath) {
                is Uri -> p
                is String -> Uri.parse(p)
                else -> {
                    failed = true
                    return
                }
            }
            val src = loadBitmap(uri)
            if (src == null) {
                failed = true
                return
            }
            val rawTw = if (targetWidth > 0) targetWidth else 640
            val rawTh = if (targetHeight > 0) targetHeight else 480
            // NV21 chroma is subsampled 2x2, so both dimensions must be
            // even (camera preview sizes always are; this is just a guard).
            val tw = (rawTw and -2).coerceAtLeast(2)
            val th = (rawTh and -2).coerceAtLeast(2)
            val frame = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frame)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val pixels = IntArray(tw * th)
            val startMs = System.currentTimeMillis()
            while (!stop) {
                val frameStart = System.currentTimeMillis()
                val t = (frameStart - startMs) / 1000f
                drawFrame(canvas, paint, src, t, tw, th)
                val surface = playSurf
                if (surface != null) {
                    if (!drawToSurface(surface, frame)) {
                        // Surface is gone: nothing more to draw to.
                        break
                    }
                } else {
                    frame.getPixels(pixels, 0, tw, 0, 0, tw, th)
                    MainHook.data_buffer = nv21FromPixels(pixels, tw, th)
                }
                val spent = System.currentTimeMillis() - frameStart
                val sleepMs = 66L - spent
                if (sleepMs > 0) {
                    try {
                        Thread.sleep(sleepMs)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        } catch (t: Throwable) {
            Log.d("vcamsx", "PhotoToFrames failed: $t")
            failed = true
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            val resolver = context?.contentResolver ?: return null
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
            val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            var sample = 1
            while (maxDim / sample > 1024) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            Log.d("vcamsx", "PhotoToFrames load failed: $e")
            null
        }
    }

    /**
     * Draw the photo into [canvas] (target size tw x th) with the
     * slow Ken Burns transform for time [t] seconds: zoom breathes
     * 1.06x-1.18x over 16s, pan drifts a few percent on a slow
     * Lissajous, sway tilts +/-1.2 degrees over 12s. Minimum zoom
     * stays above 1.0 so the rotation never exposes the corners.
     */
    private fun drawFrame(canvas: Canvas, paint: Paint, src: Bitmap, t: Float, tw: Int, th: Int) {
        val zoom = 1.12f + 0.06f * cos(2f * PI.toFloat() * t / 16f)
        val panX = 0.03f * sin(2f * PI.toFloat() * t / 19f)
        val panY = 0.02f * cos(2f * PI.toFloat() * t / 23f)
        val rot = 1.2f * sin(2f * PI.toFloat() * t / 12f)
        val srcAspect = src.width.toFloat() / src.height
        val dstAspect = tw.toFloat() / th
        var baseScale = if (srcAspect > dstAspect) {
            th.toFloat() / src.height
        } else {
            tw.toFloat() / src.width
        }
        baseScale *= zoom
        val m = Matrix()
        m.postTranslate(-src.width / 2f, -src.height / 2f)
        m.postScale(baseScale, baseScale)
        m.postRotate(rot)
        m.postTranslate(tw / 2f + panX * tw, th / 2f + panY * th)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(src, m, paint)
    }

    private fun drawToSurface(surface: Surface, frame: Bitmap): Boolean {
        return try {
            val c = surface.lockCanvas(null) ?: return false
            try {
                c.drawColor(Color.BLACK)
                // Frame is tw x th; the surface may be larger, so scale
                // it to fill the canvas (same aspect: the camera preview).
                c.drawBitmap(frame, null, android.graphics.Rect(0, 0, c.width, c.height), null)
            } finally {
                surface.unlockCanvasAndPost(c)
            }
            true
        } catch (e: Exception) {
            Log.d("vcamsx", "PhotoToFrames surface draw failed: $e")
            false
        }
    }

    /**
     * Fast integer RGB -> NV21 (BT.601 studio swing), matching the
     * YUV range the camera byte path already produces. Y per pixel,
     * one VU pair per 2x2 block.
     */
    private fun nv21FromPixels(pixels: IntArray, w: Int, h: Int): ByteArray {
        val out = ByteArray(w * h * 3 / 2)
        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = pixels[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                out[i] = (((66 * r + 129 * g + 25 * b) shr 8) + 16).toByte()
                i++
            }
        }
        var o = w * h
        for (y in 0 until h step 2) {
            for (x in 0 until w step 2) {
                val c = pixels[y * w + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                out[o++] = (((112 * r - 94 * g - 18 * b) shr 8) + 128).toByte()
                out[o++] = (((-38 * r - 74 * g + 112 * b) shr 8) + 128).toByte()
            }
        }
        return out
    }
}
