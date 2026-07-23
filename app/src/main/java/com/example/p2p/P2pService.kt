package com.example.p2p

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android Foreground Service maintaining the living P2P node network in the background.
 * Features automatic network handoff (Wi-Fi <-> 4G/5G) detection, battery monitoring,
 * and dynamic state notifications.
 */
class P2pService : Service() {

    private val tag = "P2pService"
    private val notificationChannelId = "LivingMeshP2pChannel"
    private val notificationId = 8801

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    var identityManager: NodeIdentityManager? = null
        private set
    var networkManager: P2pNetworkManager? = null
        private set

    private var batteryReceiver: BroadcastReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _isBatteryThrottled = MutableStateFlow(false)
    val isBatteryThrottled: StateFlow<Boolean> = _isBatteryThrottled.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): P2pService = this@P2pService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "P2pService initializing...")

        val identity = NodeIdentityManager(applicationContext)
        identityManager = identity
        val netManager = P2pNetworkManager(applicationContext, identity)
        networkManager = netManager

        createNotificationChannel()
        startForeground(notificationId, buildNotification("Node Initializing • STUN/mDNS..."))

        registerBatteryMonitoring()
        registerNetworkConnectivityMonitoring()

        netManager.startNetwork()

        // Observe detailed P2pState to update ongoing foreground notification
        scope.launch {
            netManager.p2pState.collect { state ->
                val statusText = when (state) {
                    is P2pState.Connected -> {
                        if (state.activePeersCount == 0) {
                            "Mesh Node Online • Searching peers..."
                        } else {
                            "Mesh Node Connected • ${state.activePeersCount} peer(s) (${state.transportType.name})"
                        }
                    }
                    is P2pState.Connecting -> "Connecting • ${state.step}"
                    is P2pState.Reconnecting -> "Reconnecting (${state.attempt}/${state.maxAttempts} in ${state.nextRetryDelayMs / 1000}s)"
                    is P2pState.Failed -> "Connection Error • ${state.errorMessage}"
                    is P2pState.Disconnected -> "Node Offline"
                }
                updateNotification(statusText)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Log.i(tag, "P2pService destroying...")
        unregisterNetworkConnectivityMonitoring()
        unregisterBatteryMonitoring()

        networkManager?.stopNetwork()
        Log.d(tag, "P2pService destroyed completely")
        super.onDestroy()
    }

    private fun registerNetworkConnectivityMonitoring() {
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(tag, "Network connection established/switched: $network")
                    networkManager?.handleNetworkChanged()
                }

                override fun onLost(network: Network) {
                    Log.w(tag, "Network lost: $network")
                    networkManager?.handleNetworkChanged()
                }
            }

            connectivityManager.registerNetworkCallback(request, networkCallback as ConnectivityManager.NetworkCallback)
        } catch (e: Exception) {
            Log.e(tag, "Failed to register network callback: ${e.message}")
        }
    }

    private fun unregisterNetworkConnectivityMonitoring() {
        networkCallback?.let {
            try {
                val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        networkCallback = null
    }

    private fun registerBatteryMonitoring() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 100

                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL

                    _batteryLevel.value = pct

                    // Smart Throttling Rule: If battery < 20% and not charging, activate power saver
                    val shouldThrottle = pct in 1..20 && !isCharging
                    _isBatteryThrottled.value = shouldThrottle
                    networkManager?.setPowerSavingMode(shouldThrottle)
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    private fun unregisterBatteryMonitoring() {
        batteryReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        batteryReceiver = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Living Mesh Node Network"
            val descriptionText = "Ongoing P2P decentralization node background execution"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(notificationChannelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("The Living Mesh P2P Node")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, buildNotification(contentText))
    }

    companion object {
        const val ACTION_STOP_SERVICE = "com.example.p2p.STOP_SERVICE"
    }
}
