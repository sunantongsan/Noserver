package com.example.p2p

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

data class CoinbaseSmartWalletState(
    val isConnected: Boolean = false,
    val userEmail: String = "",
    val smartWalletAddress: String = "",
    val credentialId: String = "",
    val isGoogleSynced: Boolean = true,
    val statusText: String = "Passkey Wallet Standby"
)

/**
 * Adapter bridging Android Passkey WebAuthn credentials directly with NodeIdentityManager
 * and AccountAbstractionEngine to provide a seed-phrase-free Coinbase Smart Wallet experience.
 */
class PasskeyIdentityAdapter(
    private val context: Context,
    private val nodeIdentityManager: NodeIdentityManager,
    private val accountAbstractionEngine: AccountAbstractionEngine
) {
    private val tag = "PasskeyIdentityAdapter"

    private val _walletState = MutableStateFlow(
        CoinbaseSmartWalletState(
            isConnected = false,
            userEmail = "",
            smartWalletAddress = deriveSmartWalletAddress(nodeIdentityManager.publicKeyHex),
            credentialId = "",
            isGoogleSynced = true,
            statusText = "Ready for One-Tap Passkey Sign-In"
        )
    )
    val walletState: StateFlow<CoinbaseSmartWalletState> = _walletState.asStateFlow()

    /**
     * Executes 1-tap Passkey creation/assertion, binds seed entropy to Node Identity and Account Abstraction.
     */
    suspend fun authenticateCoinbaseSmartWallet(
        email: String,
        passkeyWalletManager: PasskeyWalletManager
    ): Result<CoinbaseSmartWalletState> {
        return try {
            Log.i(tag, "Initiating Coinbase Smart Wallet onboarding for email: $email")

            // Step 1: Authenticate or register Passkey via CredentialManager
            val passkeyResult = if (email.isBlank()) {
                passkeyWalletManager.authenticatePasskeyWallet("")
            } else {
                passkeyWalletManager.createPasskeyWallet(email)
            }

            if (!passkeyResult.success) {
                return Result.failure(Exception(passkeyResult.errorMessage.ifBlank { "Passkey authentication failed" }))
            }

            // Step 2: Derive deterministic Smart Account Address from Passkey entropy seed
            val derivedAddress = deriveSmartWalletAddress(
                passkeyResult.userEmail + passkeyResult.credentialId + NodeIdentityManager.bytesToHex(passkeyResult.rawSeedBytes)
            )

            // Step 3: Register/Sync with Account Abstraction Engine
            accountAbstractionEngine.signInWithPasskey(
                passkeyManager = PasskeyAuthManager(context),
                email = passkeyResult.userEmail
            )

            val newState = CoinbaseSmartWalletState(
                isConnected = true,
                userEmail = passkeyResult.userEmail,
                smartWalletAddress = derivedAddress,
                credentialId = passkeyResult.credentialId,
                isGoogleSynced = true,
                statusText = "Smart Wallet Active • Synced via Google Password Manager"
            )

            _walletState.value = newState
            Log.i(tag, "Coinbase Smart Wallet connected successfully: $derivedAddress")
            Result.success(newState)
        } catch (e: Exception) {
            Log.e(tag, "Error in Coinbase Smart Wallet adapter: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Signs data using the Passkey-secured Node Identity without gas or raw private keys.
     */
    fun signUserOperation(payloadData: ByteArray): UserOperation {
        return accountAbstractionEngine.autoSignUserOperation(payloadData)
    }

    fun disconnectWallet() {
        accountAbstractionEngine.signOut()
        _walletState.value = CoinbaseSmartWalletState(
            isConnected = false,
            userEmail = "",
            smartWalletAddress = deriveSmartWalletAddress(nodeIdentityManager.publicKeyHex),
            credentialId = "",
            isGoogleSynced = true,
            statusText = "Wallet Disconnected"
        )
    }

    companion object {
        fun deriveSmartWalletAddress(seedInput: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest("coinbase_smart_account:$seedInput".toByteArray(Charsets.UTF_8))
            val hex = NodeIdentityManager.bytesToHex(hash)
            return "0x" + hex.take(40).lowercase()
        }
    }
}
