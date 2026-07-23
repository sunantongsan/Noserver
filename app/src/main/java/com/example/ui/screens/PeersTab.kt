package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PinEnd
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.p2p.DiscoveredPeer
import com.example.ui.MeshViewModel
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
fun PeersTab(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activePeers by viewModel.activePeers.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MeshObsidianBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "P2P Network Topology",
                        style = MaterialTheme.typography.titleLarge,
                        color = MeshTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${activePeers.size} node(s) discovered on local network",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeshTextSecondary
                    )
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MeshCyanPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("add_manual_peer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Peer",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add IP")
                }
            }

            if (activePeers.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MeshSlateSurface)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiFind,
                            contentDescription = "Scanning",
                            tint = MeshCyanSecondary,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "Scanning Local Wi-Fi for P2P Nodes...",
                            style = MaterialTheme.typography.titleMedium,
                            color = MeshTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ensure another Android device running The Living Mesh is on the same local network or manually add its IP address.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeshTextSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(activePeers, key = { it.nodeId }) { peer ->
                        PeerItemCard(
                            peer = peer,
                            onPingClick = {
                                viewModel.sendPing(peer.ipAddress, peer.port, peer.nodeId)
                                Toast.makeText(context, "PING sent to ${peer.nodeId}", Toast.LENGTH_SHORT).show()
                            },
                            onDirectMsgClick = {
                                viewModel.sendDirectMessage(
                                    peerIp = peer.ipAddress,
                                    peerPort = peer.port,
                                    payload = "Hello from ${viewModel.nodeId}!",
                                    targetNodeId = peer.nodeId
                                )
                                Toast.makeText(context, "Direct message sent to ${peer.nodeId}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AddPeerDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { ip, port ->
                    viewModel.addManualPeer(ip, port)
                    showAddDialog = false
                    Toast.makeText(context, "Connecting to $ip:$port", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun PeerItemCard(
    peer: DiscoveredPeer,
    onPingClick: () -> Unit,
    onDirectMsgClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MeshSlateSurface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MeshEmeraldAccent)
                    )
                    Text(
                        text = peer.nodeId,
                        style = MaterialTheme.typography.titleMedium,
                        color = MeshCyanPrimary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = "${peer.ipAddress}:${peer.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshTextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (peer.publicKeyHex.isNotBlank()) {
                Text(
                    text = "Public Key: ${peer.publicKeyHex.take(24)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPingClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MeshCyanSecondary)
                ) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = "Ping",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PING")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onDirectMsgClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MeshCyanPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Direct Msg")
                }
            }
        }
    }
}

@Composable
fun AddPeerDialog(
    onDismiss: () -> Unit,
    onAdd: (ip: String, port: Int) -> Unit
) {
    var ipInput by remember { mutableStateOf("192.168.1.") }
    var portInput by remember { mutableStateOf("8988") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MeshSlateSurface,
        title = {
            Text("Add Manual Peer Node", color = MeshTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter the local IP address and TCP port of another Android node:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshTextSecondary
                )

                OutlinedTextField(
                    value = ipInput,
                    onValueChange = { ipInput = it },
                    label = { Text("IP Address") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MeshCyanPrimary,
                        unfocusedBorderColor = MeshCharcoalVariant,
                        focusedTextColor = MeshTextPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier.testTag("manual_peer_ip_input")
                )

                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it },
                    label = { Text("TCP Port") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MeshCyanPrimary,
                        unfocusedBorderColor = MeshCharcoalVariant,
                        focusedTextColor = MeshTextPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier.testTag("manual_peer_port_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = portInput.toIntOrNull() ?: 8988
                    onAdd(ipInput, port)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MeshCyanPrimary),
                modifier = Modifier.testTag("confirm_add_peer_button")
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MeshTextSecondary)
            }
        }
    )
}
