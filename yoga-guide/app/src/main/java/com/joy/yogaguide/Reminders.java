package com.joy.yogaguide;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/** Scheduling and posting of the one daily practice reminder. */
final class Reminders {

    static final String CHANNEL_ID = "practice-reminder";
    static final String UNIQUE_WORK = "yg-daily-reminder";
    static final String KEY_HOUR = "hour";
    static final String KEY_MINUTE = "minute";
    private static final int NOTIFICATION_ID = 1;

    private Reminders() {
    }

    static void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription(ctx.getString(R.string.reminder_channel_desc));
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        // Idempotent: re-creating an existing channel updates nothing the user has
        // since changed, so it is safe to call on every launch and every fire.
        if (nm != null) nm.createNotificationChannel(ch);
    }

    /**
     * Chained one-shot work rather than PeriodicWorkRequest. A periodic request
     * only guarantees one run per interval, not a run at a particular clock time,
     * so "remind me at 6:30" would drift to whenever the window happened to open.
     */
    static void schedule(Context ctx, int hour, int minute) {
        Data input = new Data.Builder()
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .build();
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(ReminderWorker.class)
                .setInitialDelay(millisUntilNext(hour, minute), TimeUnit.MILLISECONDS)
                .setInputData(input)
                .build();
        WorkManager.getInstance(ctx)
                .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, req);
    }

    static void cancel(Context ctx) {
        WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE_WORK);
    }

    static long millisUntilNext(int hour, int minute) {
        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        // Rolling a whole day forward when the time has already passed today also
        // covers the exactly-equal case, where a zero delay would fire instantly
        // and then immediately reschedule.
        if (next.getTimeInMillis() <= now.getTimeInMillis()) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        return next.getTimeInMillis() - now.getTimeInMillis();
    }

    static void post(Context ctx) {
        Intent open = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                ctx, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ctx.getString(R.string.reminder_title))
                .setContentText(ctx.getString(R.string.reminder_text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);
        // areNotificationsEnabled covers both the API 33 runtime permission and a
        // user who switched the channel off by hand; notify() would silently
        // no-op in either case, so checking just keeps the intent explicit.
        if (nm.areNotificationsEnabled()) {
            try {
                nm.notify(NOTIFICATION_ID, b.build());
            } catch (SecurityException ignored) {
                // Permission revoked between the check and the post.
            }
        }
    }
}
