package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.p2p.NetworkState
import com.example.p2p.P2pState
import com.example.ui.MeshViewModel
import com.example.ui.theme.MeshAmberWarning
import com.example.ui.theme.MeshCharcoalVariant
import com.example.ui.theme.MeshCyanPrimary
import com.example.ui.theme.MeshCyanSecondary
import com.example.ui.theme.MeshEmeraldAccent
import com.example.ui.theme.MeshObsidianBg
import com.example.ui.theme.MeshSlateSurface
import com.example.ui.theme.MeshTextMuted
import com.example.ui.theme.MeshTextPrimary
import com.example.ui.theme.MeshTextSecondary

/**
 * Node Dashboard Screen (Phase 3 UI Interface).
 * Visualizes zero-gas node identity, live network state, proof-of-resource yield earnings,
 * and smart battery/energy protection status.
 */
@Composable
fun NodeDashboardScreen(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val p2pState by viewModel.p2pState.collectAsStateWithLifecycle()
    val reflexiveAddress by viewModel.localReflexiveAddress.collectAsStateWithLifecycle()
    val activePeers by viewModel.activePeers.collectAsStateWithLifecycle()
    val energyPolicy by viewModel.energyPolicy.collectAsStateWithLifecycle()
    val yieldMetrics by viewModel.yieldMetrics.collectAsStateWithLifecycle()
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val recentPackets by viewModel.recentPackets.collectAsStateWithLifecycle()
    val accountState by viewModel.accountState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeshObsidianBg)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Identity & Node Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MeshCyanPrimary.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MeshSlateSurface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
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
                            text = when (networkState) {
                                NetworkState.ACTIVE -> "NODE ONLINE"
                                NetworkState.POWER_SAVING -> "ENERGY SAVER ACTIVE"
                                else -> "NODE OFFLINE"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MeshTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isServiceRunning) "Foreground" else "Stopped",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeshTextSecondary
                        )
                        Switch(
                            checked = isServiceRunning,
                            onCheckedChange = { viewModel.toggleForegroundService() },
                            modifier = Modifier.testTag("toggle_foreground_service_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MeshCyanPrimary,
                                checkedTrackColor = MeshCharcoalVariant
                            )
                        )
                    }
                }

                Text(
                    text = viewModel.nodeId,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MeshCyanPrimary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("node_id_text")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Android KeyStore ECDSA",
                            tint = MeshEmeraldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = yieldMetrics.rankTierName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MeshEmeraldAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "v${com.example.BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeshTextMuted,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("app_version_tag")
                        )
                    }

                    IconButton(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Node Identity", viewModel.publicKeyHex)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Public Key copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("copy_public_key_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Public Key",
                            tint = MeshCyanSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Account Abstraction & Passkey Quick Badge
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MeshCyanPrimary.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MeshSlateSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Passkey Account",
                        tint = MeshCyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "SMART ACCOUNT (ERC-4337 CONCEPT)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeshTextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = accountState.accountAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MeshCyanPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = { viewModel.setSelectedTab(4) },
                    colors = ButtonDefaults.buttonColors(containerColor = MeshCyanPrimary.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Manage", color = MeshCyanPrimary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // 2. Proof-of-Resource Yield Earnings Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MeshEmeraldAccent.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = MeshSlateSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonetizationOn,
                            contentDescription = "Yield Engine",
                            tint = MeshEmeraldAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Proof-of-Resource Yield",
                            style = MaterialTheme.typography.titleMedium,
                            color = MeshTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "ZERO-GAS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeshEmeraldAccent,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(MeshEmeraldAccent.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Accumulated Balance",
                            style = MaterialTheme.typography.labelMedium,
                            color = MeshTextSecondary
                        )
                        Text(
                            text = yieldMetrics.getFormattedTokens(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MeshEmeraldAccent,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("accumulated_tokens_text")
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Hourly Yield Rate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeshTextMuted
                        )
                        Text(
                            text = String.format("+%.4f MESH/hr", yieldMetrics.hourlyYieldRate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MeshCyanSecondary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricMiniBadge(
                        label = "Uptime",
                        value = yieldMetrics.getFormattedUptime(),
                        icon = Icons.Default.Timer,
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniBadge(
                        label = "Packets Routed",
                        value = "${yieldMetrics.packetsRoutedCount}",
                        icon = Icons.Default.Router,
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniBadge(
                        label = "Storage Shared",
                        value = yieldMetrics.getFormattedStorage(),
                        icon = Icons.Default.Storage,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Battery & Smart Energy Scheduler Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MeshSlateSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (energyPolicy.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.Bolt,
                            contentDescription = "Energy Status",
                            tint = if (energyPolicy.isBatteryLow) MeshAmberWarning else MeshCyanPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Smart Resource Scheduler",
                            style = MaterialTheme.typography.titleMedium,
                            color = MeshTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "${energyPolicy.batteryLevel}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (energyPolicy.isBatteryLow) MeshAmberWarning else MeshCyanPrimary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = energyPolicy.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshTextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusPill(
                        label = if (energyPolicy.isCharging) "Charging" else "On Battery",
                        isActive = energyPolicy.isCharging,
                        activeColor = MeshEmeraldAccent,
                        modifier = Modifier.weight(1f)
                    )
                    StatusPill(
                        label = if (energyPolicy.isOnWifi) "Wi-Fi Active" else "Cellular Data",
                        isActive = energyPolicy.isOnWifi,
                        activeColor = MeshCyanPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    StatusPill(
                        label = if (energyPolicy.isHeavyTaskAllowed) "Compute Safe" else "Compute Throttled",
                        isActive = energyPolicy.isHeavyTaskAllowed,
                        activeColor = MeshEmeraldAccent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. Live Network & Peer Topology Metrics Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "STUN Reflexive IP",
                value = if (reflexiveAddress.isNotBlank()) reflexiveAddress.split(":").firstOrNull() ?: "DISCOVERED" else "STUN Probe...",
                subtitle = if (reflexiveAddress.isNotBlank()) "Port: ${reflexiveAddress.split(":").getOrNull(1) ?: "19302"}" else "CGNAT Traversal",
                icon = Icons.Default.QrCode,
                iconTint = MeshCyanSecondary,
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                title = "Transport State",
                value = when (p2pState) {
                    is P2pState.Connected -> "CONNECTED"
                    is P2pState.Connecting -> "CONNECTING"
                    is P2pState.Reconnecting -> "RETRYING"
                    is P2pState.Failed -> "FAILED"
                    else -> "IDLE"
                },
                subtitle = p2pState.description.take(24),
                icon = Icons.Default.CheckCircle,
                iconTint = when (p2pState) {
                    is P2pState.Connected -> MeshEmeraldAccent
                    is P2pState.Reconnecting -> MeshAmberWarning
                    is P2pState.Failed -> Color.Red
                    else -> MeshCyanPrimary
                },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Active Mesh Peers",
                value = "${activePeers.size}",
                subtitle = "mDNS + ICE Discovered",
                icon = Icons.Default.Wifi,
                iconTint = MeshCyanSecondary,
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                title = "Processed Packets",
                value = "${recentPackets.size}",
                subtitle = "P2P Wire Transactions",
                icon = Icons.Default.Speed,
                iconTint = MeshCyanPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        // 5. System Controls Action Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MeshSlateSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Node System Management",
                    style = MaterialTheme.typography.titleMedium,
                    color = MeshTextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.retryConnection() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("retry_connection_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MeshCyanPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry P2P Connection",
                            tint = MeshObsidianBg,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-Probe ICE", color = MeshObsidianBg, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { viewModel.togglePowerSaverMode() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MeshCyanSecondary)
                    ) {
                        Text("Toggle Throttle")
                    }
                }
            }
        }
    }
}

@Composable
fun MetricMiniBadge(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MeshCharcoalVariant, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MeshCyanSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MeshTextMuted,
                    fontSize = 10.sp
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MeshTextPrimary,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                if (isActive) activeColor.copy(alpha = 0.15f) else MeshCharcoalVariant,
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (isActive) activeColor.copy(alpha = 0.4f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) activeColor else MeshTextMuted,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}
