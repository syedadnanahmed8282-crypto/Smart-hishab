package com.smarthisab.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.smarthisab.app.data.AuthState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: String)

@Composable
fun AppRoot(vm: AppVm) {
    val nav = rememberNavController()
    val auth by vm.auth.collectAsState()
    val guest by vm.guest.collectAsState()
    val host = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            launch {
                val timer = launch { delay(m.ms); host.currentSnackbarData?.dismiss() }
                val r = host.showSnackbar(m.text, m.action, duration = SnackbarDuration.Indefinite)
                timer.cancel()
                if (r == SnackbarResult.ActionPerformed) m.onAction?.invoke()
            }
        }
    }

    val loading = auth == AuthState.Loading && !guest
    val loggedOut = auth == AuthState.Out && !guest
    if (loading) {
        Box(Modifier.fillMaxSize().background(Sky700), contentAlignment = Alignment.Center) {
            Text("৳", color = Color.White, fontSize = androidx.compose.ui.unit.TextUnit(64f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Bold)
        }
        return
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val authRoutes = setOf("login", "register", "forgot")
    LaunchedEffect(loggedOut, route) {
        if (loggedOut && route != null && route !in authRoutes) nav.navigate("login") { popUpTo(0) }
        if (!loggedOut && route in authRoutes) nav.navigate("home") { popUpTo(0) }
    }

    val tabs = listOf(
        Tab("home", "আজ", Ic.home), Tab("history", "ইতিহাস", Ic.clock),
        Tab("report", "রিপোর্ট", Ic.chart), Tab("settings", "সেটিংস", Ic.sliders),
    )
    Scaffold(
        containerColor = Sky50,
        snackbarHost = {
            SnackbarHost(host) { d ->
                Snackbar(
                    Modifier.padding(12.dp), containerColor = Navy, contentColor = Color.White, shape = RoundedCornerShape(16.dp),
                    action = if (d.visuals.actionLabel == null) null else ({
                        Row {
                            TextButton({ d.dismiss() }) { Text("বাতিল", color = Sky200) }
                            TextButton({ d.performAction() }) { Text(d.visuals.actionLabel ?: "", color = Sky300, fontWeight = FontWeight.Bold) }
                        }
                    }),
                ) { Text(d.visuals.message) }
            }
        },
        bottomBar = {
            if (route in tabs.map { it.route }) {
                NavigationBar(containerColor = Color.White) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { nav.navigate(t.route) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } },
                            icon = { Ico(t.icon, size = 24.dp, tint = if (route == t.route) Sky700 else Slate) }, label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Sky700, selectedTextColor = Sky700, indicatorColor = Sky100),
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            nav, if (loggedOut) "login" else "home", Modifier.padding(bottom = pad.calculateBottomPadding()),
            enterTransition = { fadeIn(tween(320)) + slideInHorizontally(tween(380)) { it / 14 } },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(320)) + slideInHorizontally(tween(380)) { -it / 14 } },
            popExitTransition = { fadeOut(tween(180)) },
        ) {
            composable("login") { LoginScreen(vm, nav) }
            composable("register") { RegisterScreen(vm, nav) }
            composable("forgot") { ForgotScreen(vm, nav) }
            composable("home") { HomeScreen(vm, nav) }
            composable("history") { HistoryScreen(vm, nav) }
            composable("report") { ReportScreen(vm, nav) }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("entry?id={id}", listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                EntryScreen(vm, nav, it.arguments?.getString("id"))
            }
            composable("folders") { FoldersScreen(vm, nav) }
            composable("newfolder") { NewFolderScreen(vm, nav) }
        }
    }
}

