package com.fiesta.opencodeservermanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ManagerScreen() } }
    }
}

@Composable
fun ManagerScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("manager", Context.MODE_PRIVATE) }
    var workerUrl by remember { mutableStateOf(prefs.getString("worker", "") ?: "") }
    var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
    var status by remember { mutableStateOf("Not checked") }
    var detail by remember { mutableStateOf("Enter your Worker URL and manager token.") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val base = workerUrl.trim().trimEnd('/')
    val serverAddress = base + "/opencode"

    fun checkStatus() {
        if (base.isBlank() || token.isBlank()) {
            status = "Configuration required"
            detail = "Enter your Worker URL and manager token."
            return
        }
        busy = true
        status = "Checking…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val connection = URL(base + "/api/status").openConnection() as HttpURLConnection
                    connection.connectTimeout = 8000
                    connection.readTimeout = 10000
                    connection.setRequestProperty("Authorization", "Bearer " + token)
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
                    connection.disconnect()
                    Pair(code, body)
                } catch (e: Exception) { Pair(-1, e.message ?: "Connection failed") }
            }
            busy = false
            status = when (result.first) {
                in 200..299 -> "Worker reachable"
                401 -> "Unauthorized"
                503 -> "Worker not configured"
                -1 -> "Connection failed"
                else -> "Runtime check returned " + result.first
            }
            detail = result.second.take(500)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("OpenCode Manager", style = MaterialTheme.typography.headlineMedium)
            Text("Manage your hosted OpenCode connection.", style = MaterialTheme.typography.bodyMedium)
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CONNECTION STATUS", style = MaterialTheme.typography.labelMedium)
                    Text(status, style = MaterialTheme.typography.titleLarge)
                    Text(detail, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { checkStatus() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "Checking…" else "Check connection")
                    }
                }
            }
            OutlinedTextField(
                value = workerUrl, onValueChange = { workerUrl = it },
                label = { Text("Cloudflare Worker URL") },
                placeholder = { Text("https://your-worker.username.workers.dev") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            OutlinedTextField(
                value = token, onValueChange = { token = it },
                label = { Text("Manager API token") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Button(onClick = {
                prefs.edit().putString("worker", base).putString("token", token).apply()
                status = "Saved"
                detail = "Settings saved on this device."
            }, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("OPENCODE SERVER ADDRESS", style = MaterialTheme.typography.labelMedium)
                    Text(if (base.isBlank()) "Not configured" else serverAddress)
                    OutlinedButton(onClick = {
                        if (base.isNotBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("OpenCode URL", serverAddress))
                            status = "Address copied"
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Copy server address") }
                    Button(onClick = {
                        if (base.isNotBlank())
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(serverAddress)))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Open server address") }
                }
            }
            Text(
                "The Worker manages the connection. OpenCode must run on a separate always-on host.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
