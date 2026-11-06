package com.wangyiheng.vcamsx.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.wangyiheng.vcamsx.MainActivity

@Composable
fun DisclaimerDialog() {
    var showDialog by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val shengm = "Disclaimer\n" +
            "About this app\n" +
            "   This app replaces camera data. You can change the image and video sent through the camera.\n" +
            "\n" +
            "Terms of use\n" +
            "   You are responsible for all data and images you upload, modify or share through this app.\n" +
            "\n" +
            "   Use this app only for lawful purposes. Do not use it for any illegal or unauthorised activity.\n" +
            "\n" +
            "   Respect other people's privacy. Do not collect, process or share anyone's personal information without their clear permission.\n" +
            "\n" +
            "   The developer is not responsible for any direct or indirect consequences of your use of this app. You use it at your own risk."

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
            },
            title = {
                Text(text = "Disclaimer")
            },
            text = {
                Box(modifier = Modifier.heightIn(max = 300.dp)) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        shengm.split("\n").forEach { line ->
                            Text(text = line)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDialog = false
                    }
                ) {
                    Text("I agree")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        closeApp(context)
                    }
                ) {
                    Text("I disagree")
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        )
    }
}

private fun closeApp(context: Context) {
    if (context is MainActivity) {
        context.finishAffinity()
    }
}
