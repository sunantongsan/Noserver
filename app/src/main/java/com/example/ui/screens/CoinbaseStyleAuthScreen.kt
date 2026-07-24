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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coinbase Smart Wallet Onboarding Screen.
 * Provides a 1-tap Passkey / Biometric authentication experience eliminating seed phrases.
 */
@Composable
fun CoinbaseStyleAuthScreen(
    viewModel: MeshViewModel,
    onContinueToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val accountState by viewModel.accountState.collectAsStateWithLifecycle()

    var emailInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loadingStatusText by remember { mutableStateOf("Creating your decentralized node...") }
    var statusMessageText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeshObsidianBg)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Hero Branding Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MeshCyanPrimary.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MeshSlateSurface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MeshCyanPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Coinbase Smart Wallet",
                            tint = MeshObsidianBg,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "COINBASE SMART WALLET",
                            style = MaterialTheme.typography.labelMedium,
                            color = MeshCyanSecondary,
                            letterSpacing = 1.2.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Passkey Onboarding",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MeshTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Sign in or create your Web3 Node Wallet instantly with biometric Passkeys synced safely to your Google Password Manager.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeshTextSecondary
                )
            }
        }

        // Active Smart Account Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MeshEmeraldAccent.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MeshSlateSurface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
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
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Status",
                            tint = MeshEmeraldAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = if (accountState.isLoggedIn) "SMART WALLET ACTIVE" else "PASSKEY READY",
                            style = MaterialTheme.typography.labelLarge,
                            color = MeshEmeraldAccent,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (accountState.isLoggedIn) {
                        IconButton(
                            onClick = {
                                viewModel.signOutAccount()
                                statusMessageText = "Signed out"
                                Toast.makeText(context, "Wallet disconnected", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("coinbase_sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Disconnect Wallet",
                                tint = MeshTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Smart Wallet Address (ERC-4337)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeshTextMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = accountState.accountAddress,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MeshCyanPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("coinbase_smart_address_text")
                        )

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Coinbase Smart Wallet", accountState.accountAddress)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Wallet Address Copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("copy_coinbase_address_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Address",
                                tint = MeshCyanSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Security Badges
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecurityBulletPill(text = "0 Seed Phrases • Biometric Enclave")
                    SecurityBulletPill(text = "Google Password Manager Cloud Sync")
                    SecurityBulletPill(text = "Gasless Node Operations (Zero Fee)")
                }
            }
        }

        // Single-Tap Passkey Action Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MeshSlateSurface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "One-Tap Passkey Sign-In",
                    style = MaterialTheme.typography.titleMedium,
                    color = MeshTextPrimary,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Email (Google Account)") },
                    placeholder = { Text("user@gmail.com") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Email",
                            tint = MeshCyanSecondary
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("coinbase_email_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MeshCyanPrimary,
                        unfocusedBorderColor = MeshCharcoalVariant,
                        focusedLabelColor = MeshCyanPrimary,
                        cursorColor = MeshCyanPrimary,
                        focusedTextColor = MeshTextPrimary,
                        unfocusedTextColor = MeshTextPrimary
                    )
                )

                // Primary Action Button
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            loadingStatusText = "Authenticating via Google Password Manager..."
                            delay(600)
                            viewModel.signUpWithPasskey(emailInput) { success, msg ->
                                isLoading = false
                                statusMessageText = msg
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("continue_with_passkey_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MeshCyanPrimary)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MeshObsidianBg,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = loadingStatusText,
                            color = MeshObsidianBg,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Passkey Fingerprint",
                            tint = MeshObsidianBg,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Continue with Passkey",
                            color = MeshObsidianBg,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                if (statusMessageText.isNotBlank()) {
                    Text(
                        text = statusMessageText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeshEmeraldAccent,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Start Node & Enter Mesh Button
        Button(
            onClick = { onContinueToDashboard() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("coinbase_start_node_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MeshEmeraldAccent)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "START NODE & ENTER MESH",
                    style = MaterialTheme.typography.titleMedium,
                    color = MeshObsidianBg,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Dashboard",
                    tint = MeshObsidianBg,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SecurityBulletPill(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Check",
            tint = MeshEmeraldAccent,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MeshTextSecondary
        )
    }
}
