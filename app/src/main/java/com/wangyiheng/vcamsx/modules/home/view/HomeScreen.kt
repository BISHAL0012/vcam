// Vcam HomeScreen — layout H (colourful tiles grid, mockup vcam-ui-h7.png).
// Original VCAMSX features only: Select Video, View Video, Video /
// Volume / Hard-Decoding switches + key system. NO Sound Boost, NO My
// Videos, NO Floating Logo, NO Auto Leveling, NO Live Stream / RTMP,
// NO mic hook, NO panel. IP collection is gone (Vcam is IP-free).
// NOTE: this file intentionally has NO package declaration (it never
// had one): MainActivity imports it as `import HomeScreen`.
import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wangyiheng.vcamsx.R
import com.wangyiheng.vcamsx.components.KeyDialog
import com.wangyiheng.vcamsx.components.VideoPlayerDialog
import com.wangyiheng.vcamsx.modules.home.controllers.HomeController
import com.wangyiheng.vcamsx.utils.KeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---- Layout H colours ----
private val BgDark = Color(0xFF0B0D18)
private val CardDark = Color(0xFF1A1D30)
private val TileOffGrey = Color(0xFF3A3C48)
private val TileOffText = Color(0xFF9696A6)
private val SubText = Color(0xFFAAAABE)
private val GreenBadge = Color(0xFF22C55E)
private val AmberWarn = Color(0xFFFFC94D)
private val KeyGradStart = Color(0xFF581C87)
private val KeyGradEnd = Color(0xFFA855F7)

private val SelectGrad = listOf(Color(0xFF7C3AED), Color(0xFFEC4899))
private val ViewGrad = listOf(Color(0xFF2563EB), Color(0xFF06B6D4))
private val VideoGrad = listOf(Color(0xFFF59E0B), Color(0xFFF97316))
private val VolumeGrad = listOf(Color(0xFF10B981), Color(0xFF059669))

@Composable
private fun GradientTile(
    gradient: List<Color>,
    isOn: Boolean,
    iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.3f)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (isOn) Brush.horizontalGradient(gradient)
                else Brush.horizontalGradient(listOf(TileOffGrey, TileOffGrey))
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(
                    if (isOn) Color.White else TileOffText
                ),
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = label,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = if (isOn) Color.White else TileOffText,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val homeController = viewModel<HomeController>()

    // ---- Key gate (checked on every app start) ----
    var keyState by remember { mutableStateOf(KeyManager.State.CHECKING) }
    var daysLeft by remember { mutableStateOf(-1) }
    var timeLeft by remember { mutableStateOf("") }
    var expiresAt by remember { mutableStateOf<String?>(null) }
    var telegramUrl by remember { mutableStateOf("https://t.me/") }
    var imoUrl by remember { mutableStateOf("https://imo.im/") }
    var keyMessage by remember { mutableStateOf<String?>(null) }
    var checkNonce by remember { mutableStateOf(0) }

    LaunchedEffect(checkNonce) {
        homeController.init()
        val result = KeyManager.check(context)
        keyState = result.state
        daysLeft = result.daysLeft
        timeLeft = result.timeLeft
        expiresAt = result.expiresAt
        telegramUrl = result.telegramUrl
        imoUrl = result.imoUrl
        keyMessage = result.message
    }

    fun openUrl(url: String) {
        if (url.isBlank()) return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }

    when (keyState) {
        KeyManager.State.CHECKING -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDark),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFA855F7))
                    Spacer(Modifier.height(16.dp))
                    Text("Checking key...", color = Color.White, fontSize = 16.sp)
                }
            }
            return
        }
        KeyManager.State.NO_INTERNET -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDark)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Internet required",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        keyMessage ?: "Check your connection and retry.",
                        color = SubText,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { checkNonce++ }) { Text("Retry") }
                }
            }
            return
        }
        KeyManager.State.UPDATE_REQUIRED -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDark)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Please update Vcam",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        keyMessage ?: "This version is too old.",
                        color = SubText,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { openUrl(telegramUrl) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2DA8E0)
                            )
                        ) { Text("Telegram") }
                        Button(
                            onClick = { openUrl(imoUrl) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00B964)
                            )
                        ) { Text("imo") }
                    }
                }
            }
            return
        }
        KeyManager.State.NEED_KEY -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDark),
                contentAlignment = Alignment.Center
            ) {
                Text("Vcam", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            KeyDialog(
                telegramUrl = telegramUrl,
                imoUrl = imoUrl,
                onActivated = { result ->
                    daysLeft = result.daysLeft
                    timeLeft = result.timeLeft
                    expiresAt = result.expiresAt
                    telegramUrl = result.telegramUrl
                    imoUrl = result.imoUrl
                    keyState = KeyManager.State.VALID
                }
            )
            return
        }
        KeyManager.State.VALID -> {
            // Fall through to the home UI below.
        }
    }

    // ---- Home UI (key is valid) ----
    var currentVideoUrl by remember {
        mutableStateOf(homeController.infoManager.getVideoInfo()?.videoUrl)
    }

    val selectVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            homeController.copyVideoToAppDir(context, it)
            currentVideoUrl = it.toString()
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted: Boolean ->
            if (isGranted) {
                selectVideoLauncher.launch("video/*")
            } else {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    Toast.makeText(
                        context,
                        "Please open Settings and allow folder permission",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    selectVideoLauncher.launch("video/*")
                }
            }
        }
    )

    val banner by produceState<Bitmap?>(null, currentVideoUrl) {
        value = currentVideoUrl?.let { homeController.loadVideoBanner(it) }
    }
    val displayName by produceState<String?>(null, currentVideoUrl) {
        value = currentVideoUrl?.let {
            withContext(Dispatchers.IO) { homeController.queryVideoDisplayName(it) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header: logo + Vcam + days-left badge.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Vcam",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(text = "Virtual Camera", fontSize = 13.sp, color = SubText)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(19.dp))
                    .background(if (daysLeft in 0..3) Color(0xFFF59E0B) else GreenBadge)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (timeLeft.isNotBlank()) timeLeft
                        else if (daysLeft >= 0) "$daysLeft days left" else "Key active",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // 2x2 tiles.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                GradientTile(
                    gradient = SelectGrad,
                    isOn = true,
                    iconRes = R.drawable.ic_tile_select,
                    label = "Select\nVideo",
                    onClick = {
                        requestPermissionLauncher.launch(
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        )
                    }
                )
            }
            Box(Modifier.weight(1f)) {
                GradientTile(
                    gradient = ViewGrad,
                    isOn = true,
                    iconRes = R.drawable.ic_tile_view,
                    label = "View\nVideo",
                    onClick = { homeController.isVideoDisplay.value = true }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                GradientTile(
                    gradient = VideoGrad,
                    isOn = homeController.isVideoEnabled.value,
                    iconRes = R.drawable.ic_tile_video,
                    label = "Video\n" + if (homeController.isVideoEnabled.value) "ON" else "OFF",
                    onClick = {
                        val on = !homeController.isVideoEnabled.value
                        homeController.isVideoEnabled.value = on
                        homeController.saveState()
                        Toast.makeText(
                            context,
                            if (on) "Video ON" else "Video OFF",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
            Box(Modifier.weight(1f)) {
                GradientTile(
                    gradient = VolumeGrad,
                    isOn = homeController.isVolumeEnabled.value,
                    iconRes = R.drawable.ic_tile_volume,
                    label = "Volume\n" + if (homeController.isVolumeEnabled.value) "ON" else "OFF",
                    onClick = {
                        val on = !homeController.isVolumeEnabled.value
                        homeController.isVolumeEnabled.value = on
                        homeController.saveState()
                        Toast.makeText(
                            context,
                            if (on) "Volume ON" else "Volume OFF",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }

        // Hard / Soft Decoding switch (original switch, dark row).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardDark)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = if (homeController.codecType.value) "Hard Decoding" else "Soft Decoding",
                fontSize = 18.sp,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = homeController.codecType.value,
                onCheckedChange = {
                    if (homeController.isH264HardwareDecoderSupport()) {
                        homeController.codecType.value = it
                        homeController.saveState()
                        Toast.makeText(
                            context,
                            if (it) "Hard Decoding ON" else "Soft Decoding ON",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        homeController.codecType.value = false
                        Toast.makeText(
                            context,
                            "Hard decoding not supported",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        // Now Playing card: video banner (first-frame thumbnail) + name.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(CardDark)
                .padding(16.dp)
        ) {
            Text(text = "Now Playing", fontSize = 14.sp, color = SubText)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF2A2D42)),
                contentAlignment = Alignment.Center
            ) {
                val thumb = banner
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        currentVideoUrl.isNullOrBlank() -> "No video"
                        !displayName.isNullOrBlank() -> displayName!!
                        else -> currentVideoUrl!!.substringAfterLast('/').ifBlank { "Video 1" }
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = when {
                        currentVideoUrl.isNullOrBlank() -> ""
                        homeController.isVideoEnabled.value -> "Playing"
                        else -> "Paused"
                    },
                    fontSize = 14.sp,
                    color = if (homeController.isVideoEnabled.value) Color(0xFF4ADE80)
                    else SubText
                )
            }
        }

        // Key card: expiry + renew.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.horizontalGradient(listOf(KeyGradStart, KeyGradEnd)))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_key),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Key valid until ${expiresAt?.let { KeyManager.displayExpiry(it) } ?: "—"}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Renew: Telegram / imo",
                fontSize = 15.sp,
                color = if (daysLeft in 0..3) AmberWarn else Color(0xFFEBDCFF)
            )
        }
    }

    VideoPlayerDialog(homeController)
}
