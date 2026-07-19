package willykez.gitflowmobile.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import willykez.gitflowmobile.MainActivity
import willykez.gitflowmobile.R

/**
 * Posts the "new commits available" notification background sync produces.
 * Deliberately just one summary notification per sync run (not one per
 * repo) — background fetch can touch a dozen repos, and a dozen separate
 * notifications for a fetch-only job (nothing was actually pulled/merged,
 * just noticed) would train people to swipe them away unread.
 */
object SyncNotifications {
    private const val CHANNEL_ID = "background_sync"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID, "Background sync", NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Lets you know when a background fetch finds new commits to pull"
        }
        manager.createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true // no runtime permission before API 33
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** [reposWithNewCommits] are repo *names*, not paths — just for display. */
    fun notifyNewCommits(context: Context, reposWithNewCommits: List<String>) {
        if (reposWithNewCommits.isEmpty() || !hasPermission(context)) return
        ensureChannel(context)

        val title = if (reposWithNewCommits.size == 1) {
            "New commits in ${reposWithNewCommits.first()}"
        } else {
            "New commits in ${reposWithNewCommits.size} repos"
        }
        val body = reposWithNewCommits.take(5).joinToString(", ") +
            if (reposWithNewCommits.size > 5) ", and ${reposWithNewCommits.size - 5} more" else ""

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // hasPermission() above already confirmed POST_NOTIFICATIONS on API 33+;
        // this catch is a last-resort guard against any other manufacturer-specific
        // notification restriction, not a substitute for that check.
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // notification permission revoked between the check above and this call — skip silently
        }
    }
}
