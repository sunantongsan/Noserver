package com.example.p2p

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * AppPreferences manages first-launch onboarding status, privacy consent, and account creation flags.
 * Includes Google Play Policy compliant account data deletion methods.
 */
class AppPreferences(private val context: Context) {

    private val tag = "AppPreferences"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("NoserverAppPrefs", Context.MODE_PRIVATE)

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean("is_first_launch", true)
        set(value) = prefs.edit().putBoolean("is_first_launch", value).apply()

    var isAccountCreated: Boolean
        get() = prefs.getBoolean("is_account_created", false)
        set(value) = prefs.edit().putBoolean("is_account_created", value).apply()

    var hasAcceptedTerms: Boolean
        get() = prefs.getBoolean("has_accepted_terms", false)
        set(value) = prefs.edit().putBoolean("has_accepted_terms", value).apply()

    var userEmail: String
        get() = prefs.getString("user_email", "") ?: ""
        set(value) = prefs.edit().putString("user_email", value).apply()

    fun completeOnboarding(email: String) {
        prefs.edit()
            .putBoolean("is_first_launch", false)
            .putBoolean("is_account_created", true)
            .putBoolean("has_accepted_terms", true)
            .putString("user_email", email)
            .apply()
        Log.i(tag, "Onboarding completed successfully for $email")
    }

    /**
     * Google Play Account Deletion Readiness.
     * Clears all local user credentials, identity shares, and resets first-launch flags.
     */
    fun deleteAccountData(): Boolean {
        return try {
            prefs.edit().clear().apply()

            // Also clear ThresholdKeyPrefs if present
            val thresholdPrefs = context.getSharedPreferences("ThresholdKeyPrefs", Context.MODE_PRIVATE)
            thresholdPrefs.edit().clear().apply()

            Log.i(tag, "All local account data and threshold key shares deleted for Google Play compliance.")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete account data: ${e.message}")
            false
        }
    }
}
