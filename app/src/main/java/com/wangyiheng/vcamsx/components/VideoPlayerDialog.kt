package com.wangyiheng.vcamsx.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.wangyiheng.vcamsx.modules.home.controllers.HomeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun VideoPlayerDialog(homeController: HomeController) {
    if (!homeController.isVideoDisplay.value) return
    val context = LocalContext.current
    val videoUrl = homeController.infoManager.getVideoInfo()?.videoUrl
    // Live Photo (v1.6): a picked photo is previewed as an image —
    // MediaPlayer cannot play a still image.
    val isPhoto = remember(videoUrl) {
        videoUrl?.let {
            try {
                context.contentResolver.getType(Uri.parse(it))?.startsWith("image/") == true
            } catch (e: Exception) {
                false
            }
        } ?: false
    }
    val photoBitmap by produceState<Bitmap?>(null, videoUrl, isPhoto) {
        value = if (isPhoto && videoUrl != null) {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(Uri.parse(videoUrl))?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } catch (e: Exception) {
                    null
                }
            }
        } else {
            null
        }
    }
    Dialog(onDismissRequest = {
        homeController.isVideoDisplay.value = false
    }) {
        Column(
            modifier = Modifier.size(width = 300.dp, height = 400.dp), // 设置Dialog的大小
            verticalArrangement = Arrangement.Center
        ) {
            if (isPhoto) {
                if (photoBitmap != null) {
                    Image(
                        bitmap = photoBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                } else {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                AndroidView(
                    modifier = Modifier.weight(1f), // 让视频播放器填充除按钮以外的空间
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    homeController.playVideo(holder)
                                }
                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                }

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    // 这里释放播放器资源
                                    homeController.release()
                                }
                            })
                        }
                    }
                )
            }
        }
    }
}
