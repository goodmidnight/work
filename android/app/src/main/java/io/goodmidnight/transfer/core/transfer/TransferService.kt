package io.goodmidnight.transfer.core.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TransferService : Service() {

    @Inject
    lateinit var transferController: TransferController
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingIntent: PendingIntent? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private var lastNotificationTime = 0L

    companion object {
        private const val TAG = "TransferService"
        const val ACTION_START_FOREGROUND = "ACTION_START_FOREGROUND"
        const val ACTION_STOP_FOREGROUND = "ACTION_STOP_FOREGROUND"
        private const val CHANNEL_ID = "transfer_channel"
        private const val NOTIFICATION_ID = 101
        const val TARGET_PENDING_DEEPLINK_URL = "transfer://io.goodmidnight.transfer/transfer/progress"
        private const val THROTTLE_INTERVAL_MS = 500L
    }

    override fun onCreate() {
        super.onCreate()
        setupLocks()
        val deepLinkIntent = Intent(Intent.ACTION_VIEW, TARGET_PENDING_DEEPLINK_URL.toUri()).apply {
            setPackage(packageName)
        }
        pendingIntent = PendingIntent.getActivity(this, 0, deepLinkIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setupLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TransferApp::WakeLock")

        val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("TransferApp::MulticastLock")
        multicastLock?.setReferenceCounted(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOREGROUND -> {
                Log.d(TAG, "Starting foreground service with locks")
                wakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)
                multicastLock?.acquire()
                startForeground(NOTIFICATION_ID, createNotification("Preparing connection...", 0))
                observeControllerState()
            }
            ACTION_STOP_FOREGROUND -> {
                releaseLocks()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun releaseLocks() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (multicastLock?.isHeld == true) multicastLock?.release()
    }

    private fun observeControllerState() {
        serviceScope.launch {
            transferController.state
                .transform { state ->
                    if (state.status == TransferState.TransferStatus.TRANSFERRING && state.progress != 100) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastNotificationTime >= THROTTLE_INTERVAL_MS) {
                            lastNotificationTime = currentTime
                            emit(state)
                        }
                    } else emit(state)
                }
                .collect { state ->
                    when (state.status) {
                        TransferState.TransferStatus.LISTENING -> {
                            updateNotification("Waiting for incoming files...", 0)
                        }
                        TransferState.TransferStatus.CONNECTING -> {
                            updateNotification("Connecting to peer...", 0)
                        }
                        TransferState.TransferStatus.TRANSFERRING -> {
                            updateNotification("Transferring ${state.currentFileName}...", state.progress)
                        }
                        TransferState.TransferStatus.COMPLETED -> {
                            updateNotification("Transfer completed.", 100)
                        }
                        TransferState.TransferStatus.ERROR -> {
                            updateNotification("Transfer error occurred.", 0)
                        }
                        else -> {}
                    }
                }
        }
    }

    private fun createNotification(content: String, progress: Int): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "File Transfer", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("File Transfer")
            .setContentText(content)
            .setProgress(100, progress, progress == 0 && (content.contains("Connecting") || content.contains("Waiting")))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(content: String, progress: Int) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, createNotification(content, progress))
    }

    override fun onDestroy() {
        releaseLocks()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
