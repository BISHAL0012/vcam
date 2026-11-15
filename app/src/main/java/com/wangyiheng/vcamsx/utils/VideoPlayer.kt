package com.wangyiheng.vcamsx.utils

import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Surface
import com.wangyiheng.vcamsx.MainHook.Companion.c2_reader_Surfcae
import com.wangyiheng.vcamsx.MainHook.Companion.context
import com.wangyiheng.vcamsx.MainHook.Companion.isPhotoUri
import com.wangyiheng.vcamsx.MainHook.Companion.oriHolder
import com.wangyiheng.vcamsx.MainHook.Companion.original_c1_preview_SurfaceTexture
import com.wangyiheng.vcamsx.MainHook.Companion.original_preview_Surface
import com.wangyiheng.vcamsx.utils.InfoProcesser.videoStatus
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object VideoPlayer {
    var c2_hw_decode_obj: VideoToFrames? = null
    var photo_decode_obj: PhotoToFrames? = null
    var photoSurface: Surface? = null
    var mediaPlayer: MediaPlayer? = null
    var c3_player: MediaPlayer? = null
    var copyReaderSurface:Surface? = null
    var currentRunningSurface:Surface? = null
    private val scheduledExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    init {
        // 初始化代码...
        startTimerTask()
    }

    // 启动定时任务
    private fun startTimerTask() {
        scheduledExecutor.scheduleWithFixedDelay({
            // 每五秒执行的代码
            performTask()
        }, 10, 10, TimeUnit.SECONDS)
    }

    // 实际执行的任务
    private fun performTask() {
        restartMediaPlayer()
    }

    fun restartMediaPlayer(){
        if(videoStatus?.isVideoEnable == true || videoStatus?.isLiveStreamingEnabled == true) return
        if(currentRunningSurface == null || currentRunningSurface?.isValid == false) return
        releaseMediaPlayer()
    }

    fun initMediaPlayer(surface:Surface){
        val volume = if (videoStatus?.volume == true) 1F else 0F
        mediaPlayer = MediaPlayer().apply {
            isLooping = true
            setSurface(surface)
            setVolume(volume,volume)
            setOnPreparedListener { start() }
            val videoPathUri = Uri.parse("content://com.wangyiheng.vcamsx.videoprovider")
            context?.let { setDataSource(it, videoPathUri) }
            prepare()
        }
    }



    // Vcam has no Live Stream / RTMP: this only restores the saved
    // status so the local video player can start in the target app.
    fun initializeTheStateAsWellAsThePlayer(){
        InfoProcesser.initStatus()
    }


    // 将surface传入进行播放
    private fun handleMediaPlayer(surface: Surface) {
        try {
            // 数据初始化
            InfoProcesser.initStatus()

            videoStatus?.also { status ->
                if (!status.isVideoEnable && !status.isLiveStreamingEnabled) return

                if (isPhotoUri(Uri.parse("content://com.wangyiheng.vcamsx.videoprovider"))) {
                    // Live Photo (v1.6): MediaPlayer cannot play a still
                    // image — draw the animated photo onto the surface.
                    startPhotoPlayer(surface)
                    return
                }

                val volume = if (status.volume) 1F else 0F

                mediaPlayer?.also {
                    if (it.isPlaying) {
                        it.setVolume(volume, volume)
                        it.setSurface(surface)
                    } else {
                        releaseMediaPlayer()
                        initMediaPlayer(surface)
                    }
                } ?: run {
                    releaseMediaPlayer()
                    initMediaPlayer(surface)
                }
            }
        } catch (e: Exception) {
            // 这里可以添加更详细的异常处理或日志记录
            logError("MediaPlayer Error", e)
        }
    }

    private fun logError(message: String, e: Exception) {
        // 实现日志记录逻辑，例如使用Android的Log.e函数
        Log.e("MediaPlayerHandler", "$message: ${e.message}")
    }


    // Live Photo (v1.6): (re)start the photo animator on a surface,
    // unless it is already running on that same surface.
    private fun startPhotoPlayer(surface: Surface) {
        if (photo_decode_obj?.isRunning() == true && photoSurface == surface) return
        releaseMediaPlayer()
        photo_decode_obj = PhotoToFrames()
        photo_decode_obj!!.set_surface(surface)
        photo_decode_obj!!.decode(Uri.parse("content://com.wangyiheng.vcamsx.videoprovider"))
        photoSurface = surface
    }

    private fun stopPhotoPlayer() {
        photo_decode_obj?.stopDecode()
        photo_decode_obj = null
        photoSurface = null
    }

    fun releaseMediaPlayer(){
        stopPhotoPlayer()
        if(mediaPlayer == null)return
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun camera2Play() {
        // 带name的surface
        original_preview_Surface?.let { surface ->
            handleMediaPlayer(surface)
        }

        // name=null的surface
        c2_reader_Surfcae?.let { surface ->
            c2_reader_play(surface)
        }
    }

    fun c1_camera_play() {
        if (original_c1_preview_SurfaceTexture != null) {
            original_preview_Surface = Surface(original_c1_preview_SurfaceTexture)
            if(original_preview_Surface!!.isValid == true){
                handleMediaPlayer(original_preview_Surface!!)
            }
        }

        if(oriHolder?.surface != null){
            original_preview_Surface = oriHolder?.surface
            if(original_preview_Surface!!.isValid == true){
                handleMediaPlayer(original_preview_Surface!!)
            }
        }

        c2_reader_Surfcae?.let { surface ->
            c2_reader_play(surface)
        }
    }

    fun c2_reader_play(c2_reader_Surfcae:Surface){
        if(c2_reader_Surfcae == copyReaderSurface){
            if (c2_hw_decode_obj != null || photo_decode_obj?.isRunning() == true) return
            // else: the previous player died — fall through and recreate.
        }

        copyReaderSurface = c2_reader_Surfcae

        if(c2_hw_decode_obj != null){
            c2_hw_decode_obj!!.stopDecode()
            c2_hw_decode_obj = null
        }
        stopPhotoPlayer()

        if (isPhotoUri(Uri.parse("content://com.wangyiheng.vcamsx.videoprovider"))) {
            // Live Photo (v1.6): animate the still image onto the surface.
            photo_decode_obj = PhotoToFrames()
            photo_decode_obj!!.set_surface(c2_reader_Surfcae)
            photo_decode_obj!!.decode(Uri.parse("content://com.wangyiheng.vcamsx.videoprovider"))
            photoSurface = c2_reader_Surfcae
            return
        }

        c2_hw_decode_obj = VideoToFrames()
        try {
            val videoUrl = "content://com.wangyiheng.vcamsx.videoprovider"
            val videoPathUri = Uri.parse(videoUrl)
            c2_hw_decode_obj!!.setSaveFrames(OutputImageFormat.NV21)
            c2_hw_decode_obj!!.set_surface(c2_reader_Surfcae)
            c2_hw_decode_obj!!.decode(videoPathUri)
        }catch (e:Exception){
            Log.d("dbb",e.toString())
        }
    }

}