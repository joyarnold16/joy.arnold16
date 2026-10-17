package com.joy.yogaguide;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Posts the daily reminder, then queues tomorrow's.
 *
 * Re-arming from inside the worker is what makes the reminder survive a reboot
 * or a force-stop: WorkManager persists the enqueued request in its own database,
 * so there is no BOOT_COMPLETED receiver to maintain.
 */
public class ReminderWorker extends Worker {

    public ReminderWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        int hour = getInputData().getInt(Reminders.KEY_HOUR, 7);
        int minute = getInputData().getInt(Reminders.KEY_MINUTE, 0);

        Reminders.createChannel(ctx);
        Reminders.post(ctx);
        Reminders.schedule(ctx, hour, minute);

        return Result.success();
    }
}
