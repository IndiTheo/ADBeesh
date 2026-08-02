package com.indidevs.android.adbeesh.test.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.indidevs.android.adbeesh.config.AdbSessionConfig
import com.indidevs.android.adbeesh.notifications.NotificationCallback
import com.indidevs.android.adbeesh.notifications.NotificationCodeProvider
import com.indidevs.android.adbeesh.notifications.NotificationFlowConfig
import com.indidevs.android.adbeesh.pairing.AdbPairing
import com.indidevs.android.adbeesh.pairing.AdbPairingWorkflow
import com.indidevs.android.adbeesh.test.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SessionTab(
    id: String,
    pairing: AdbPairing,
    out: String,
    onOut: (String) -> Unit,
    scope: CoroutineScope,
    ctx: Context
) {
    val status by pairing.status.collectAsState()
    var useCustomConfig by remember { mutableStateOf(true) }

    val (sessionConfig, flowConfig) = remember(id, useCustomConfig) {
        val appTitle = "ADBeesh $id"
        Pair(
            AdbSessionConfig(id),
            if (useCustomConfig) {
                NotificationFlowConfig.Builder()
                    .common {
                        title = "$appTitle | $title"
                        smallIconRes = android.R.drawable.ic_dialog_info
                    }
                    .searching {
                        text = "Looking for your device..."
                    }
                    .awaitingCode { update ->
                        text = "Device found on port ${update.port}. Enter pairing code:"
                    }
                    .error { update ->
                        text = "Failed: ${update.error ?: "Unknown error"}"
                        smallIconRes = android.R.drawable.ic_dialog_alert
                    }
                    .success { title = "Pairing Complete!" }
                    .build()
            } else {
                NotificationFlowConfig() // Completely default
            }
        )
    }

    LaunchedEffect(status) { Log.i(Constants.TAG, "[$id] ${status.name}") }

    Column(Modifier.fillMaxSize().padding(16.dp), Arrangement.spacedBy(8.dp)) {
        Text("ID: $id | Status: ${status.name}", style = MaterialTheme.typography.headlineSmall)
        mapOf(
            "Pair" to {
                scope.launch {
                    runCatching {
                        val workflow = AdbPairingWorkflow.Builder()
                            .withCodeProvider(NotificationCodeProvider(ctx, flowConfig))
                            .addCallback(NotificationCallback(ctx, flowConfig))
                            .build()
                        pairing.ensurePaired(workflow)
                    }.onFailure { onOut("Err: ${it.message}") }
                }
            },
            "Open Session" to {
                onOut("Connecting...")
                scope.launch {
                    runCatching {
                        pairing.openSession().use {
                            onOut(it.shell("echo 'Success $id'").output)
                        }
                    }.onFailure { onOut("Err: ${it.message}") }
                }
            },
            "Unpair" to {
                pairing.unpair()
                onOut("Unpaired")
            }
        ).forEach { (txt, act) -> Button({ act() }, Modifier.fillMaxWidth()) { Text(txt) } }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Use Custom Notification Config", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = useCustomConfig,
                onCheckedChange = { useCustomConfig = it }
            )
        }

        if (out.isNotEmpty()) Text("Out: $out", Modifier.padding(top = 16.dp))
    }
}
