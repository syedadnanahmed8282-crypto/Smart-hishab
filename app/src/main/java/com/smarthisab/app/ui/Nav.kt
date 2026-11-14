package com.smarthisab.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.smarthisab.app.data.AuthState
import com.smarthisab.app.domain.Mode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: String)

/** নিচের ৪ ট্যাবের যেকোনোটায় যাওয়া। স্ট্যাকে সবসময় [home, ট্যাব], তাই পেছনে গেলে হোমে ফেরা যায়। */
fun NavHostController.goTab(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo("home") { inclusive = route == "home" }
        launchSingleTop = true
    }
}

@Composable
fun AppRoot(vm: AppVm) {
    val nav = rememberNavController()
    val auth by vm.auth.collectAsState()
    val guest by vm.guest.collectAsState()
    val signedLocal by vm.signedLocal.collectAsState()
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

    // অফলাইনেও অ্যাপ চলবে: আগে লগইন করা থাকলে বা লগইন ছাড়া মোডে থাকলে সরাসরি ভেতরে ঢোকে
    val ready = guest != null && signedLocal != null
    val inApp = guest == true || signedLocal == true || auth == AuthState.In
    val loading = !ready || (!inApp && auth == AuthState.Loading)
    val loggedOut = !inApp && auth == AuthState.Out

    if (loading) {
        Box(Modifier.fillMaxSize().background(Sky700), contentAlignment = Alignment.Center) {
            Text("৳", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
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
    val isTab = route in tabs.map { it.route }
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    BackHandler(enabled = isTab && route != "home") { nav.goTab("home") }

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
            if (isTab && !imeOpen) {
                NavigationBar(containerColor = Color.White) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { nav.goTab(t.route) },
                            icon = { Ico(t.icon, size = 24.dp, tint = if (route == t.route) Sky700 else Slate) }, label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Sky700, selectedTextColor = Sky700, indicatorColor = Sky100),
                        )
                    }
                }
            }
        },
    ) { pad ->
        val bottom = pad.calculateBottomPadding()
        NavHost(
            nav, if (loggedOut) "login" else "home",
            Modifier.padding(bottom = bottom).consumeWindowInsets(PaddingValues(bottom = bottom)).imePadding(),
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
                val mode by vm.mode.collectAsState()
                if (mode == Mode.MAIN) EntryScreen(vm, nav, it.arguments?.getString("id")) else MoneyEntryScreen(vm, nav, it.arguments?.getString("id"))
            }
            composable("folders") { FoldersScreen(vm, nav) }
            composable("newfolder") { NewFolderScreen(vm, nav) }
        }
    }
}
