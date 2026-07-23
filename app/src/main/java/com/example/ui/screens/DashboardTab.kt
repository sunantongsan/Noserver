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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.graphics.Brush
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

@Composable
fun DashboardTab(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val activePeers by viewModel.activePeers.collectAsStateWithLifecycle()
    val totalBytes by viewModel.totalHostedBytes.collectAsStateWithLifecycle()
    val isPowerSaver by viewModel.isPowerSavingMode.collectAsStateWithLifecycle()
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val recentPackets by viewModel.recentPackets.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeshObsidianBg)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Network Identity Header Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MeshCyanPrimary.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
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
                                NetworkState.POWER_SAVING -> "NODE THROTTLED (POWER SAVER)"
                                NetworkState.STARTING -> "STARTING NODE..."
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
                            text = if (isServiceRunning) "Foreground Active" else "Service Stopped",
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
                            contentDescription = "Android KeyStore",
                            tint = MeshEmeraldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Android KeyStore (ECDSA secp256r1)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeshTextSecondary
                        )
                    }

                    IconButton(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Node Identity", viewModel.publicKeyHex)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Public Key copied!", Toast.LENGTH_SHORT).show()
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

        // Power Saver Throttling Banner
        AnimatedVisibility(
            visible = isPowerSaver,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MeshCharcoalVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BatterySaver,
                        contentDescription = "Battery Throttling",
                        tint = MeshAmberWarning,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Battery Smart Throttling Active",
                            style = MaterialTheme.typography.titleSmall,
                            color = MeshAmberWarning,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Background network scans reduced to conserve energy (<20% battery).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeshTextSecondary
                        )
                    }
                }
            }
        }

        // Quick Stats Metrics Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Mesh Peers",
                value = "${activePeers.size}",
                subtitle = "Local mDNS discovered",
                icon = Icons.Default.Wifi,
                iconTint = MeshCyanSecondary,
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                title = "Hosted Block",
                value = formatBytes(totalBytes),
                subtitle = "Zero-gas local storage",
                icon = Icons.Default.Storage,
                iconTint = MeshEmeraldAccent,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Packets Processed",
                value = "${recentPackets.size}",
                subtitle = "Recent P2P transactions",
                icon = Icons.Default.Speed,
                iconTint = MeshCyanPrimary,
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                title = "Wakelock",
                value = if (isServiceRunning) "ACTIVE" else "RELEASED",
                subtitle = "Background CPU state",
                icon = Icons.Default.Memory,
                iconTint = if (isServiceRunning) MeshEmeraldAccent else MeshTextMuted,
                modifier = Modifier.weight(1f)
            )
        }

        // System Control Actions Card
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
                    text = "Node System Controls",
                    style = MaterialTheme.typography.titleMedium,
                    color = MeshTextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Smart Throttling Override",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MeshTextPrimary
                        )
                        Text(
                            text = "Manually throttle node background power",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeshTextSecondary
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.togglePowerSaverMode() },
                        modifier = Modifier.testTag("toggle_power_saver_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isPowerSaver) MeshAmberWarning else MeshCyanSecondary
                        )
                    ) {
                        Text(if (isPowerSaver) "Throttled" else "Normal")
                    }
                }
            }
        }

        // Node Identity Technical Spec Details
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MeshSlateSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Public Key Fingerprint (Hex)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MeshCyanSecondary
                )
                Text(
                    text = viewModel.publicKeyHex,
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshTextSecondary,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MeshSlateSurface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MeshTextSecondary
                )
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MeshTextPrimary,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MeshTextMuted,
                fontSize = 11.sp
            )
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
