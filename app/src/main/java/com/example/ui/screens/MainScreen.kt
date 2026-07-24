package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.p2p.NetworkState
import com.example.ui.MeshViewModel
import com.example.ui.theme.MeshAmberWarning
import com.example.ui.theme.MeshCharcoalVariant
import com.example.ui.theme.MeshCyanPrimary
import com.example.ui.theme.MeshCyanSecondary
import com.example.ui.theme.MeshEmeraldAccent
import com.example.ui.theme.MeshObsidianBg
import com.example.ui.theme.MeshSlateSurface
import com.example.ui.theme.MeshTextPrimary
import com.example.ui.theme.MeshTextSecondary

@Composable
fun MainScreen(
    viewModel: MeshViewModel
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val isPowerSaver by viewModel.isPowerSavingMode.collectAsStateWithLifecycle()
    val batteryLevel by viewModel.batteryLevel.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MeshObsidianBg),
        topBar = {
            // Main Top Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MeshSlateSurface)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when (networkState) {
                                        NetworkState.ACTIVE -> MeshEmeraldAccent
                                        NetworkState.POWER_SAVING -> MeshAmberWarning
                                        else -> Color.Gray
                                    }
                                )
                        )
                        Text(
                            text = "THE LIVING MESH",
                            style = MaterialTheme.typography.titleMedium,
                            color = MeshCyanPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isPowerSaver) {
                            Icon(
                                imageVector = Icons.Default.BatterySaver,
                                contentDescription = "Battery Throttled",
                                tint = MeshAmberWarning,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "$batteryLevel%",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (batteryLevel <= 20) MeshAmberWarning else MeshTextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MeshSlateSurface,
                contentColor = MeshCyanPrimary,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("bottom_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.setSelectedTab(0) },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Node") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MeshObsidianBg,
                        selectedTextColor = MeshCyanPrimary,
                        indicatorColor = MeshCyanPrimary,
                        unselectedIconColor = MeshTextSecondary,
                        unselectedTextColor = MeshTextSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_dashboard")
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.setSelectedTab(1) },
                    icon = { Icon(Icons.Default.People, contentDescription = "Peers") },
                    label = { Text("Peers") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MeshObsidianBg,
                        selectedTextColor = MeshCyanPrimary,
                        indicatorColor = MeshCyanPrimary,
                        unselectedIconColor = MeshTextSecondary,
                        unselectedTextColor = MeshTextSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_peers")
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.setSelectedTab(2) },
                    icon = { Icon(Icons.Default.Message, contentDescription = "Messages") },
                    label = { Text("Stream") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MeshObsidianBg,
                        selectedTextColor = MeshCyanPrimary,
                        indicatorColor = MeshCyanPrimary,
                        unselectedIconColor = MeshTextSecondary,
                        unselectedTextColor = MeshTextSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_messages")
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { viewModel.setSelectedTab(3) },
                    icon = { Icon(Icons.Default.Storage, contentDescription = "Storage") },
                    label = { Text("Storage") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MeshObsidianBg,
                        selectedTextColor = MeshCyanPrimary,
                        indicatorColor = MeshCyanPrimary,
                        unselectedIconColor = MeshTextSecondary,
                        unselectedTextColor = MeshTextSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_storage")
                )

                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { viewModel.setSelectedTab(4) },
                    icon = { Icon(Icons.Default.Fingerprint, contentDescription = "Account") },
                    label = { Text("Account") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MeshObsidianBg,
                        selectedTextColor = MeshCyanPrimary,
                        indicatorColor = MeshCyanPrimary,
                        unselectedIconColor = MeshTextSecondary,
                        unselectedTextColor = MeshTextSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_account")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DashboardTab(viewModel = viewModel)
                1 -> PeersTab(viewModel = viewModel)
                2 -> MessagingTab(viewModel = viewModel)
                3 -> StorageTab(viewModel = viewModel)
                4 -> AuthScreen(
                    viewModel = viewModel,
                    onContinueToDashboard = { viewModel.setSelectedTab(0) }
                )
            }
        }
    }
}
