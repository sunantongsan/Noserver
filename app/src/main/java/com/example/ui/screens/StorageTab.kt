package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.p2p.DataPacket
import com.example.ui.MeshViewModel
import com.example.ui.theme.MeshCharcoalVariant
import com.example.ui.theme.MeshCoralError
import com.example.ui.theme.MeshCyanPrimary
import com.example.ui.theme.MeshCyanSecondary
import com.example.ui.theme.MeshEmeraldAccent
import com.example.ui.theme.MeshObsidianBg
import com.example.ui.theme.MeshSlateSurface
import com.example.ui.theme.MeshTextMuted
import com.example.ui.theme.MeshTextPrimary
import com.example.ui.theme.MeshTextSecondary

@Composable
fun StorageTab(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val storedPackets by viewModel.storedPackets.collectAsStateWithLifecycle()
    val totalBytes by viewModel.totalHostedBytes.collectAsStateWithLifecycle()
    var showStoreDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeshObsidianBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Storage Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MeshSlateSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Zero-Gas Storage Ledger",
                        style = MaterialTheme.typography.titleMedium,
                        color = MeshTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Local Room DB + P2P Mesh Hosting",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeshTextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatBytes(totalBytes)} Total Hosted (${storedPackets.size} blocks)",
                        style = MaterialTheme.typography.titleLarge,
                        color = MeshEmeraldAccent,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showStoreDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MeshCyanPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("store_data_block_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Upload",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Store Block")
                    }

                    if (storedPackets.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.clearLedger()
                                Toast.makeText(context, "Ledger cleared", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MeshCoralError),
                            modifier = Modifier.testTag("clear_ledger_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }
                }
            }
        }

        // Stored Data Blocks List
        if (storedPackets.isEmpty()) {
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "Storage Empty",
                        tint = MeshCyanSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No Data Blocks Hosted On Node",
                        style = MaterialTheme.typography.titleMedium,
                        color = MeshTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Store custom text or JSON payloads to test local Room DB persistence and P2P decentralized replication.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeshTextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(storedPackets, key = { it.id }) { packet ->
                    StoredBlockCard(
                        packet = packet,
                        onDeleteClick = {
                            viewModel.deleteStoredBlock(packet.id)
                            Toast.makeText(context, "Block deleted", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        if (showStoreDialog) {
            StoreBlockDialog(
                onDismiss = { showStoreDialog = false },
                onStore = { payload ->
                    viewModel.storeDataBlock(payload)
                    showStoreDialog = false
                    Toast.makeText(context, "Data block saved & broadcasted!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun StoredBlockCard(
    packet: DataPacket,
    onDeleteClick: () -> Unit
) {
    val sizeBytes = packet.payload.toByteArray().size

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MeshSlateSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BLOCK ID: ${packet.id.take(16)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeshCyanSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "$sizeBytes B",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeshEmeraldAccent,
                        fontFamily = FontFamily.Monospace
                    )

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Block",
                            tint = MeshCoralError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Text(
                text = packet.payload,
                style = MaterialTheme.typography.bodyMedium,
                color = MeshTextPrimary,
                maxLines = 3
            )

            Text(
                text = "Host Node: ${packet.senderNodeId} • TTL: ${packet.ttl}",
                style = MaterialTheme.typography.bodySmall,
                color = MeshTextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun StoreBlockDialog(
    onDismiss: () -> Unit,
    onStore: (payload: String) -> Unit
) {
    var payloadInput by remember { mutableStateOf("{\"block_type\": \"STORAGE\", \"content\": \"Sample decentralized file metadata\"}") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MeshSlateSurface,
        title = {
            Text("Store Data Block on Node", color = MeshTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Enter text or JSON payload to persist locally in Room DB and broadcast across the P2P mesh:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshTextSecondary
                )

                OutlinedTextField(
                    value = payloadInput,
                    onValueChange = { payloadInput = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MeshCyanPrimary,
                        unfocusedBorderColor = MeshCharcoalVariant,
                        focusedTextColor = MeshTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .testTag("payload_block_input"),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (payloadInput.isNotBlank()) {
                        onStore(payloadInput)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MeshCyanPrimary),
                modifier = Modifier.testTag("confirm_store_block_button")
            ) {
                Text("Store Block")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MeshTextSecondary)
            }
        }
    )
}
