package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Twitter/X style post composer component for P2P Mesh broadcasting.
 */
@Composable
fun CreatePostComponent(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier,
    onPostCreated: () -> Unit = {}
) {
    val context = LocalContext.current
    var postContent by remember { mutableStateOf("") }
    val maxChars = 280

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MeshCyanPrimary.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeshSlateSurface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MeshCyanPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Passkey Signed",
                            tint = MeshCyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "NEW MESH POST",
                        style = MaterialTheme.typography.labelMedium,
                        color = MeshCyanSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Character Counter
                val remainingChars = maxChars - postContent.length
                Text(
                    text = "$remainingChars",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (remainingChars < 20) MeshAmberWarning else MeshTextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }

            OutlinedTextField(
                value = postContent,
                onValueChange = {
                    if (it.length <= maxChars) {
                        postContent = it
                    }
                },
                placeholder = { Text("What's happening in the mesh? (Max 280 chars)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .testTag("create_post_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MeshCyanPrimary,
                    unfocusedBorderColor = MeshCharcoalVariant,
                    focusedTextColor = MeshTextPrimary,
                    unfocusedTextColor = MeshTextPrimary,
                    cursorColor = MeshCyanPrimary
                ),
                maxLines = 4
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Signed via Node KeyPair & Passkey",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeshTextMuted
                )

                Button(
                    onClick = {
                        if (postContent.isBlank()) {
                            Toast.makeText(context, "Post content cannot be empty", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        // Broadcast chat message / post across network using viewModel
                        viewModel.broadcastMessage(postContent)
                        Toast.makeText(context, "Post signed & broadcast to P2P Mesh!", Toast.LENGTH_SHORT).show()
                        postContent = ""
                        onPostCreated()
                    },
                    enabled = postContent.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeshCyanPrimary,
                        disabledContainerColor = MeshCharcoalVariant
                    ),
                    modifier = Modifier.testTag("post_to_mesh_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Post to Mesh",
                        tint = MeshObsidianBg,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Post to Mesh",
                        color = MeshObsidianBg,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
