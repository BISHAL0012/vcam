package com.wangyiheng.vcamsx.utils

import android.content.ContentValues
import android.content.ContentValues.TAG
import android.graphics.*
import android.media.*
import android.net.Uri
import android.util.Log
import android.view.Surface
import com.wangyiheng.vcamsx.MainHook
import com.wangyiheng.vcamsx.MainHook.Companion.context
import de.robv.android.xposed.XposedBridge
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

class VideoToFrames : Runnable {

    private var stopDecode = false

    private var outputImageFormat: OutputImageFormat? = null
    private var videoFilePath: Any? = null
    private var childThread: Thread? = null
    private var throwable: Throwable? = null // 定义 throwable 变量
    private val decodeColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
    private var play_surf: Surface? = null

    // Camera1 preview size requested by the target app (e.g. imo calls:
    // 640x480). 0 means "unknown": the old behaviour (return the decoded
    // frame at its native size) is kept. Set only from MainHook's Camera1
    // onPreviewFrame hook, never from the Camera2/Surface path, so apps
    // that render to a Surface (e.g. the working Telegram path) are
    // completely untouched.
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
    private val DEFAULT_TIMEOUT_US: Long = 10000
    private val callback: Callback? = null
    private val COLOR_FormatI420 = 1
    private val COLOR_FormatNV21 = 2
    private val VERBOSE = false
    fun stopDecode() {
        stopDecode = true
    }

    interface Callback {
        fun onFinishDecode()
        fun onDecodeFrame(index: Int)
    }

    @Throws(IOException::class)
    fun setSaveFrames(imageFormat: OutputImageFormat) {
        outputImageFormat = imageFormat
    }

    fun set_surface(player_surface:Surface){
        if(player_surface != null){
            play_surf = player_surface
        }
    }

    fun decode(videoFilePath: Any) {
        this.videoFilePath = videoFilePath
        if (childThread == null) {
            childThread = Thread(this, "decode").apply {
                start()
            }
            throwable?.let { throw it }
        }
    }

    override fun run() {
        try {
            Log.d("vcamsxtoast","------Start decoding------")
            videoFilePath?.let { videoDecode(it) }
        } catch (t: Throwable) {
            throwable = t
        }
    }

    private fun videoDecode(videoPath: Any) {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null

        try {
            extractor = MediaExtractor().apply {
                when (videoPath) {
                    is String -> setDataSource(videoPath) // 当参数是 String 时
                    is Uri -> context?.let { setDataSource(it, videoPath, null) } // 当参数是 Uri 时
                    else -> throw IllegalArgumentException("Unsupported video path type")
                }
            }
            val trackIndex = selectTrack(extractor)
            if (trackIndex < 0) {
                XposedBridge.log("No video track found in $videoFilePath")
            }
            extractor.selectTrack(trackIndex)
            val mediaFormat = extractor.getTrackFormat(trackIndex)
            val mime = mediaFormat.getString(MediaFormat.KEY_MIME)
            decoder = MediaCodec.createDecoderByType(mime!!)
            showSupportedColorFormat(decoder.codecInfo.getCapabilitiesForType(mime))
            if (isColorFormatSupported(decodeColorFormat, decoder.codecInfo.getCapabilitiesForType(mime))) {
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, decodeColorFormat)
                XposedBridge.log("set decode color format to type $decodeColorFormat")
            } else {
                Log.i(ContentValues.TAG, "unable to set decode color format, color format type $decodeColorFormat not supported")
                XposedBridge.log("unable to set decode color format, color format type $decodeColorFormat not supported")
            }
            decodeFramesToImage(decoder, extractor, mediaFormat)
            decoder.stop()
            while (!stopDecode) {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                decodeFramesToImage(decoder, extractor, mediaFormat)
                decoder.stop()
            }
        } catch (e: Exception) {
            // Handle exceptions
        } finally {
            if(decoder != null) {
                decoder.stop()
                decoder.release()
                decoder = null
            }
            if(extractor != null) {
                extractor.release()
                extractor = null
            }
        }
    }
    private fun selectTrack(extractor: MediaExtractor): Int {
        val numTracks = extractor.trackCount
        for (i in 0 until numTracks) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime!!.startsWith("video/")) {
                return i
            }
        }
        return -1
    }

    private fun showSupportedColorFormat(caps: MediaCodecInfo.CodecCapabilities) {
        for (c in caps.colorFormats) {
            print("$c\t")
        }
        println()
    }

    fun isColorFormatSupported(colorFormat: Int, caps: MediaCodecInfo.CodecCapabilities): Boolean {
        return caps.colorFormats.any { it == colorFormat }
    }

    private fun decodeFramesToImage(decoder: MediaCodec, extractor: MediaExtractor, mediaFormat: MediaFormat) {
        var isFirst = false
        var startWhen: Long = 0
        // Presentation time of the first frame, so pacing is anchored to
        // (pts - firstPts) rather than assuming the video starts at 0.
        var startPtsMs: Long = 0
        val info = MediaCodec.BufferInfo()
        decoder.configure(mediaFormat, play_surf, null, 0)
        var sawInputEOS = false
        var sawOutputEOS = false
        decoder.start()
        var outputFrameCount = 0

        while (!sawOutputEOS && !stopDecode) {
            if (!sawInputEOS) {
                val inputBufferId = decoder.dequeueInputBuffer(DEFAULT_TIMEOUT_US)
                if (inputBufferId >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferId)
                    val sampleSize = extractor.readSampleData(inputBuffer!!, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }
            }

            val outputBufferId = decoder.dequeueOutputBuffer(info, DEFAULT_TIMEOUT_US)
            if (outputBufferId >= 0) {
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawOutputEOS = true
                }
                val doRender = info.size != 0
                if (doRender) {
                    outputFrameCount++
                    callback?.onDecodeFrame(outputFrameCount)

                    if (!isFirst) {
                        startWhen = System.currentTimeMillis()
                        startPtsMs = info.presentationTimeUs / 1000
                        isFirst = true
                    }
                    if (play_surf == null) {
                        // A/V SYNC FIX (imo byte path, run #40): the old code
                        // played EVERY decoded frame in sequence and only
                        // slept when early — it never dropped a late frame.
                        // On a mid-range CPU the decode+rescale cost per
                        // frame can exceed one frame interval, so each frame
                        // landed a little later than its presentation time
                        // and the lag grew without bound: video ran slow
                        // while the separate audio path ran at real time.
                        // Fix: if a frame is already >100 ms behind
                        // wall-clock time (its presentation time, anchored
                        // to the first frame), drop it BEFORE the expensive
                        // rescale — the peer keeps the previous frame via
                        // MainHook.data_buffer and the next frame is closer
                        // to on-time, so lag stays bounded. This frame-drop
                        // is the ONLY sync mechanism here: no audio re-seek
                        // of any kind.
                        val framePtsMs = info.presentationTimeUs / 1000
                        val lagMs = (System.currentTimeMillis() - startWhen) -
                            (framePtsMs - startPtsMs)
                        if (lagMs > 100) {
                            decoder.releaseOutputBuffer(outputBufferId, true)
                            continue
                        }
                        val image = decoder.getOutputImage(outputBufferId)
                        if (outputImageFormat != null && image != null) {
                            MainHook.data_buffer = getDataFromImage(image)
                        }
                        image?.close()
                    }

                    val sleepTime = (info.presentationTimeUs / 1000 - startPtsMs) - (System.currentTimeMillis() - startWhen)
                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(sleepTime)
                        } catch (e: InterruptedException) {
                            XposedBridge.log("" + e.toString())
                            XposedBridge.log("Thread sleep error")
                        }
                    }
                    decoder.releaseOutputBuffer(outputBufferId, true)
                }
            }
        }
        callback?.onFinishDecode()
    }

    fun logImageFormat(image: Image) {
        val format = image.format
        val formatString = when (format) {
            ImageFormat.YUV_420_888 -> "YUV_420_888"
            ImageFormat.JPEG -> "JPEG"
            ImageFormat.RAW_SENSOR -> "RAW_SENSOR"
            ImageFormat.NV21 -> "NV21"
            ImageFormat.YV12 -> "YV12"
            ImageFormat.RAW_PRIVATE -> "RAW_PRIVATE"
            ImageFormat.RAW10 -> "RAW10"
            ImageFormat.RAW12 -> "RAW12"
            ImageFormat.DEPTH_JPEG -> "DEPTH_JPEG"
            ImageFormat.DEPTH16 -> "DEPTH16"
            ImageFormat.DEPTH_POINT_CLOUD -> "DEPTH_POINT_CLOUD"
            // 添加更多格式根据需要
            else -> "Unknown format: $format"
        }
        Log.d("vcamsx", "Image format is $formatString")
    }

    fun imageToBitmap(image: Image): Bitmap {
        Log.d("vcamsx",image.format.toString())
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // YUV_420_888数据转NV21
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 75, out)

        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

//    fun bitmapToByteArray(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
//        val stream = ByteArrayOutputStream()
//        bitmap.compress(format, quality, stream)
//        return stream.toByteArray()
//    }

    fun bitmapToYUV(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val intArray = IntArray(width * height)
        bitmap.getPixels(intArray, 0, width, 0, 0, width, height)

        val yuvArray = ByteArray(width * height * 3)

        var index = 0
        intArray.forEach { color ->
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            // Apply the RGB to YUV formula
            val y = (0.257 * r) + (0.504 * g) + (0.098 * b) + 16
            val u = -(0.148 * r) - (0.291 * g) + (0.439 * b) + 128
            val v = (0.439 * r) - (0.368 * g) - (0.071 * b) + 128

            // Assuming the YUV format is YUV444, store each Y, U, and V value sequentially
            yuvArray[index++] = y.toInt().toByte()
            yuvArray[index++] = u.toInt().toByte()
            yuvArray[index++] = v.toInt().toByte()
        }

        return yuvArray
    }
//    private fun getDataFromImage(image: Image, colorFormat: Int): ByteArray {
//        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
//            throw IllegalArgumentException("only support COLOR_FormatI420 and COLOR_FormatNV21")
//        }
//
//        logImageFormat(image)
//        if (!isImageFormatSupported(image)) {
//            throw RuntimeException("can't convert Image to byte array, format ${image.format}")
//        }
//
//
//        val crop = image.cropRect
//        val format = image.format
//        val width = crop.width()
//        val height = crop.height()
//        val planes = image.planes
//        val data = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
//        val rowData = ByteArray(planes[0].rowStride)
//
//        var channelOffset = 0
//        var outputStride = 1
//        for (i in planes.indices) {
//            when (i) {
//                0 -> {
//                    channelOffset = 0
//                    outputStride = 1
//                }
//                1 -> {
//                    channelOffset = if (colorFormat == COLOR_FormatI420) width * height else width * height + 1
//                    outputStride = 2
//                }
//                2 -> {
//                    channelOffset = if (colorFormat == COLOR_FormatI420) (width * height * 1.25).toInt() else width * height
//                    outputStride = 2
//                }
//            }
//            val buffer = planes[i].buffer
//            val rowStride = planes[i].rowStride
//            val pixelStride = planes[i].pixelStride
//
//            val shift = if (i == 0) 0 else 1
//            val w = width shr shift
//            val h = height shr shift
//            buffer.position(rowStride * (crop.top shr shift) + pixelStride * (crop.left shr shift))
//            for (row in 0 until h) {
//                val length: Int
//                if (pixelStride == 1 && outputStride == 1) {
//                    length = w
//                    buffer.get(data, channelOffset, length)
//                    channelOffset += length
//                } else {
//                    length = (w - 1) * pixelStride + 1
//                    buffer.get(rowData, 0, length)
//                    for (col in 0 until w) {
//                        data[channelOffset] = rowData[col * pixelStride]
//                        channelOffset += outputStride
//                    }
//                }
//                if (row < h - 1) {
//                    buffer.position(buffer.position() + rowStride - length)
//                }
//            }
//        }
//        return data
//    }

    private fun getDataFromImage(image: Image): ByteArray {
        // Frame-format fix for Camera1 call paths (imo): the old code
        // returned the decoded frame at its NATIVE size and MainHook copied
        // min(data, appBuffer) bytes into the app's preview buffer. When
        // the video was not exactly the camera preview size (e.g. a
        // downloaded 720p/1080p video vs imo's 640x480), every row was
        // read at the wrong width and the chroma plane was read from the
        // wrong offset, giving vertical stripes with green/purple tint
        // and only a faint image underneath. Rescaling here, for ANY
        // source size/codec (the decoder already normalised the codec to
        // YUV_420_888), makes the buffer exactly the preview size.
        // No transform is applied: Vcam has no floating panel, so
        // mirror/rotate/zoom are identity (defaults in scaledNv21).
        val tw = targetWidth
        val th = targetHeight
        if (tw > 0 && th > 0 && tw % 2 == 0 && th % 2 == 0 &&
            image.format == ImageFormat.YUV_420_888 &&
            (image.width != tw || image.height != th)) {
            val scaled = scaledNv21(image, tw, th)
            if (scaled != null) return scaled
            // Fall through to the old path if scaling could not run.
        }

        logImageFormat(image)
        if (!isImageFormatSupported(image)) {
            throw RuntimeException("can't convert Image to byte array, format ${image.format}")
        }

        val crop = image.cropRect
        val width = crop.width()
        val height = crop.height()
        val planes = image.planes
        val pixelFormatBits = ImageFormat.getBitsPerPixel(image.format)
        val data = ByteArray(width * height * pixelFormatBits / 8)
        val rowData = ByteArray(planes[0].rowStride)

        fun copyPlaneData(planeIndex: Int, buffer: ByteBuffer, rowStride: Int, pixelStride: Int, width: Int, height: Int, channelOffset: Int, outputStride: Int) {
            var outputOffset = channelOffset
            buffer.position(rowStride * (crop.top / 2) + pixelStride * (crop.left / 2))
            for (row in 0 until height) {
                val length = if (pixelStride == 1 && outputStride == 1) {
                    width
                } else {
                    (width - 1) * pixelStride + 1
                }
                if (length == rowStride && outputStride == 1) {
                    buffer.get(data, outputOffset, length)
                    outputOffset += length
                } else {
                    buffer.get(rowData, 0, length)
                    for (col in 0 until width) {
                        data[outputOffset] = rowData[col * pixelStride]
                        outputOffset += outputStride
                    }
                }
                if (row < height - 1) {
                    buffer.position(buffer.position() + rowStride - length)
                }
            }
        }

        var channelOffset = 0
        val uvHeight = height / 2
        val uvWidth = width / 2

        // Y Plane
        copyPlaneData(0, planes[0].buffer, planes[0].rowStride, planes[0].pixelStride, width, height, channelOffset, 1)
        channelOffset += width * height


        copyPlaneData(1, planes[2].buffer, planes[2].rowStride, planes[2].pixelStride, uvWidth, uvHeight, channelOffset, 2)
        copyPlaneData(2, planes[1].buffer, planes[1].rowStride, planes[1].pixelStride, uvWidth, uvHeight, channelOffset + 1, 2)


        return data
    }



    /**
     * Convert a YUV_420_888 [Image] to packed NV21 at exactly
     * ([targetW] x [targetH]), centre-cropping to the target aspect first
     * so no stretching occurs. Works for any source size: the source
     * codec has already been decoded to YUV_420_888 by MediaCodec, so
     * downloaded, recorded, portrait or landscape videos all go through
     * the same code. Rotation metadata is NOT applied here (it never was
     * in this byte path); a rotated video is centre-cropped, not rotated.
     * Returns null on any unexpected plane layout so the caller can fall
     * back to the old conversion instead of crashing the call.
     */
    private fun scaledNv21(
        image: Image, targetW: Int, targetH: Int,
        mirror: Boolean = false, rotateSteps: Int = 0, zoom: Float = 1.0f
    ): ByteArray? {
        return try {
            val crop = image.cropRect
            val srcW = crop.width()
            val srcH = crop.height()
            if (srcW <= 0 || srcH <= 0 || image.planes.size < 3) return null
            val rot = ((rotateSteps % 4) + 4) % 4
            // Centre-crop source to target aspect. For odd rotations the
            // image is turned 90°, so the crop aspect swaps (th/tw);
            // the output size itself never changes, so the app's preview
            // buffer size stays exact.
            val tgtAspect = if (rot % 2 == 1) {
                targetH.toFloat() / targetW.toFloat()
            } else {
                targetW.toFloat() / targetH.toFloat()
            }
            val srcAspect = srcW.toFloat() / srcH.toFloat()
            var cropW: Int
            var cropH: Int
            var offX: Int
            var offY: Int
            if (srcAspect > tgtAspect) {
                cropH = srcH
                cropW = (srcH * tgtAspect).toInt().coerceAtLeast(2)
                offX = (srcW - cropW) / 2
                offY = 0
            } else {
                cropW = srcW
                cropH = (srcW / tgtAspect).toInt().coerceAtLeast(2)
                offX = 0
                offY = (srcH - cropH) / 2
            }
            // Zoom: sample a smaller central area, then resample to target.
            if (zoom > 1.001f) {
                val zw = (cropW / zoom).toInt().coerceAtLeast(2)
                val zh = (cropH / zoom).toInt().coerceAtLeast(2)
                offX += (cropW - zw) / 2
                offY += (cropH - zh) / 2
                cropW = zw
                cropH = zh
            }
            // Map an output-normalised coordinate through Mirror then
            // Rotate. Four taps cycle back to identity, as on the
            // Surface path; direction (CW/CCW) does not matter for that.
            // A/V SYNC COST FIX: the old code did this via a function
            // returning Pair<Float, Float> — one heap object allocated
            // PER PIXEL (~384k allocations per 640x480 frame), a major
            // cost on a mid-range CPU. The loops below now compute the
            // mapped coordinates inline into plain Float locals, with
            // exactly the same mapping, and no per-pixel allocation.
            // (mu, mv) for a given (u, v): uu = mirror ? 1-u : u, vv = v,
            // then rot 0: (uu, vv); 1: (vv, 1-uu); 2: (1-uu, 1-vv);
            // 3: (1-vv, uu).
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]
            val yBuf = yPlane.buffer.duplicate()
            val uBuf = uPlane.buffer.duplicate()
            val vBuf = vPlane.buffer.duplicate()
            val out = ByteArray(targetW * targetH * 3 / 2)
            // Y plane: nearest-neighbour sample, absolute reads so plane
            // position/stride handling cannot drift between rows.
            for (ty in 0 until targetH) {
                val vNorm = (ty + 0.5f) / targetH
                for (tx in 0 until targetW) {
                    val uNorm = (tx + 0.5f) / targetW
                    var mu = if (mirror) 1f - uNorm else uNorm
                    var mv = vNorm
                    when (rot) {
                        1 -> { val t = mu; mu = mv; mv = 1f - t }
                        2 -> { mu = 1f - mu; mv = 1f - mv }
                        3 -> { val t = mu; mu = 1f - mv; mv = t }
                    }
                    val sx = crop.left + offX + (mu * cropW).toInt().coerceIn(0, srcW - 1)
                    val sy = crop.top + offY + (mv * cropH).toInt().coerceIn(0, srcH - 1)
                    val idx = yPlane.rowStride * sy + yPlane.pixelStride * sx
                    if (idx < yBuf.capacity()) out[ty * targetW + tx] = yBuf.get(idx)
                }
            }
            // Chroma: NV21 interleaves V then U, subsampled 2x2.
            val chromaBase = targetW * targetH
            val tgtChromaW = targetW / 2
            val tgtChromaH = targetH / 2
            for (cy in 0 until tgtChromaH) {
                val vNorm = (cy + 0.5f) / tgtChromaH
                for (cx in 0 until tgtChromaW) {
                    val uNorm = (cx + 0.5f) / tgtChromaW
                    var mu = if (mirror) 1f - uNorm else uNorm
                    var mv = vNorm
                    when (rot) {
                        1 -> { val t = mu; mu = mv; mv = 1f - t }
                        2 -> { mu = 1f - mu; mv = 1f - mv }
                        3 -> { val t = mu; mu = 1f - mv; mv = t }
                    }
                    val sxC = crop.left / 2 + offX / 2 +
                        ((mu * cropW) / 2).toInt().coerceIn(0, srcW / 2 - 1)
                    val syC = crop.top / 2 + offY / 2 +
                        ((mv * cropH) / 2).toInt().coerceIn(0, srcH / 2 - 1)
                    val vIdx = vPlane.rowStride * syC + vPlane.pixelStride * sxC
                    val uIdx = uPlane.rowStride * syC + uPlane.pixelStride * sxC
                    if (vIdx < vBuf.capacity()) out[chromaBase + cy * targetW + cx * 2] = vBuf.get(vIdx)
                    if (uIdx < uBuf.capacity()) out[chromaBase + cy * targetW + cx * 2 + 1] = uBuf.get(uIdx)
                }
            }
            out
        } catch (e: Exception) {
            Log.d("vcamsx", "scaledNv21 failed: $e")
            null
        }
    }

    private fun isImageFormatSupported(image: Image): Boolean {
        val format = image.format
        Log.d("vcamsx", "format$format")
        return when (format) {
            ImageFormat.YUV_420_888, ImageFormat.NV21, ImageFormat.YV12 -> true
            else -> false
        }
    }
}


enum class OutputImageFormat(val friendlyName: String) {
    I420("I420"),
    NV21("NV21"),
    JPEG("JPEG");

    override fun toString() = friendlyName
}

