package com.gibilator.gmg.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gibilator.gmg.GmgApp
import com.gibilator.gmg.R
import com.gibilator.gmg.data.ConnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service running the poll/control loop for the active grill.
 * Replaces the HA coordinator's periodic refresh — keeps monitoring alive when
 * the app is backgrounded.
 */
class PollService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat(buildNotification("Monitoring your grill", null))
        if (loop == null) loop = scope.launch { runLoop() }
        return START_STICKY
    }

    private suspend fun runLoop() {
        val repo = GmgApp.repo
        while (scope.isActive) {
            val prefs = runCatching { repo.prefs.flow.first() }.getOrNull()
            if (prefs != null) repo.applyPrefs(prefs)
            runCatching { repo.pollOnce() }
            val st = repo.state.value
            val grill = st.snapshot?.grillTemp
            val text = when {
                st.conn == ConnState.ServerMode -> "Grill in Server Mode — can't reach it"
                grill != null -> "Grill ${grill}°F" + (st.cook?.probeF?.let { " · Food ${it}°F" } ?: "")
                else -> "Connecting…"
            }
            updateNotification(buildNotification(st.info?.model ?: "GMG Control", text))
            delay(((prefs?.scanIntervalS ?: 15) * 1000L))
        }
    }

    private fun buildNotification(title: String, text: String?): Notification =
        NotificationCompat.Builder(this, Channels.SERVICE)
            .setSmallIcon(R.drawable.ic_stat_grill)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(SERVICE_ID, notification)
        }
    }

    private fun updateNotification(notification: Notification) {
        runCatching {
            getSystemService(NOTIFICATION_SERVICE)
            androidx.core.app.NotificationManagerCompat.from(this).notify(SERVICE_ID, notification)
        }
    }

    override fun onDestroy() {
        loop?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val SERVICE_ID = 42
        const val ACTION_STOP = "com.gibilator.gmg.STOP"

        fun start(context: Context) {
            val intent = Intent(context, PollService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, PollService::class.java).setAction(ACTION_STOP))
        }
    }
}
