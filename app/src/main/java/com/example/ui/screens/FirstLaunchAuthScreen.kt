package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.p2p.AppPreferences
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mandatory First-Launch Onboarding Screen complying with Google Play Privacy & Safety Policies.
 * Features Privacy Terms consent check, Passkey creation, and Google Play Account Deletion readiness.
 */
@Composable
fun FirstLaunchAuthScreen(
    viewModel: MeshViewModel,
    appPreferences: AppPreferences,
    onOnboardingCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var emailInput by remember { mutableStateOf(appPreferences.userEmail) }
    var termsAccepted by remember { mutableStateOf(appPreferences.hasAcceptedTerms) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeshObsidianBg)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Hero Logo & Title
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MeshCyanPrimary.copy(alpha = 0.15f))
                .border(2.dp, MeshCyanPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Noserver Security",
                tint = MeshCyanPrimary,
                modifier = Modifier.size(38.dp)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "NOSERVER MESH",
                style = MaterialTheme.typography.labelMedium,
                color = MeshCyanSecondary,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Welcome to P2P Node",
                style = MaterialTheme.typography.headlineMedium,
                color = MeshTextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Keyless Web3 Node Identity & Decentralized Mesh",
                style = MaterialTheme.typography.bodyMedium,
                color = MeshTextSecondary,
                textAlign = TextAlign.Center
            )
        }

        // Email & Passkey Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MeshCyanPrimary.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MeshSlateSurface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "1. Create Node Passkey Wallet",
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
                        .testTag("first_launch_email_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MeshCyanPrimary,
                        unfocusedBorderColor = MeshCharcoalVariant,
                        focusedLabelColor = MeshCyanPrimary,
                        cursorColor = MeshCyanPrimary,
                        focusedTextColor = MeshTextPrimary,
                        unfocusedTextColor = MeshTextPrimary
                    )
                )

                // Privacy Policy Terms Checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { termsAccepted = !termsAccepted }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = termsAccepted,
                        onCheckedChange = { termsAccepted = it },
                        modifier = Modifier.testTag("terms_checkbox"),
                        colors = CheckboxDefaults.colors(
                            checkedColor = MeshCyanPrimary,
                            uncheckedColor = MeshTextMuted,
                            checkmarkColor = MeshObsidianBg
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "I agree to Privacy Policy & Mesh Node Data Terms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeshTextSecondary
                    )
                }

                TextButton(
                    onClick = { showTermsDialog = true },
                    modifier = Modifier
                        .align(Alignment.Start)
                        .testTag("view_privacy_policy_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PrivacyTip,
                            contentDescription = "Privacy Policy",
                            tint = MeshCyanSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Read Full Google Play Compliance Policy",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeshCyanSecondary
                        )
                    }
                }

                // Create Passkey Button
                Button(
                    onClick = {
                        if (!termsAccepted) {
                            Toast.makeText(context, "Please accept Privacy Terms to proceed", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            isLoading = true
                            delay(400)
                            viewModel.signUpWithPasskey(emailInput) { success, msg ->
                                isLoading = false
                                statusText = msg
                                if (success) {
                                    appPreferences.completeOnboarding(emailInput)
                                    Toast.makeText(context, "Passkey Wallet Created!", Toast.LENGTH_SHORT).show()
                                    onOnboardingCompleted()
                                } else {
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("first_launch_create_passkey_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MeshCyanPrimary)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MeshObsidianBg,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Creating Biometric Wallet...", color = MeshObsidianBg, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Passkey",
                            tint = MeshObsidianBg,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CREATE PASSKEY & START NODE",
                            color = MeshObsidianBg,
                            fontWeight = FontWeight.Bold
                        )
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

        // Account Deletion Readiness for Google Play Policy
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MeshSlateSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google Play Data Policy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeshTextMuted
                    )
                    Text(
                        text = "Delete Node & Account Data",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeshTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { showDeleteAccountDialog = true },
                    modifier = Modifier.testTag("delete_account_data_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MeshAmberWarning)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Delete Data",
                        tint = MeshAmberWarning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete Data", fontSize = 12.sp)
                }
            }
        }
    }

    // Terms & Privacy Policy Dialog
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PrivacyTip,
                        contentDescription = "Privacy Policy",
                        tint = MeshCyanPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Privacy & Node Terms", color = MeshTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Noserver P2P Mesh operates completely serverless without central database storage. Your Passkey authentication stays secured in your Android KeyStore and Google Password Manager.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeshTextSecondary
                    )
                    Text(
                        text = "1. Data Collection: Zero personally identifiable telemetry or user tracking is logged or shared with third parties.\n2. Key Management: Web3 Wallet keys are split into 2-of-3 non-custodial shares on device.\n3. Account Deletion: You can clear all local shares and reset identity at any time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeshTextMuted
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        termsAccepted = true
                        appPreferences.hasAcceptedTerms = true
                        showTermsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeshCyanPrimary)
                ) {
                    Text("Accept Terms", color = MeshObsidianBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text("Close", color = MeshTextSecondary)
                }
            },
            containerColor = MeshSlateSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Delete Account Data Dialog
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = {
                Text("Delete Account & Local Data?", color = MeshTextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "This will permanently wipe all local 2-of-3 key shares, reset your Node Identity, and clear app preferences in compliance with Google Play Policy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeshTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        appPreferences.deleteAccountData()
                        viewModel.signOutAccount()
                        showDeleteAccountDialog = false
                        Toast.makeText(context, "Account & key data erased", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeshAmberWarning)
                ) {
                    Text("Delete Everything", color = MeshObsidianBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel", color = MeshTextSecondary)
                }
            },
            containerColor = MeshSlateSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
