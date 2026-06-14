package com.gibilator.gmg.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gibilator.gmg.ui.grills.GrillsScreen
import com.gibilator.gmg.ui.home.HomeScreen
import com.gibilator.gmg.ui.onboarding.OnboardingScreen
import com.gibilator.gmg.ui.settings.SettingsScreen
import com.gibilator.gmg.ui.wizard.NewCookScreen
import com.gibilator.gmg.vm.GrillViewModel

private object Routes {
    const val ONBOARD = "onboarding"
    const val HOME = "home"
    const val NEWCOOK = "newcook"
    const val GRILLS = "grills"
    const val SETTINGS = "settings"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Cook", Icons.Rounded.Home),
    Tab(Routes.NEWCOOK, "New Cook", Icons.Rounded.RestaurantMenu),
    Tab(Routes.GRILLS, "Grills", Icons.Rounded.Wifi),
    Tab(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
)

@Composable
fun GmgNav(vm: GrillViewModel) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val discovery by vm.discovery.collectAsStateWithLifecycle()

    if (prefs == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val start = if (prefs!!.onboardingDone) Routes.HOME else Routes.ONBOARD

    Scaffold(
        bottomBar = {
            if (currentRoute != null && currentRoute != Routes.ONBOARD) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = start, modifier = Modifier.padding(inner)) {
            composable(Routes.ONBOARD) {
                OnboardingScreen(onGetStarted = {
                    vm.completeOnboarding()
                    nav.navigate(Routes.GRILLS) { popUpTo(Routes.ONBOARD) { inclusive = true } }
                })
            }
            composable(Routes.HOME) {
                HomeScreen(
                    state = state,
                    prefs = prefs,
                    onPowerOn = vm::powerOn,
                    onPowerOff = vm::powerOff,
                    onColdSmoke = vm::coldSmoke,
                    onSetGrillTemp = vm::setGrillTemp,
                    onSetProbeTarget = vm::setProbeTarget,
                    onMeatOn = vm::markMeatOn,
                    onAbort = vm::abortCook,
                    onNewCook = { nav.navigate(Routes.NEWCOOK) },
                    onGoToGrills = { nav.navigate(Routes.GRILLS) },
                )
            }
            composable(Routes.NEWCOOK) {
                NewCookScreen(
                    prefs = prefs,
                    preview = preview,
                    onPreview = vm::preview,
                    onClearPreview = vm::clearPreview,
                    onStart = { meat, weight, probe, mode, finish -> vm.startCook(meat, weight, probe, mode, finish) },
                    onDone = { nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
                )
            }
            composable(Routes.GRILLS) {
                GrillsScreen(
                    discovery = discovery,
                    onRunDiscovery = vm::runDiscovery,
                    onConnectHost = { host ->
                        vm.connectTo(host)
                        nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    prefs = prefs,
                    onScanInterval = vm::setScanInterval,
                    onMaxPit = vm::setMaxPit,
                    onAutoCook = vm::setAutoCook,
                    onPush = vm::setPush,
                    onDevMode = vm::setDevMode,
                    onTempUnit = vm::setTempUnit,
                    onWeightUnit = vm::setWeightUnit,
                )
            }
        }
    }
}
