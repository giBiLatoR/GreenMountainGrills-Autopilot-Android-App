package com.gibilator.gmg.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gibilator.gmg.R
import com.gibilator.gmg.cook.CookNotifier
import java.util.concurrent.atomic.AtomicInteger

/** Channel ids. */
object Channels {
    const val MILESTONES = "gmg_milestones"
    const val SERVICE = "gmg_service"

    fun ensure(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannel(
            NotificationChannel(MILESTONES, "Cook milestones", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Grill ready, the stall, it's done, and other cook updates."
            },
        )
        mgr.createNotificationChannel(
            NotificationChannel(SERVICE, "Monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ongoing grill monitoring."
            },
        )
    }
}

/** Posts cook milestones as Android local notifications. Port of `_notify`/`_dispatch_push`. */
class AndroidCookNotifier(private val context: Context) : CookNotifier {
    private val ids = AtomicInteger(1000)

    override fun notify(title: String, message: String, critical: Boolean) {
        Channels.ensure(context)
        val builder = NotificationCompat.Builder(context, Channels.MILESTONES)
            .setSmallIcon(R.drawable.ic_stat_grill)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(if (critical) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        if (critical) builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        runCatching {
            NotificationManagerCompat.from(context).notify(ids.incrementAndGet(), builder.build())
        }
    }
}
