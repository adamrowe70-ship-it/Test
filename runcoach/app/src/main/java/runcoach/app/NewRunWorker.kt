package runcoach.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import runcoach.app.data.RunCoachService
import runcoach.core.Decision
import java.util.concurrent.TimeUnit

/**
 * Checks Health Connect for a new run every 30 minutes. When Garmin has synced one, it works
 * out your next session, builds the playlist and sends you a notification.
 */
class NewRunWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        private const val NAME = "new-run-check"
        private const val CHANNEL = "next-run"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<NewRunWorker>(30, TimeUnit.MINUTES).build(),
            )
        }
    }

    override suspend fun doWork(): Result {
        val service = RunCoachService(applicationContext)
        if (!service.health.isAvailable() || !service.health.hasPermissions()) return Result.success()
        // Errors here (no run yet, permission revoked) won't fix themselves on retry; try again next period.
        val result = runCatching { service.processLatestRun() }.getOrNull()
            ?: return Result.success()

        val r = result.report.recommendation
        val title = when (r.decision) {
            Decision.PROGRESS -> "Nice run! Step up next time"
            Decision.REPEAT -> "Good work. Repeat this session"
            Decision.REGRESS -> "Tough one. Ease back a step"
        }
        notify(title, "Next: ${r.nextPlan.describe()}" + if (result.playlistUrl != null) " · playlist ready" else "", result.playlistUrl)
        return Result.success()
    }

    private fun notify(title: String, text: String, playlistUrl: String?) {
        val ctx = applicationContext
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Next run", NotificationManager.IMPORTANCE_DEFAULT))

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
        if (playlistUrl != null) {
            val play = PendingIntent.getActivity(
                ctx, 1, Intent(Intent.ACTION_VIEW, Uri.parse(playlistUrl)), PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(android.R.drawable.ic_media_play, "Open playlist", play)
        }
        NotificationManagerCompat.from(ctx).notify(1, builder.build())
    }
}
