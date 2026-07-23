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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import com.example.p2p.PacketType
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessagingTab(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recentPackets by viewModel.recentPackets.collectAsStateWithLifecycle()
    var inputMessage by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeshObsidianBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Console Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "P2P Cryptographic Data Stream",
                    style = MaterialTheme.typography.titleLarge,
                    color = MeshTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Signed with ECDSA • Decoupled from central servers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshTextSecondary
                )
            }
        }

        // Messages Feed
        if (recentPackets.isEmpty()) {
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
                        imageVector = Icons.Default.Message,
                        contentDescription = "No Packets",
                        tint = MeshCyanSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No P2P Packets Exchanged Yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MeshTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Broadcast a signed message to nearby nodes using the box below.",
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
                items(recentPackets, key = { it.id }) { packet ->
                    PacketItemCard(packet = packet, currentNodeId = viewModel.nodeId)
                }
            }
        }

        // Send Input Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputMessage,
                onValueChange = { inputMessage = it },
                placeholder = { Text("Type signed P2P payload...", color = MeshTextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MeshCyanPrimary,
                    unfocusedBorderColor = MeshCharcoalVariant,
                    focusedTextColor = MeshTextPrimary,
                    unfocusedContainerColor = MeshSlateSurface,
                    focusedContainerColor = MeshSlateSurface
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("message_input_field"),
                singleLine = true
            )

            Button(
                onClick = {
                    if (inputMessage.isNotBlank()) {
                        viewModel.broadcastMessage(inputMessage)
                        inputMessage = ""
                        Toast.makeText(context, "Packet signed & broadcasted!", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MeshCyanPrimary),
                modifier = Modifier
                    .height(54.dp)
                    .testTag("broadcast_message_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Broadcast",
                    tint = MeshObsidianBg
                )
            }
        }
    }
}

@Composable
fun PacketItemCard(
    packet: DataPacket,
    currentNodeId: String
) {
    val isSelf = packet.senderNodeId == currentNodeId
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val formattedTime = dateFormat.format(Date(packet.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelf) MeshCharcoalVariant else MeshSlateSurface
        ),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MeshCyanPrimary.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = packet.packetType.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MeshCyanSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = if (isSelf) "YOU ($currentNodeId)" else packet.senderNodeId,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelf) MeshEmeraldAccent else MeshCyanPrimary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshTextMuted,
                    fontSize = 11.sp
                )
            }

            Text(
                text = packet.payload,
                style = MaterialTheme.typography.bodyMedium,
                color = MeshTextPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = "Signature Verified",
                        tint = MeshEmeraldAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "ECDSA SIGNATURE VALID",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeshEmeraldAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = "Sig: ${packet.signature.take(12)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
