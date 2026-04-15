package com.grainbeaute.androidweb.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.grainbeaute.androidweb.data.LocalRepository
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController, repository: LocalRepository) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var identificationBadgeCount by remember { mutableStateOf(0) }

    // Polling du badge d'identification indépendant de l'onglet actif
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val captures = repository.getUnassignedCaptures()
                identificationBadgeCount = captures.count { it.status == "done" }
            } catch (e: Exception) {
                // Silencieusement ignorer les erreurs de polling
            }
            delay(5000)
        }
    }

    // Refresh immédiat du badge à chaque changement de navigation (ex: retour depuis la caméra)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry) {
        try {
            val captures = repository.getUnassignedCaptures()
            identificationBadgeCount = captures.count { it.status == "done" }
        } catch (e: Exception) {
            // Ignorer
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF007AFF),
                        selectedTextColor = Color(0xFF007AFF),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFFE3F2FD)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Spa, contentDescription = "Mes Grains") },
                    label = { Text("Grains") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF007AFF),
                        selectedTextColor = Color(0xFF007AFF),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFFE3F2FD)
                    )
                )
                NavigationBarItem(
                    icon = {
                        BadgedBox(badge = {
                            if (identificationBadgeCount > 0) Badge { Text("$identificationBadgeCount") }
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Identification")
                        }
                    },
                    label = { Text("Identification") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF007AFF),
                        selectedTextColor = Color(0xFF007AFF),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFFE3F2FD)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Assignment, contentDescription = "Mes Visites") },
                    label = { Text("Visites") },
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF007AFF),
                        selectedTextColor = Color(0xFF007AFF),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFFE3F2FD)
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(navController, repository)
                1 -> MolesScreen(navController, repository)
                2 -> InboxScreen(
                    navController = navController,
                    repository = repository,
                    onBadgeCountChange = { identificationBadgeCount = it }
                )
                3 -> VisitsScreen(navController, repository)
            }
        }
    }
}
