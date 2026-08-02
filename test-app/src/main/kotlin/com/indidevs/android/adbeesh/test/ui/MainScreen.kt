package com.indidevs.android.adbeesh.test.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.indidevs.android.adbeesh.config.AdbSessionConfig
import com.indidevs.android.adbeesh.pairing.AdbPairing

@Composable
fun MainScreen(context: Context) {
    val sessions = remember { mutableStateListOf("adbeesh-test-1") }
    val pairings = remember { mutableStateMapOf<String, AdbPairing>() }
    val outputs = remember { mutableStateMapOf<String, String>() }
    var selectedIdx by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val activeIdx = if (sessions.isEmpty()) 0 else selectedIdx.coerceIn(0, sessions.size - 1)

    val pLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            pLauncher.launch(
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (sessions.isNotEmpty()) {
                key(sessions.size) {
                    ScrollableTabRow(
                        selectedTabIndex = activeIdx,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        edgePadding = 0.dp,
                        divider = {}
                    ) {
                        sessions.forEachIndexed { i, id ->
                            Tab(activeIdx == i, { selectedIdx = i }, text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(id, style = MaterialTheme.typography.labelLarge)
                                    IconButton(
                                        {
                                            pairings[id]?.unpair()
                                            listOf(pairings, outputs).forEach { it.remove(id) }
                                            sessions.removeAt(i)
                                            selectedIdx = (sessions.size - 1).coerceAtLeast(0)
                                        },
                                        Modifier.size(
                                            24.dp
                                        )
                                    ) { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
                                }
                            })
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton({
                val next = sessions.mapNotNull {
                    it.substringAfterLast(
                        "-"
                    ).toIntOrNull()
                }.maxOrNull()?.inc() ?: 1
                "adbeesh-test-$next".let {
                    sessions.add(it)
                    selectedIdx = sessions.size - 1
                }
            }) { Icon(Icons.Default.Add, null) }
        }
        if (sessions.isNotEmpty()) {
            sessions[activeIdx].let { id ->
                Box(Modifier.weight(1f)) {
                    key(id) {
                        val pairing =
                            remember {
                                pairings.getOrPut(
                                    id
                                ) {
                                    AdbPairing.get(
                                        context,
                                        id,
                                        AdbSessionConfig(identityName = id)
                                    )
                                }
                            }
                        SessionTab(
                            id,
                            pairing,
                            outputs[id] ?: "",
                            { outputs[id] = it },
                            scope,
                            context
                        )
                    }
                }
            }
        }

        Notice()
    }
}

@Composable
private fun Notice() {
    val uriHandler = LocalUriHandler.current
    val annotatedString = buildAnnotatedString {
        append(
            "This application is intended for internal end-to-end testing. For library integration guides and source code, please visit: "
        )
        pushStringAnnotation(tag = "URL", annotation = "https://gitlab.com/IndiDevs/adbeesh")
        withStyle(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append("https://gitlab.com/IndiDevs/adbeesh")
        }
        pop()
    }

    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        ),
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    uriHandler.openUri(annotation.item)
                }
        }
    )
}
