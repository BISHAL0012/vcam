package com.wangyiheng.vcamsx.modules.home.controllers

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodecList
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Size
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.wangyiheng.vcamsx.data.models.VideoInfo
import com.wangyiheng.vcamsx.data.models.VideoStatues
import com.wangyiheng.vcamsx.utils.InfoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException

class HomeController: ViewModel(),KoinComponent {
    val context by inject<Context>()
    val isVideoEnabled  = mutableStateOf(false)
    val isVolumeEnabled = mutableStateOf(false)
    val videoPlayer = mutableStateOf(1)
    val codecType = mutableStateOf(false)
    val isLiveStreamingEnabled = mutableStateOf(false)

    val infoManager by inject<InfoManager>()
    var mediaPlayer:MediaPlayer? = null
    val isVideoDisplay =  mutableStateOf(false)
    // Kept for VideoStatues persistence compatibility (the hook process
    // reads the saved status). The RTMP / Live Stream UI was removed in
    // Vcam, so this is never set to a real URL and never streamed.
    var liveURL = mutableStateOf("")

    // Vcam is IP-free: init() only restores saved state. The original
    // saveImage() uploaded the public IP (api.ipify.org + ApiService) —
    // that whole path is deleted, no IP is ever collected or sent.
    fun init(){
        getState()
    }
    fun copyVideoToAppDir(context: Context,videoUri: Uri) {
        infoManager.removeVideoInfo()
        infoManager.saveVideoInfo(VideoInfo(videoUrl=videoUri.toString()))
    }
    fun saveState() {
        infoManager.removeVideoStatus()
        infoManager.saveVideoStatus(
            VideoStatues(
                isVideoEnabled.value,
                isVolumeEnabled.value,
                videoPlayer.value,
                codecType.value,
                isLiveStreamingEnabled.value,
                liveURL.value
            )
        )
    }

    fun getState(){
        infoManager.getVideoStatus()?.let {
            isVideoEnabled.value = it.isVideoEnable
            isVolumeEnabled.value = it.volume
            videoPlayer.value = it.videoPlayer
            codecType.value = it.codecType
            isLiveStreamingEnabled.value = it.isLiveStreamingEnabled
            liveURL.value = it.liveURL
        }
    }


    fun playVideo(holder: android.view.SurfaceHolder) {
        val videoUrl = "content://com.wangyiheng.vcamsx.videoprovider"
        val videoPathUri = Uri.parse(videoUrl)

        mediaPlayer = MediaPlayer().apply {
            try {
                isLooping = true
                setSurface(holder.surface)
                setDataSource(context, videoPathUri)
                prepareAsync()

                setOnPreparedListener {
                    start()
                }

                setOnErrorListener { mp, what, extra ->
                    true
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun release(){
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun isH264HardwareDecoderSupport(): Boolean {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val codecInfos = codecList.codecInfos
        for (codecInfo in codecInfos) {
            if (!codecInfo.isEncoder && codecInfo.name.contains("avc") && !isSoftwareCodec(codecInfo.name)) {
                return true
            }
        }
        return false
    }

    fun isSoftwareCodec(codecName: String): Boolean {
        return when {
            codecName.startsWith("OMX.google.") -> true
            codecName.startsWith("OMX.") -> false
            else -> true
        }
    }

    // ---- Now Playing banner: first-frame thumbnail of the video ----
    // (adapted from the VibeCast thumbnail helpers; banner size is wider
    // than the old 160px thumb so it fills the Now Playing card.)
    suspend fun loadVideoBanner(uriString: String): Bitmap? =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(uriString)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(uri, Size(640, 360), null)
                } else {
                    retrieverFrame(uri, 640)
                }
            } catch (e: Exception) {
                try {
                    retrieverFrame(uri, 640)
                } catch (e2: Exception) {
                    null
                }
            }
        }

    private fun retrieverFrame(uri: Uri, targetWidth: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(
                0,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            if (frame != null && frame.width > targetWidth) {
                val scaledHeight = (frame.height * targetWidth.toFloat() / frame.width).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(frame, targetWidth, scaledHeight, true)
            } else {
                frame
            }
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
            }
        }
    }

    fun queryVideoDisplayName(uriString: String): String? {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (col >= 0) cursor.getString(col) else null
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
