package io.goodmidnight.transfer.core.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
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

    // Limits notification update frequency to prevent system drop/overload
    private var lastNotificationTime = 0L

    companion object {
        const val ACTION_START_FOREGROUND = "ACTION_START_FOREGROUND"
        const val ACTION_STOP_FOREGROUND = "ACTION_STOP_FOREGROUND"
        private const val CHANNEL_ID = "transfer_channel"
        private const val NOTIFICATION_ID = 101

        const val TARGET_PENDING_DEEPLINK_URL = "transfer://io.goodmidnight.transfer/transfer/progress"
        private const val THROTTLE_INTERVAL_MS = 500L
    }

    override fun onCreate() {
        super.onCreate()
        val deepLinkIntent = Intent(
            Intent.ACTION_VIEW,
            TARGET_PENDING_DEEPLINK_URL.toUri()
        ).apply {
            setPackage(packageName)
        }

        pendingIntent = PendingIntent.getActivity(
            this,
            0,
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOREGROUND -> {
                startForeground(NOTIFICATION_ID, createNotification("Preparing connection...", 0))
                observeControllerState()
            }

            ACTION_STOP_FOREGROUND -> {
                // Consider STOP_FOREGROUND_DETACH if you want to leave the notification on completion
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
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
                        TransferState.TransferStatus.CONNECTING -> {
                            updateNotification("Connecting to receiver...", 0)
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
        val channel = NotificationChannel(
            CHANNEL_ID,
            "File Transfer",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("File Transfer")
            .setContentText(content)
            .setProgress(100, progress, progress == 0 && content.contains("Connecting"))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(content: String, progress: Int) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            createNotification(content, progress)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}