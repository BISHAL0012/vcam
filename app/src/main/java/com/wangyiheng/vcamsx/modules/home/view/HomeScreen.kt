// Vcam HomeScreen — GLASS design (v1.5, mockup vcam-glass reference).
// Aurora gradient background + frosted-glass cards. Home UI only:
// Select Video (hero), View Video, Video / Volume / Soft-Decoding
// switches, Now Playing banner, License pill, Key bar + Renew.
// Original VCAMSX features only: NO Sound Boost, NO My Videos,
// NO Floating Logo, NO Auto Leveling, NO Live Stream / RTMP,
// NO mic hook, NO panel. IP collection is gone (Vcam is IP-free).
// Logo is an iPhone-style rounded square, NOT a circle.
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageVector
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.PathParser
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ---- Glass icons: Material Symbols (filled), paths extracted from the font ----
private fun glassVector(pathData: String): ImageVector =
    ImageVector.Builder("glass", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            PathParser().parsePathString(pathData),
            PathFillType.NonZero,
            fill = SolidColor(Color.Black)
        ).build()

private val IconCrown = glassVector(
    "M5.59,21.56 Q5.2,21.56,4.95,21.3 Q4.71,21.04,4.71,20.66 Q4.71,20.29,4.95,20.04 Q5.2,19.8,5.59,19.8 L18.41,19.8 Q18.8,19.8,19.06,20.06 Q19.32,20.31,19.32,20.69 Q19.32,21.06,19.06,21.31 Q18.8,21.56,18.41,21.56 Z M6.18,17.62 Q5.56,17.62,5.06,17.2 Q4.55,16.77,4.45,16.12 L3.31,8.94 Q3.18,8.99,3.08,9 Q2.97,9.01,2.87,9.01 Q2.3,8.99,1.9,8.59 Q1.5,8.19,1.5,7.62 Q1.5,7.02,1.91,6.61 Q2.33,6.19,2.9,6.19 Q3.49,6.19,3.91,6.61 Q4.32,7.02,4.32,7.62 Q4.32,7.82,4.25,8.02 Q4.19,8.21,4.09,8.34 L7.78,10 Q7.78,10,7.78,10 Q7.78,10,7.78,10 L11.35,5.08 Q10.99,4.9,10.78,4.58 Q10.58,4.25,10.58,3.84 Q10.58,3.25,10.99,2.84 Q11.41,2.44,12,2.44 Q12.59,2.44,13.01,2.84 Q13.42,3.25,13.42,3.84 Q13.42,4.25,13.22,4.58 Q13.01,4.9,12.65,5.08 L16.22,10 Q16.22,10,16.22,10 Q16.22,10,16.22,10 L19.91,8.34 Q19.81,8.21,19.75,8.02 Q19.68,7.82,19.68,7.62 Q19.68,7.02,20.09,6.61 Q20.51,6.19,21.08,6.19 Q21.67,6.19,22.09,6.61 Q22.5,7.02,22.5,7.62 Q22.5,8.19,22.1,8.59 Q21.7,8.99,21.1,9.01 Q21,9.01,20.91,8.99 Q20.82,8.96,20.69,8.91 L19.55,16.12 Q19.45,16.77,18.97,17.2 Q18.49,17.62,17.82,17.62 Z M6.18,15.87 L17.82,15.87 Q17.82,15.87,17.82,15.87 Q17.82,15.87,17.82,15.87 L18.59,10.85 L16.94,11.6 Q16.34,11.88,15.76,11.7 Q15.18,11.52,14.79,11.03 L12,7.15 L9.21,11.03 Q8.82,11.52,8.22,11.7 Q7.63,11.88,7.06,11.6 L5.38,10.85 L6.18,15.87 Q6.18,15.87,6.18,15.87 Q6.18,15.87,6.18,15.87 Z M6.21,15.87 Q6.21,15.87,6.21,15.87 Q6.21,15.87,6.21,15.87 L5.41,10.77 L7.16,11.57 Q7.71,11.81,8.26,11.65 Q8.82,11.5,9.18,11.03 L12,7.12 L14.82,11.03 Q15.18,11.5,15.74,11.65 Q16.29,11.81,16.84,11.57 L18.59,10.77 L17.79,15.87 Q17.79,15.87,17.79,15.87 Q17.79,15.87,17.79,15.87 Z"
)

private val IconSettings = glassVector(
    "M10.4,22.5 Q10.01,22.5,9.7,22.25 Q9.39,22.01,9.34,21.62 L8.95,19.19 Q8.51,19.03,7.99,18.74 Q7.47,18.44,7.09,18.13 L4.89,19.16 Q4.5,19.32,4.11,19.2 Q3.72,19.09,3.52,18.72 L1.91,15.88 Q1.71,15.52,1.8,15.14 Q1.89,14.77,2.22,14.51 L4.29,12.98 Q4.24,12.78,4.23,12.49 Q4.22,12.21,4.22,12 Q4.22,11.79,4.23,11.51 Q4.24,11.22,4.29,11.02 L2.22,9.49 Q1.89,9.26,1.8,8.87 Q1.71,8.48,1.91,8.12 L3.52,5.28 Q3.72,4.94,4.11,4.81 Q4.5,4.68,4.86,4.84 L7.11,5.87 Q7.47,5.56,7.99,5.28 Q8.51,4.99,8.95,4.84 L9.34,2.38 Q9.39,1.99,9.7,1.75 Q10.01,1.5,10.4,1.5 L13.6,1.5 Q13.99,1.5,14.3,1.75 Q14.61,1.99,14.66,2.38 L15.05,4.81 Q15.49,4.99,16.02,5.26 Q16.55,5.53,16.91,5.87 L19.11,4.84 Q19.5,4.68,19.88,4.81 Q20.25,4.94,20.46,5.28 L22.09,8.12 Q22.29,8.48,22.2,8.88 Q22.11,9.28,21.78,9.49 L19.71,10.97 Q19.76,11.2,19.77,11.48 Q19.78,11.77,19.78,12 Q19.78,12.23,19.77,12.5 Q19.76,12.78,19.71,13.01 L21.78,14.51 Q22.11,14.72,22.2,15.12 Q22.29,15.52,22.09,15.88 L20.48,18.72 Q20.28,19.09,19.89,19.2 Q19.5,19.32,19.14,19.16 L16.89,18.13 Q16.53,18.44,16.02,18.75 Q15.52,19.06,15.05,19.19 L14.66,21.62 Q14.61,22.01,14.3,22.25 Q13.99,22.5,13.6,22.5 Z M10.91,20.74 L13.09,20.74 L13.45,17.84 Q14.3,17.64,15.08,17.2 Q15.85,16.76,16.47,16.11 L19.22,17.3 L20.2,15.52 L17.77,13.76 Q17.87,13.32,17.95,12.88 Q18.03,12.44,18.03,12 Q18.03,11.56,17.96,11.12 Q17.9,10.68,17.77,10.24 L20.2,8.48 L19.22,6.7 L16.47,7.89 Q15.88,7.19,15.12,6.72 Q14.35,6.26,13.45,6.16 L13.09,3.26 L10.91,3.26 L10.55,6.16 Q9.67,6.31,8.9,6.76 Q8.12,7.22,7.5,7.89 L4.78,6.7 L3.8,8.48 L6.21,10.24 Q6.1,10.68,6.04,11.12 Q5.97,11.56,5.97,12 Q5.97,12.44,6.04,12.88 Q6.1,13.32,6.21,13.76 L3.8,15.52 L4.78,17.3 L7.5,16.11 Q8.15,16.76,8.92,17.2 Q9.7,17.64,10.55,17.84 Z M12,15.36 Q13.4,15.36,14.38,14.38 Q15.36,13.4,15.36,12 Q15.36,10.6,14.38,9.62 Q13.4,8.64,12,8.64 Q10.6,8.64,9.62,9.62 Q8.64,10.6,8.64,12 Q8.64,13.4,9.62,14.38 Q10.6,15.36,12,15.36 Z M10.89,20.69 L10.53,17.82 Q9.67,17.64,8.92,17.2 Q8.17,16.76,7.58,16.14 L4.86,17.3 L3.83,15.47 L6.23,13.71 Q6.13,13.27,6.06,12.85 Q6,12.44,6,12 Q6,11.56,6.06,11.13 Q6.13,10.71,6.23,10.29 L3.83,8.53 L4.86,6.67 L7.58,7.86 Q8.17,7.22,8.91,6.79 Q9.65,6.36,10.53,6.16 L10.89,3.31 L13.11,3.31 L13.47,6.16 Q14.35,6.31,15.09,6.75 Q15.83,7.19,16.42,7.86 L19.14,6.67 L20.17,8.53 L17.77,10.29 Q17.9,10.71,17.95,11.13 Q18,11.56,18,12 Q18,12.44,17.94,12.87 Q17.87,13.29,17.77,13.71 L20.17,15.47 L19.14,17.3 L16.42,16.14 Q15.83,16.76,15.08,17.2 Q14.33,17.64,13.47,17.82 L13.11,20.69 Z"
)

private val IconClock = glassVector(
    "M12.93,11.64 L12.93,7.37 Q12.93,6.98,12.69,6.75 Q12.44,6.52,12.08,6.52 Q11.72,6.52,11.47,6.75 Q11.22,6.98,11.22,7.37 L11.22,12 Q11.22,12.16,11.29,12.31 Q11.35,12.47,11.46,12.59 L15.1,16.37 Q15.36,16.66,15.74,16.64 Q16.11,16.63,16.37,16.37 Q16.63,16.14,16.63,15.76 Q16.63,15.39,16.37,15.13 Z M12,22.5 Q9.85,22.5,7.93,21.67 Q6,20.84,4.58,19.42 Q3.16,18,2.33,16.07 Q1.5,14.15,1.5,12 Q1.5,9.85,2.33,7.93 Q3.16,6,4.58,4.58 Q6,3.16,7.93,2.33 Q9.85,1.5,12,1.5 Q14.15,1.5,16.07,2.33 Q18,3.16,19.42,4.58 Q20.84,6,21.67,7.93 Q22.5,9.85,22.5,12 Q22.5,14.15,21.67,16.07 Q20.84,18,19.42,19.42 Q18,20.84,16.09,21.67 Q14.17,22.5,12,22.5 Z M12,20.69 Q8.41,20.69,5.86,18.14 Q3.31,15.59,3.31,12 Q3.31,8.41,5.86,5.86 Q8.41,3.31,12,3.31 Q15.59,3.31,18.14,5.86 Q20.69,8.41,20.69,12 Q20.69,15.59,18.14,18.14 Q15.59,20.69,12,20.69 Z M12,20.74 Q15.59,20.74,18.17,18.18 Q20.74,15.62,20.74,12 Q20.74,8.41,18.17,5.83 Q15.59,3.26,12,3.26 Q8.41,3.26,5.83,5.83 Q3.26,8.41,3.26,12 Q3.26,15.62,5.83,18.18 Q8.41,20.74,12,20.74 Z"
)

private val IconVideocam = glassVector(
    "M3.26,20.43 Q2.56,20.43,2.03,19.9 Q1.5,19.37,1.5,18.67 L1.5,5.33 Q1.5,4.6,2.03,4.09 Q2.56,3.57,3.26,3.57 L16.6,3.57 Q17.33,3.57,17.84,4.09 Q18.36,4.6,18.36,5.33 L18.36,10.84 L21.75,7.47 Q21.93,7.27,22.22,7.37 Q22.5,7.47,22.5,7.76 L22.5,16.24 Q22.5,16.53,22.22,16.63 Q21.93,16.73,21.75,16.53 L18.36,13.16 L18.36,18.67 Q18.36,19.37,17.84,19.9 Q17.33,20.43,16.6,20.43 Z M3.26,18.67 L16.6,18.67 Q16.6,18.67,16.6,18.67 Q16.6,18.67,16.6,18.67 L16.6,5.33 Q16.6,5.33,16.6,5.33 Q16.6,5.33,16.6,5.33 L3.26,5.33 Q3.26,5.33,3.26,5.33 Q3.26,5.33,3.26,5.33 L3.26,18.67 Q3.26,18.67,3.26,18.67 Q3.26,18.67,3.26,18.67 Z M3.26,18.67 Q3.26,18.67,3.26,18.67 Q3.26,18.67,3.26,18.67 L3.26,5.33 Q3.26,5.33,3.26,5.33 Q3.26,5.33,3.26,5.33 L16.53,5.33 Q16.53,5.33,16.53,5.33 Q16.53,5.33,16.53,5.33 L16.53,18.67 Q16.53,18.67,16.53,18.67 Q16.53,18.67,16.53,18.67 Z"
)

private val IconEye = glassVector(
    "M12,16.09 Q13.71,16.09,14.9,14.9 Q16.09,13.71,16.09,12 Q16.09,10.29,14.9,9.1 Q13.71,7.91,12,7.91 Q10.29,7.91,9.1,9.1 Q7.91,10.29,7.91,12 Q7.91,13.71,9.1,14.9 Q10.29,16.09,12,16.09 Z M12,14.6 Q10.92,14.6,10.16,13.84 Q9.4,13.08,9.4,12 Q9.4,10.92,10.16,10.16 Q10.92,9.4,12,9.4 Q13.08,9.4,13.84,10.16 Q14.6,10.92,14.6,12 Q14.6,13.08,13.84,13.84 Q13.08,14.6,12,14.6 Z M1.72,12.82 Q1.62,12.63,1.56,12.42 Q1.5,12.22,1.5,12 Q1.5,11.78,1.56,11.58 Q1.62,11.37,1.72,11.18 Q3.35,8.32,6.04,6.47 Q8.72,4.63,12,4.63 Q15.28,4.63,17.96,6.47 Q20.65,8.32,22.28,11.18 Q22.38,11.37,22.44,11.58 Q22.5,11.78,22.5,12 Q22.5,12.22,22.44,12.42 Q22.38,12.63,22.28,12.82 Q20.65,15.68,17.96,17.53 Q15.28,19.37,12,19.37 Q8.72,19.37,6.04,17.53 Q3.35,15.68,1.72,12.82 Z M3.02,12 Q4.29,9.4,6.7,7.86 Q9.11,6.32,12,6.32 Q14.89,6.32,17.3,7.86 Q19.71,9.4,20.96,12 Q19.71,14.6,17.3,16.14 Q14.89,17.68,12,17.68 Q9.11,17.68,6.7,16.14 Q4.29,14.6,3.02,12 Z M21.08,12 Q19.8,9.38,17.36,7.8 Q14.91,6.22,12,6.22 Q9.09,6.22,6.64,7.8 Q4.2,9.38,2.9,12 Q4.2,14.62,6.64,16.2 Q9.09,17.78,12,17.78 Q14.91,17.78,17.36,16.2 Q19.8,14.62,21.08,12 Z"
)

private val IconVolume = glassVector(
    "M20.73,12 Q20.73,9.31,19.22,7.18 Q17.72,5.05,15.29,4.05 Q14.95,3.9,14.8,3.57 Q14.66,3.25,14.8,2.93 Q14.98,2.56,15.33,2.43 Q15.69,2.3,16.09,2.44 Q18.98,3.65,20.74,6.22 Q22.5,8.8,22.5,11.99 Q22.5,15.18,20.74,17.76 Q18.98,20.35,16.09,21.56 Q15.69,21.7,15.33,21.57 Q14.98,21.44,14.8,21.07 Q14.66,20.75,14.8,20.43 Q14.95,20.1,15.29,19.95 Q17.72,18.95,19.22,16.82 Q20.73,14.69,20.73,12 Z M6.16,15.63 L2.47,15.63 Q2.04,15.63,1.77,15.36 Q1.5,15.09,1.5,14.66 L1.5,9.4 Q1.5,8.97,1.77,8.7 Q2.04,8.42,2.47,8.42 L6.16,8.42 L10.48,4.13 Q10.94,3.65,11.54,3.9 Q12.14,4.16,12.14,4.79 L12.14,19.27 Q12.14,19.9,11.54,20.15 Q10.94,20.41,10.48,19.93 Z M17.35,12.03 Q17.35,13.52,16.61,14.73 Q15.86,15.95,14.49,16.58 Q14.26,16.72,14.06,16.56 Q13.86,16.41,13.86,16.15 L13.86,7.88 Q13.86,7.62,14.06,7.48 Q14.26,7.34,14.49,7.45 Q15.86,8.08,16.61,9.32 Q17.35,10.57,17.35,12.03 Z M10.17,7.37 L7.05,10.37 L3.45,10.37 L3.45,13.69 L7.05,13.69 L10.17,16.72 Z M10.14,16.63 L7.05,13.63 L3.47,13.63 L3.47,10.43 L7.05,10.43 L10.14,7.42 Z"
)

private val IconLayers = glassVector(
    "M1.96,15.22 Q1.57,14.92,1.57,14.43 Q1.57,13.93,1.99,13.63 Q2.29,13.42,2.62,13.42 Q2.95,13.42,3.22,13.63 L12,20.46 Q12,20.46,12,20.46 Q12,20.46,12,20.46 L20.78,13.63 Q21.05,13.42,21.39,13.42 Q21.74,13.42,22.01,13.63 Q22.43,13.93,22.43,14.43 Q22.43,14.92,22.04,15.22 L13.26,22.05 Q12.69,22.5,12,22.5 Q11.31,22.5,10.74,22.05 Z M10.74,17.2 L2.98,11.18 Q2.2,10.55,2.2,9.56 Q2.2,8.57,2.98,7.97 L10.74,1.92 Q11.28,1.5,11.99,1.5 Q12.69,1.5,13.26,1.92 L20.99,7.97 Q21.8,8.57,21.8,9.56 Q21.8,10.55,20.99,11.18 L13.26,17.2 Q12.69,17.65,12,17.65 Q11.31,17.65,10.74,17.2 Z M12,15.58 L19.73,9.56 Q19.73,9.56,19.73,9.56 Q19.73,9.56,19.73,9.56 L12,3.54 Q12,3.54,12,3.54 Q12,3.54,12,3.54 L4.24,9.56 Q4.24,9.56,4.24,9.56 Q4.24,9.56,4.24,9.56 L12,15.58 Q12,15.58,12,15.58 Q12,15.58,12,15.58 Z M12,15.52 Q12,15.52,12,15.52 Q12,15.52,12,15.52 L4.33,9.56 Q4.33,9.56,4.33,9.56 Q4.33,9.56,4.33,9.56 L12,3.6 Q12,3.6,12,3.6 Q12,3.6,12,3.6 L19.67,9.56 Q19.67,9.56,19.67,9.56 Q19.67,9.56,19.67,9.56 Z"
)

private val IconChevron = glassVector(
    "M14.26,11.98 L6.4,4.12 Q5.95,3.67,5.97,3.04 Q5.99,2.4,6.45,1.95 Q6.9,1.5,7.55,1.5 Q8.21,1.5,8.66,1.95 L17.6,10.89 Q17.83,11.12,17.94,11.39 Q18.05,11.66,18.05,11.98 Q18.05,12.29,17.94,12.56 Q17.83,12.84,17.6,13.06 L8.61,22.05 Q8.16,22.5,7.53,22.48 Q6.9,22.45,6.45,22 Q5.99,21.55,5.99,20.9 Q5.99,20.24,6.45,19.79 Z"
)

private val IconPause = glassVector(
    "M17.23,22.5 Q16.22,22.5,15.5,21.78 Q14.78,21.06,14.78,20.05 L14.78,3.95 Q14.78,2.94,15.5,2.22 Q16.22,1.5,17.23,1.5 L18.89,1.5 Q19.9,1.5,20.62,2.22 Q21.35,2.94,21.35,3.95 L21.35,20.05 Q21.35,21.06,20.62,21.78 Q19.9,22.5,18.89,22.5 Z M5.11,22.5 Q4.1,22.5,3.38,21.78 Q2.65,21.06,2.65,20.05 L2.65,3.95 Q2.65,2.94,3.38,2.22 Q4.1,1.5,5.11,1.5 L6.77,1.5 Q7.78,1.5,8.5,2.22 Q9.22,2.94,9.22,3.95 L9.22,20.05 Q9.22,21.06,8.5,21.78 Q7.78,22.5,6.77,22.5 Z M17.23,20.05 L18.89,20.05 L18.89,3.95 L17.23,3.95 Z M5.11,20.05 L6.77,20.05 L6.77,3.95 L5.11,3.95 Z M5.11,3.95 L6.73,3.95 L6.73,20.05 L5.11,20.05 Z M17.23,3.95 L18.86,3.95 L18.86,20.05 L17.23,20.05 Z"
)

private val IconPlay = glassVector(
    "M3.44,21.14 L3.44,2.9 Q3.44,2.26,3.84,1.88 Q4.24,1.5,4.8,1.5 Q5,1.5,5.18,1.56 Q5.36,1.62,5.52,1.7 L19.92,10.9 Q20.24,11.1,20.4,11.38 Q20.56,11.66,20.56,12.02 Q20.56,12.38,20.4,12.66 Q20.24,12.94,19.92,13.14 L5.52,22.34 Q5.36,22.42,5.16,22.46 Q4.96,22.5,4.8,22.5 Q4.24,22.5,3.84,22.14 Q3.44,21.78,3.44,21.14 Z M6.16,5.46 L16.52,12.02 L6.16,18.58 Z M6.16,18.7 L16.68,12.02 L6.16,5.34 Z"
)

private val IconFilm = glassVector(
    "M3.26,3.57 L4.76,6.65 Q4.97,7.03,5.34,7.27 Q5.72,7.5,6.13,7.5 Q6.98,7.5,7.41,6.79 Q7.84,6.08,7.47,5.33 L6.62,3.57 L8.92,3.57 L10.42,6.65 Q10.63,7.03,11,7.27 Q11.38,7.5,11.79,7.5 Q12.65,7.5,13.07,6.79 Q13.5,6.08,13.14,5.33 L12.28,3.57 L14.59,3.57 L16.09,6.65 Q16.29,7.03,16.67,7.27 Q17.04,7.5,17.46,7.5 Q18.31,7.5,18.74,6.79 Q19.16,6.08,18.8,5.33 L17.95,3.57 L20.74,3.57 Q21.47,3.57,21.98,4.09 Q22.5,4.6,22.5,5.33 L22.5,18.67 Q22.5,19.37,21.98,19.9 Q21.47,20.43,20.74,20.43 L3.26,20.43 Q2.56,20.43,2.03,19.91 Q1.5,19.4,1.5,18.67 L1.5,5.33 Q1.5,4.6,2.03,4.09 Q2.56,3.57,3.26,3.57 Z M3.26,9.26 L3.26,18.67 Q3.26,18.67,3.26,18.67 Q3.26,18.67,3.26,18.67 L20.74,18.67 Q20.74,18.67,20.74,18.67 Q20.74,18.67,20.74,18.67 L20.74,9.26 Z M3.26,9.26 L20.66,9.26 L20.66,18.67 Q20.66,18.67,20.66,18.67 Q20.66,18.67,20.66,18.67 L3.26,18.67 Q3.26,18.67,3.26,18.67 Q3.26,18.67,3.26,18.67 Z"
)

private val IconSend = glassVector(
    "M21.89,12.93 L2.9,20.91 Q2.41,21.11,1.95,20.84 Q1.5,20.56,1.5,20.03 L1.5,3.97 Q1.5,3.44,1.95,3.16 Q2.41,2.89,2.9,3.09 L21.89,11.07 Q22.5,11.33,22.5,12 Q22.5,12.67,21.89,12.93 Z M3.43,18.54 L19.11,12 L3.43,5.37 L3.43,10.1 L10.58,12 L3.43,13.84 Z M3.43,18.54 L18.97,12 L3.43,5.37 L3.43,10.1 L1.56,9.63 L1.56,14.31 L3.43,13.84 Z"
)

private val IconExpand = glassVector(
    "M2.48,22.5 Q2.07,22.5,1.79,22.21 Q1.5,21.93,1.5,21.52 L1.5,14.64 Q1.5,14.21,1.79,13.94 Q2.07,13.66,2.49,13.66 Q2.91,13.66,3.18,13.94 Q3.45,14.21,3.45,14.64 L3.45,19.17 L19.17,3.45 L14.64,3.45 Q14.21,3.45,13.94,3.16 Q13.66,2.88,13.66,2.46 Q13.66,2.05,13.94,1.77 Q14.21,1.5,14.64,1.5 L21.52,1.5 Q21.93,1.5,22.21,1.79 Q22.5,2.07,22.5,2.48 L22.5,9.36 Q22.5,9.79,22.21,10.06 Q21.93,10.34,21.51,10.34 Q21.09,10.34,20.82,10.06 Q20.55,9.79,20.55,9.36 L20.55,4.83 L4.83,20.55 L9.36,20.55 Q9.79,20.55,10.06,20.84 Q10.34,21.12,10.34,21.54 Q10.34,21.95,10.06,22.23 Q9.79,22.5,9.36,22.5 Z"
)

// ---- Glass colours ----
private val AuroraBase = Color(0xFF070718)
private val GlassFill = Color.White.copy(alpha = 0.08f)
private val GlassBorder = Color.White.copy(alpha = 0.18f)
private val SubText = Color(0xFFB9B9D0)
private val GreenOk = Color(0xFF4ADE80)
private val AmberWarn = Color(0xFFFFC94D)
private val PurpleAccent = Color(0xFFA855F7)
private val BlueAccent = Color(0xFF38BDF8)
private val RenewGrad = listOf(Color(0xFF8B5CF6), Color(0xFFA855F7))

@Composable
private fun AuroraBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuroraBase)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Purple glow, top-left.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF7C3AED).copy(alpha = 0.85f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.05f, size.height * 0.02f),
                    radius = size.width * 0.8f
                ),
                radius = size.width * 0.8f,
                center = Offset(size.width * 0.05f, size.height * 0.02f)
            )
            // Blue glow, right.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2563EB).copy(alpha = 0.8f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.98f, size.height * 0.28f),
                    radius = size.width * 0.75f
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.98f, size.height * 0.28f)
            )
            // Magenta hint, middle.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFDB2777).copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.7f, size.height * 0.62f),
                    radius = size.width * 0.6f
                ),
                radius = size.width * 0.6f,
                center = Offset(size.width * 0.7f, size.height * 0.62f)
            )
            // Cyan glow, bottom-left.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0891B2).copy(alpha = 0.7f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.08f, size.height * 0.98f),
                    radius = size.width * 0.7f
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.08f, size.height * 0.98f)
            )
        }
        content()
    }
}

@Composable
private fun GlassBox(
    modifier: Modifier = Modifier,
    corner: Int = 24,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    var m = modifier
        .clip(RoundedCornerShape(corner.dp))
        .background(GlassFill)
        .border(1.dp, GlassBorder, RoundedCornerShape(corner.dp))
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(modifier = m, content = content)
}

@Composable
private fun GlassIconButton(
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    size: Int = 52
) {
    GlassBox(
        modifier = Modifier.size(size.dp),
        corner = size / 2,
        onClick = onClick
    ) {
        Image(
            imageVector = icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier
                .size(26.dp)
                .align(Alignment.Center)
        )
    }
}

@Composable
private fun PurpleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = PurpleAccent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFF6B7280)
        )
    )
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
    var releaseUrl by remember { mutableStateOf("https://github.com/BISHAL0012/Vcam-App/releases") }
    var keyMessage by remember { mutableStateOf<String?>(null) }
    var checkNonce by remember { mutableStateOf(0) }
    var showRenew by remember { mutableStateOf(false) }

    LaunchedEffect(checkNonce) {
        homeController.init()
        val result = KeyManager.check(context)
        keyState = result.state
        daysLeft = result.daysLeft
        timeLeft = result.timeLeft
        expiresAt = result.expiresAt
        telegramUrl = result.telegramUrl
        releaseUrl = result.releaseUrl
        imoUrl = result.imoUrl
        keyMessage = result.message
    }

    // ---- Key re-check while the app runs ----
    // The key used to be checked only on app start, so an expired key
    // kept working if the app stayed open/minimised. Re-check every
    // 60s while VALID: expired/revoked -> lock back to the key screen;
    // still valid -> refresh the time-left badge. A failed fetch
    // (NO_INTERNET) is ignored so a brief network blip does not
    // kick the user out; the next cycle retries.
    LaunchedEffect(keyState) {
        if (keyState != KeyManager.State.VALID) return@LaunchedEffect
        while (true) {
            delay(60_000L)
            val result = KeyManager.check(context)
            when (result.state) {
                KeyManager.State.VALID -> {
                    daysLeft = result.daysLeft
                    timeLeft = result.timeLeft
                    expiresAt = result.expiresAt
                    telegramUrl = result.telegramUrl
                    releaseUrl = result.releaseUrl
                    imoUrl = result.imoUrl
                }
                KeyManager.State.NEED_KEY,
                KeyManager.State.UPDATE_REQUIRED -> {
                    keyState = result.state
                    daysLeft = result.daysLeft
                    timeLeft = result.timeLeft
                    expiresAt = result.expiresAt
                    telegramUrl = result.telegramUrl
                    releaseUrl = result.releaseUrl
                    imoUrl = result.imoUrl
                    keyMessage = result.message
                    break
                }
                else -> {
                    // NO_INTERNET / CHECKING: keep running, retry next cycle.
                }
            }
        }
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
            AuroraBackground {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    CircularProgressIndicator(color = PurpleAccent)
                    Spacer(Modifier.height(16.dp))
                    Text("Checking key...", color = Color.White, fontSize = 16.sp)
                }
            }
            return
        }
        KeyManager.State.NO_INTERNET -> {
            AuroraBackground {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                ) {
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
            AuroraBackground {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                ) {
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
                    Button(
                        onClick = { openUrl(releaseUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5CF6)
                        )
                    ) { Text("Download Update") }
                    Spacer(Modifier.height(12.dp))
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
            AuroraBackground {
                Text(
                    "Vcam",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            KeyDialog(
                telegramUrl = telegramUrl,
                imoUrl = imoUrl,
                onActivated = { result ->
                    daysLeft = result.daysLeft
                    timeLeft = result.timeLeft
                    expiresAt = result.expiresAt
                    telegramUrl = result.telegramUrl
                    releaseUrl = result.releaseUrl
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

    val videoOn = homeController.isVideoEnabled.value
    val volumeOn = homeController.isVolumeEnabled.value
    val softOn = !homeController.codecType.value

    fun toggleVideo() {
        val on = !homeController.isVideoEnabled.value
        homeController.isVideoEnabled.value = on
        homeController.saveState()
        Toast.makeText(
            context,
            if (on) "Video ON" else "Video OFF",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun toggleVolume() {
        val on = !homeController.isVolumeEnabled.value
        homeController.isVolumeEnabled.value = on
        homeController.saveState()
        Toast.makeText(
            context,
            if (on) "Volume ON" else "Volume OFF",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun setSoftDecoding(soft: Boolean) {
        if (!soft) {
            // Switching to Hard Decoding: needs hardware support.
            if (homeController.isH264HardwareDecoderSupport()) {
                homeController.codecType.value = true
                homeController.saveState()
                Toast.makeText(context, "Hard Decoding ON", Toast.LENGTH_SHORT).show()
            } else {
                homeController.codecType.value = false
                Toast.makeText(
                    context,
                    "Hard decoding not supported",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            homeController.codecType.value = false
            homeController.saveState()
            Toast.makeText(context, "Soft Decoding ON", Toast.LENGTH_SHORT).show()
        }
    }

    AuroraBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: iPhone-style rounded-square logo + Vcam + crown/settings.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
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
                GlassIconButton(
                    icon = IconCrown,
                    tint = Color(0xFFFACC15),
                    onClick = { showRenew = true }
                )
                Spacer(Modifier.width(10.dp))
                GlassIconButton(
                    icon = IconSettings,
                    tint = Color.White,
                    onClick = {
                        Toast.makeText(
                            context,
                            "Settings coming soon",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            // License pill: days left + active state.
            GlassBox(
                modifier = Modifier.fillMaxWidth(),
                corner = 28,
                onClick = { showRenew = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Image(
                        imageVector = IconClock,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(PurpleAccent),
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (timeLeft.isNotBlank()) timeLeft
                                else if (daysLeft >= 0) "$daysLeft days left" else "Key active",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Your license is active",
                            fontSize = 14.sp,
                            color = SubText
                        )
                    }
                    GlassBox(modifier = Modifier.size(36.dp), corner = 18) {
                        Image(
                            imageVector = IconChevron,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.Center)
                        )
                    }
                }
            }

            // Select Video hero card.
            GlassBox(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    requestPermissionLauncher.launch(
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(128.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFA855F7),
                                        Color(0xFF2563EB),
                                        Color(0xFF06B6D4)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            imageVector = IconVideocam,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.size(60.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Select Video",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Choose your video file",
                        fontSize = 14.sp,
                        color = SubText
                    )
                }
                GlassBox(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.CenterEnd),
                    corner = 20
                ) {
                    Image(
                        imageVector = IconChevron,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            // 2x2 glass cards: View Video / Video / Volume / Soft Decoding.
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(IntrinsicSize.Max)
            ) {
                // View Video.
                GlassBox(
                    modifier = Modifier.weight(1f),
                    onClick = { homeController.isVideoDisplay.value = true }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Image(
                            imageVector = IconEye,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(BlueAccent),
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "View Video",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Watch & Preview",
                            fontSize = 13.sp,
                            color = SubText
                        )
                    }
                    GlassBox(
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.CenterEnd),
                        corner = 18
                    ) {
                        Image(
                            imageVector = IconChevron,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.Center)
                        )
                    }
                }
                // Video On/Off.
                GlassBox(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Image(
                            imageVector = IconVideocam,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(PurpleAccent),
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Video " + if (videoOn) "On" else "Off",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Start video feed",
                            fontSize = 13.sp,
                            color = SubText
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            PurpleSwitch(
                                checked = videoOn,
                                onCheckedChange = { toggleVideo() }
                            )
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(IntrinsicSize.Max)
            ) {
                // Volume On/Off.
                GlassBox(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Image(
                            imageVector = IconVolume,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(PurpleAccent),
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Volume " + if (volumeOn) "On" else "Off",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Enable audio",
                            fontSize = 13.sp,
                            color = SubText
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            PurpleSwitch(
                                checked = volumeOn,
                                onCheckedChange = { toggleVolume() }
                            )
                        }
                    }
                }
                // Soft Decoding.
                GlassBox(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Image(
                            imageVector = IconLayers,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(PurpleAccent),
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Soft Decoding",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Improve performance",
                            fontSize = 13.sp,
                            color = SubText
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            PurpleSwitch(
                                checked = softOn,
                                onCheckedChange = { setSoftDecoding(it) }
                            )
                        }
                    }
                }
            }

            // Now Playing glass card.
            GlassBox(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PurpleAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                imageVector = IconPlay,
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(Color.White),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Now Playing",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Image(
                            imageVector = IconExpand,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
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
                                imageVector = if (videoOn) IconPause else IconPlay,
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(Color.White),
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            imageVector = IconFilm,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
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
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (videoOn) GreenOk else SubText
                                )
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = when {
                                currentVideoUrl.isNullOrBlank() -> ""
                                videoOn -> "Playing"
                                else -> "Paused"
                            },
                            fontSize = 14.sp,
                            color = if (videoOn) GreenOk else SubText
                        )
                    }
                }
            }

            // Key bar: expiry + Renew button.
            GlassBox(
                modifier = Modifier.fillMaxWidth(),
                corner = 28
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_key),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(PurpleAccent),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(GlassBorder)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Key valid until ${expiresAt?.let { KeyManager.displayExpiry(it) } ?: "—"}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Renew: Telegram / imo",
                            fontSize = 14.sp,
                            color = if (daysLeft in 0..3) AmberWarn else SubText
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.horizontalGradient(RenewGrad))
                            .clickable { showRenew = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                imageVector = IconSend,
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(Color.White),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Renew",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    // Renew dialog: pick Telegram or imo to contact for a new key.
    if (showRenew) {
        AlertDialog(
            onDismissRequest = { showRenew = false },
            title = { Text("Renew key") },
            text = { Text("Contact to renew your key:") },
            confirmButton = {
                TextButton(onClick = {
                    showRenew = false
                    openUrl(telegramUrl)
                }) { Text("Telegram") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenew = false
                    openUrl(imoUrl)
                }) { Text("imo") }
            }
        )
    }

    VideoPlayerDialog(homeController)
}
