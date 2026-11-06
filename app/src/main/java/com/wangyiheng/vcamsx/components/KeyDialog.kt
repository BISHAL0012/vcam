package com.wangyiheng.vcamsx.components

@file:OptIn(ExperimentalMaterial3Api::class)

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wangyiheng.vcamsx.utils.KeyManager
import kotlinx.coroutines.launch

/**
 * Blocking key dialog (Vcam key system). Shown when no valid stored key
 * exists. Cannot be dismissed without a valid key: the user either
 * activates a key or contacts the seller (Telegram / imo) to buy one.
 * Contact URLs come from the server config, so the seller can change
 * them any time without a new build.
 */
@Composable
fun KeyDialog(
    telegramUrl: String,
    imoUrl: String,
    onActivated: (KeyManager.CheckResult) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var keyText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var activating by remember { mutableStateOf(false) }
    val androidId = remember { KeyManager.androidId(context) }

    fun openUrl(url: String) {
        if (url.isBlank()) return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        // Explicit dark surface: the dialog must look the same on light-
        // and dark-theme phones (the text below is hardcoded white).
        Surface(
            color = Color(0xFF1A1D30),
            shape = RoundedCornerShape(24.dp)
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Enter Key",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            OutlinedTextField(
                value = keyText,
                onValueChange = { keyText = it; error = null },
                placeholder = { Text("Enter your key...", color = Color(0xFF8E8E9E)) },
                singleLine = true,
                // TextFieldDefaults.outlinedTextFieldColors (not the newer
                // OutlinedTextFieldDefaults.colors) — the project pins
                // compose-bom 2023.03.00 / material3 1.1.x, where the
                // newer colors() API does not exist yet.
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White,
                    containerColor = Color.Transparent,
                    cursorColor = Color.White,
                    focusedBorderColor = Color(0xFFA855F7),
                    unfocusedBorderColor = Color(0xFF555560),
                    placeholderColor = Color(0xFF8E8E9E)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Your Android ID  (tap to copy)",
                fontSize = 14.sp,
                color = Color(0xFFB0B0C0)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        cm.setPrimaryClip(
                            ClipData.newPlainText("Android ID", androidId)
                        )
                        Toast.makeText(context, "Android ID copied", Toast.LENGTH_SHORT).show()
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = androidId,
                    fontSize = 15.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "Copy", fontSize = 14.sp, color = Color(0xFFA855F7))
            }
            if (error != null) {
                Text(text = error!!, fontSize = 14.sp, color = Color(0xFFFF6B6B))
            }
            Text(
                text = "Buy key — contact Telegram, imo",
                fontSize = 14.sp,
                color = Color(0xFFB0B0C0)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { openUrl(telegramUrl) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2DA8E0)
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text("Telegram") }
                Button(
                    onClick = { openUrl(imoUrl) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00B964)
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text("imo") }
            }
            Button(
                onClick = {
                    if (keyText.isBlank() || activating) return@Button
                    activating = true
                    error = null
                    scope.launch {
                        val result = KeyManager.activate(context, keyText)
                        activating = false
                        if (result.state == KeyManager.State.VALID) {
                            onActivated(result)
                        } else {
                            error = result.message ?: "Invalid or expired key."
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7C3AED)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (activating) "Checking..." else "Activate", fontSize = 18.sp)
            }
        }
        }
    }
}
