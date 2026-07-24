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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.p2p.PacketType
import com.example.ui.MeshViewModel
import com.example.ui.models.SocialPostModel
import com.example.ui.theme.MeshAmberWarning
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

// Vibrant avatar color palette for P2P Nodes
private val NodeAvatarColors = listOf(
    Color(0xFF00E5FF), // Cyan
    Color(0xFF00E676), // Emerald
    Color(0xFFFFD600), // Amber
    Color(0xFF7C4DFF), // Purple
    Color(0xFFFF4081), // Pink
    Color(0xFFFF6D00)  // Orange
)

/**
 * Main Twitter/X style Social Network Feed Screen for Noserver P2P Mesh.
 */
@Composable
fun SocialFeedScreen(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activePeers by viewModel.activePeers.collectAsStateWithLifecycle()
    val recentPackets by viewModel.recentPackets.collectAsStateWithLifecycle()

    // Filter packets down to CHAT_MESSAGE or social feed broadcasts
    val socialPosts = remember(recentPackets) {
        recentPackets
            .filter { it.packetType == PacketType.CHAT_MESSAGE || it.targetNodeId == "BROADCAST" }
            .map { SocialPostModel.fromDataPacket(it) }
    }

    val peerCount = activePeers.size
    val isConnected = peerCount > 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeshObsidianBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Peer Status Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isConnected) MeshEmeraldAccent.copy(alpha = 0.4f) else MeshAmberWarning.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
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
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) MeshEmeraldAccent else MeshAmberWarning)
                    )

                    Icon(
                        imageVector = if (isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = "Peer Connection Status",
                        tint = if (isConnected) MeshEmeraldAccent else MeshAmberWarning,
                        modifier = Modifier.size(20.dp)
                    )

                    Column {
                        Text(
                            text = if (isConnected) "P2P MESH: CONNECTED TO $peerCount PEER${if (peerCount > 1) "S" else ""}" else "SEARCHING FOR PEERS...",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isConnected) MeshEmeraldAccent else MeshAmberWarning,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("p2p_mesh_status_banner")
                        )
                        Text(
                            text = if (isConnected) "Decentralized P2P Gossip Broadcast Active" else "Broadcasting local discovery beacons over Wi-Fi/UDP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeshTextMuted
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = "Peers Count",
                        tint = MeshCyanSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$peerCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeshCyanPrimary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 2. Post Composer Input
        CreatePostComponent(viewModel = viewModel)

        // 3. Social Feed Stream Header
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
                    imageVector = Icons.Default.RssFeed,
                    contentDescription = "Feed",
                    tint = MeshCyanPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Decentralized Feed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MeshTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "${socialPosts.size} Posts",
                style = MaterialTheme.typography.labelSmall,
                color = MeshTextMuted,
                fontFamily = FontFamily.Monospace
            )
        }

        // 4. Social Feed List
        if (socialPosts.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MeshSlateSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = "No Posts",
                        tint = MeshCyanSecondary,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "No mesh posts broadcast yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MeshTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Be the first to post to the decentralized network using the composer above!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeshTextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag("social_feed_list")
            ) {
                items(socialPosts, key = { it.id }) { post ->
                    SocialPostCard(post = post, onCopyAddress = { address ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Node Address", address)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Wallet Address Copied!", Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }
    }
}

@Composable
fun SocialPostCard(
    post: SocialPostModel,
    onCopyAddress: (String) -> Unit
) {
    val avatarBg = NodeAvatarColors[post.avatarColorIndex % NodeAvatarColors.size]
    val timeFormatted = remember(post.timestamp) {
        val sdf = SimpleDateFormat("HH:mm • dd MMM", Locale.getDefault())
        sdf.format(Date(post.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MeshSlateSurface, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeshSlateSurface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Avatar, Wallet Address / Node ID, Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Avatar Icon with Node Color
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(avatarBg.copy(alpha = 0.25f))
                            .border(1.dp, avatarBg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = post.senderNodeId.take(2).uppercase(),
                            color = avatarBg,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = post.walletAddress.take(10) + "..." + post.walletAddress.takeLast(4),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MeshTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified ECDSA Signature",
                                tint = MeshCyanPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = "Node ID: ${post.senderNodeId.take(12)}...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeshTextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = MeshTextMuted,
                    fontSize = 11.sp
                )
            }

            // Post Content Text
            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MeshTextPrimary,
                lineHeight = 22.sp
            )

            // Cryptographic Footer: Signature Badge & Copy Wallet Address
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Signed Packet",
                        tint = MeshEmeraldAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Sig: ${post.signature.take(12)}...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeshEmeraldAccent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }

                IconButton(
                    onClick = { onCopyAddress(post.walletAddress) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Wallet Address",
                        tint = MeshTextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
