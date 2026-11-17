package com.example.qimo413;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Weekly, inexact cleanup for the simulator's local history. */
public class HistoryCleanupReceiver extends BroadcastReceiver {
    private static final long WEEK_MS = 7L * AlarmManager.INTERVAL_DAY;

    public static void schedule(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        Intent intent = new Intent(context, HistoryCleanupReceiver.class);
        PendingIntent task = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long first = context.getSharedPreferences("greenhouse_demo", Context.MODE_PRIVATE)
                .getLong("next_history_cleanup", 0L);
        long now = System.currentTimeMillis();
        if (first == 0L) first = now + WEEK_MS;
        if (first < now) first = now + 60_000L;
        context.getSharedPreferences("greenhouse_demo", Context.MODE_PRIVATE).edit()
                .putLong("next_history_cleanup", first).apply();
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, first, WEEK_MS, task);
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            schedule(context);
            return;
        }
        new EventDbHelper(context).deleteOlderThanWeek();
        context.getSharedPreferences("greenhouse_demo", Context.MODE_PRIVATE).edit()
                .putLong("next_history_cleanup", System.currentTimeMillis() + WEEK_MS).apply();
    }
}
