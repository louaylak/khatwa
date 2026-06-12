package com.khatwa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.khatwa.app.data.Prefs
import com.khatwa.app.data.ProfileStore
import com.khatwa.app.tracking.TrackStatus
import com.khatwa.app.tracking.TrackingManager
import com.khatwa.app.ui.Ads
import com.khatwa.app.ui.DetailScreen
import com.khatwa.app.ui.Ember
import com.khatwa.app.ui.EmberDeep
import com.khatwa.app.ui.EmberGlow
import com.khatwa.app.ui.HistoryScreen
import com.khatwa.app.ui.KhatwaTheme
import com.khatwa.app.ui.Muted
import com.khatwa.app.ui.NightBg
import com.khatwa.app.ui.ProfileEditScreen
import com.khatwa.app.ui.ProfilePickerScreen
import com.khatwa.app.ui.RecordScreen
import com.khatwa.app.ui.Sand
import com.khatwa.app.ui.StatsScreen
import com.khatwa.app.ui.Surface1
import com.khatwa.app.ui.Surface2

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(NightBg.toArgb())
        )
        super.onCreate(savedInstanceState)
        setContent {
            KhatwaTheme {
                var gateDone by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    // App-open interstitial: shows once if available, never blocks
                    // more than ~4.5 s on a bad connection.
                    try {
                        Ads.awaitOpenAd(this@MainActivity)
                    } catch (_: Exception) { }
                    gateDone = true
                }
                if (gateDone) AppRoot() else SplashGate()
            }
        }
    }
}

@Composable
private fun SplashGate() {
    val inf = rememberInfiniteTransition(label = "splash")
    val a by inf.animateFloat(
        0.35f, 1f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "blink"
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(NightBg, EmberDeep.copy(alpha = 0.35f), NightBg))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Khatwa", style = MaterialTheme.typography.displayMedium, color = Ember)
            Text("خطوة", style = MaterialTheme.typography.titleLarge, color = EmberGlow)
            Spacer(Modifier.height(18.dp))
            Text(
                "loading…",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
                modifier = Modifier.alpha(a)
            )
        }
    }
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }

    val start = remember {
        val hasProfile = prefs.activeProfileId?.let { ProfileStore(ctx).get(it) } != null
        if (hasProfile) "main" else "profiles"
    }

    NavHost(navController = nav, startDestination = start) {

        composable("profiles") {
            ProfilePickerScreen(
                onPicked = { p ->
                    prefs.activeProfileId = p.id
                    nav.navigate("main") { popUpTo(0) { inclusive = true } }
                },
                onAdd = { nav.navigate("edit") },
                onEdit = { p -> nav.navigate("edit?id=${p.id}") }
            )
        }

        composable(
            route = "edit?id={id}",
            arguments = listOf(navArgument("id") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { back ->
            ProfileEditScreen(
                existingId = back.arguments?.getString("id"),
                onDone = {
                    nav.navigate("main") { popUpTo(0) { inclusive = true } }
                },
                onBack = { nav.popBackStack() }
            )
        }

        composable("main") {
            MainScaffold(nav)
        }

        composable(
            route = "detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { back ->
            val id = back.arguments?.getString("id") ?: return@composable
            DetailScreen(activityId = id, onBack = { nav.popBackStack() })
        }
    }
}

@Composable
fun MainScaffold(nav: NavHostController) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val activeId = prefs.activeProfileId
    val profile = remember(activeId) { activeId?.let { ProfileStore(ctx).get(it) } }

    if (profile == null) {
        LaunchedEffect(Unit) {
            nav.navigate("profiles") { popUpTo(0) { inclusive = true } }
        }
        return
    }

    val live by TrackingManager.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val hideBar = live.status != TrackStatus.IDLE

    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Ember,
        selectedTextColor = Ember,
        indicatorColor = Surface2,
        unselectedIconColor = Muted,
        unselectedTextColor = Muted
    )

    Scaffold(
        containerColor = NightBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!hideBar) {
                NavigationBar(containerColor = Surface1) {
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Filled.RadioButtonChecked, contentDescription = "Record") },
                        label = { Text("Record") }, colors = itemColors
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Filled.History, contentDescription = "History") },
                        label = { Text("History") }, colors = itemColors
                    )
                    NavigationBarItem(
                        selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.Filled.BarChart, contentDescription = "Stats") },
                        label = { Text("Stats") }, colors = itemColors
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> RecordScreen(
                    profile = profile,
                    onOpenProfiles = { nav.navigate("profiles") },
                    onSaved = { id -> nav.navigate("detail/$id") }
                )
                1 -> HistoryScreen(profile.id, onOpen = { nav.navigate("detail/$it") })
                else -> StatsScreen(profile)
            }
        }
    }
}
