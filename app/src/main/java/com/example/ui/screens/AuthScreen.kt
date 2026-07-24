package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.p2p.AuthMode
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
 * Onboarding and Account Abstraction Authentication Screen.
 * Provides keyless Passkey / Biometric sign-in, 2-of-3 Threshold Key Recovery, and Smart Account management.
 */
@Composable
fun AuthScreen(
    viewModel: MeshViewModel,
    onContinueToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accountState by viewModel.accountState.collectAsStateWithLifecycle()

    var emailInput by remember { mutableStateOf("") }
    var recoveryJsonInput by remember { mutableStateOf("") }
    var showRecoverySection by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeshObsidianBg)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Hero Banner Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MeshCyanPrimary.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MeshSlateSurface)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MeshCyanPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Passkey Onboarding",
                            tint = MeshCyanPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "NOSERVER MESH",
                            style = MaterialTheme.typography.labelMedium,
                            color = MeshCyanSecondary,
                            letterSpacing = 1.2.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Account Abstraction",
                            style = MaterialTheme.typography.titleLarge,
                            color = MeshTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Keyless, gasless Web3 node authentication powered by Android Passkeys & 2-of-3 Threshold Secret Sharing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeshTextSecondary
                )
            }
        }

        // Active Account Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MeshEmeraldAccent.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MeshSlateSurface)
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
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Security State",
                            tint = MeshEmeraldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = when (accountState.authMode) {
                                AuthMode.PASSKEY_SECURED -> "PASSKEY SECURED"
                                AuthMode.THRESHOLD_RECOVERED -> "THRESHOLD RECOVERED"
                                else -> "ANONYMOUS NODE"
                            },
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
                                Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Sign Out",
                                tint = MeshTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = "Smart Account Address",
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = MeshCyanPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("smart_account_address_text")
                        )

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Smart Account", accountState.accountAddress)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Account Address Copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("copy_account_address_button")
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusPill(
                        label = "${accountState.sharesSecuredCount}-of-3 Shares Active",
                        isActive = accountState.sharesSecuredCount >= 2,
                        activeColor = MeshEmeraldAccent,
                        modifier = Modifier.weight(1f)
                    )
                    StatusPill(
                        label = if (accountState.isLoggedIn) "Gasless Signer Ready" else "Device Share Only",
                        isActive = accountState.isLoggedIn,
                        activeColor = MeshCyanPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Email & Passkey Sign-In Form
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MeshSlateSurface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Passkey & Email Onboarding",
                    style = MaterialTheme.typography.titleMedium,
                    color = MeshTextPrimary,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Email Address") },
                    placeholder = { Text("user@domain.com") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Email Input",
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
                        .testTag("email_input_field"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MeshCyanPrimary,
                        unfocusedBorderColor = MeshCharcoalVariant,
                        focusedLabelColor = MeshCyanPrimary,
                        cursorColor = MeshCyanPrimary,
                        focusedTextColor = MeshTextPrimary,
                        unfocusedTextColor = MeshTextPrimary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.signUpWithPasskey(emailInput) { success, msg ->
                                statusText = msg
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sign_up_passkey_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MeshCyanPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Register Passkey",
                            tint = MeshObsidianBg,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Register", color = MeshObsidianBg, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.signInWithPasskey(emailInput) { success, msg ->
                                statusText = msg
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sign_in_passkey_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MeshCyanSecondary)
                    ) {
                        Text("Sign In")
                    }
                }

                if (statusText.isNotBlank()) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MeshEmeraldAccent,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Expandable Mesh Recovery Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MeshSlateSurface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
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
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = "Mesh Recovery",
                            tint = MeshAmberWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "2-of-3 Mesh Share Recovery",
                            style = MaterialTheme.typography.titleSmall,
                            color = MeshTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(
                        onClick = { showRecoverySection = !showRecoverySection },
                        modifier = Modifier.testTag("toggle_recovery_section_button")
                    ) {
                        Text(
                            text = if (showRecoverySection) "Hide" else "Expand",
                            color = MeshCyanSecondary
                        )
                    }
                }

                AnimatedVisibility(visible = showRecoverySection) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "If you lost your device or passkey, paste your encrypted Share 3 JSON backup payload to reconstruct your node identity key.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeshTextSecondary
                        )

                        OutlinedTextField(
                            value = recoveryJsonInput,
                            onValueChange = { recoveryJsonInput = it },
                            label = { Text("Mesh Recovery Share 3 (JSON)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recovery_json_input_field"),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeshAmberWarning,
                                unfocusedBorderColor = MeshCharcoalVariant,
                                focusedTextColor = MeshTextPrimary,
                                unfocusedTextColor = MeshTextPrimary
                            )
                        )

                        Button(
                            onClick = {
                                viewModel.recoverAccountFromMesh(emailInput, recoveryJsonInput) { success, msg ->
                                    statusText = msg
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recover_account_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MeshAmberWarning)
                        ) {
                            Text("Reconstruct Account Key", color = MeshObsidianBg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Action Button - Start Node / Continue
        Button(
            onClick = { onContinueToDashboard() },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("start_node_button"),
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
                    contentDescription = "Enter Mesh Dashboard",
                    tint = MeshObsidianBg,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
