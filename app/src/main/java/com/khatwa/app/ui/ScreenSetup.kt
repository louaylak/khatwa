package com.khatwa.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.khatwa.app.data.Prefs
import com.khatwa.app.map.MapStore
import kotlinx.coroutines.launch

@Composable
fun MapSetupScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()

    var dl by remember { mutableStateOf<MapStore.DL>(MapStore.DL.Idle) }
    var ready by remember { mutableStateOf(MapStore.isReady(ctx)) }
    var importing by remember { mutableStateOf(false) }
    var importFailed by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true; importFailed = false
                val ok = MapStore.importFrom(ctx, uri)
                importing = false
                if (ok) { ready = true; prefs.mapPromptDone = true } else importFailed = true
            }
        }
    }

    val busy = importing || dl is MapStore.DL.Running

    Column(
        Modifier
            .fillMaxSize()
            .background(NightBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .size(92.dp)
                .background(Brush.radialGradient(listOf(EmberGlow, Ember, EmberDeep)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (ready) Icons.Filled.Check else Icons.Filled.Map,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color(0xFF1A0D04),
                modifier = Modifier.size(46.dp)
            )
        }
        Spacer(Modifier.height(22.dp))
        Text("Offline map of Algeria", style = MaterialTheme.typography.headlineMedium, color = Sand)
        Spacer(Modifier.height(10.dp))
        Text(
            if (ready)
                "The detailed map is installed. Every street, side road and footpath in Algeria now works with no internet at all."
            else
                "One download (~${MapStore.APPROX_MB} MB) gives you the full OpenStreetMap of Algeria — every street, side road and footpath — rendered on your phone with zero internet afterwards. Wi-Fi recommended. The download resumes if interrupted.",
            style = MaterialTheme.typography.bodyLarge,
            color = Muted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(30.dp))

        when (val d = dl) {
            is MapStore.DL.Running -> {
                LinearProgressIndicator(
                    progress = { d.pct / 100f },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = Ember,
                    trackColor = Surface2
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "${d.pct}%  ·  ${d.doneMb} / ${d.totalMb} MB",
                    style = MaterialTheme.typography.titleMedium,
                    color = Sand
                )
                Spacer(Modifier.height(6.dp))
                Text("Keep the app open while downloading", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            is MapStore.DL.Error -> {
                Text("Download failed: ${d.msg}", color = Danger, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text("Trying again continues from where it stopped.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(14.dp))
            }
            else -> {}
        }
        if (importing) {
            Text("Importing map file…", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        if (importFailed) {
            Text("That file does not look like a valid algeria.map", color = Danger, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(14.dp))

        if (ready) {
            Button(
                onClick = { prefs.mapPromptDone = true; onDone() },
                colors = ButtonDefaults.buttonColors(containerColor = Ember),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("Continue", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Button(
                onClick = {
                    if (!busy) scope.launch {
                        MapStore.download(ctx).collect { state ->
                            dl = state
                            if (state is MapStore.DL.Done) {
                                ready = true
                                prefs.mapPromptDone = true
                            }
                        }
                    }
                },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Ember),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (dl is MapStore.DL.Error) "Retry download" else "Download map",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { if (!busy) importLauncher.launch(arrayOf("*/*")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Sand)
                Spacer(Modifier.width(8.dp))
                Text("Import algeria.map from storage", color = Sand)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { if (!busy) { prefs.mapPromptDone = true; onDone() } },
                enabled = !busy
            ) {
                Text("Use online maps for now", color = Muted)
            }
        }

        Spacer(Modifier.height(26.dp))
        Text(
            "Map data © OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall,
            color = Muted.copy(alpha = 0.7f)
        )
    }
}
