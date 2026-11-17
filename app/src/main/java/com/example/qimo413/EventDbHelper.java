package com.example.qimo413;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** SQLite：记录自动阈值触发的设备开/关历史，支持单条删除与每周过期清理。 */
public class EventDbHelper extends SQLiteOpenHelper {
    private static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;
    public static final class Entry {
        public final long id;
        public final String time, title, detail;
        Entry(long id, String time, String title, String detail) { this.id = id; this.time = time; this.title = title; this.detail = detail; }
    }
    public EventDbHelper(Context context) { super(context, "greenhouse_events.db", null, 3); }
    /** 建表：历史记录 ID、时间、标题、详情、时间戳、事件类型。 */
    @Override public void onCreate(SQLiteDatabase db) { db.execSQL("CREATE TABLE events (_id INTEGER PRIMARY KEY AUTOINCREMENT, time TEXT NOT NULL, title TEXT NOT NULL, detail TEXT NOT NULL, created_at INTEGER NOT NULL, event_type TEXT NOT NULL DEFAULT 'legacy')"); }

    /** 数据库版本升级：v1→v2 补时间戳，v2→v3 补事件类型。 */
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE events ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0");
            // Existing entries only contain MM-dd HH:mm. Infer the most recent occurrence, preserving their order.
            List<Long> ids = new ArrayList<>();
            List<Long> timestamps = new ArrayList<>();
            try (Cursor cursor = db.query("events", new String[]{"_id", "time"}, null, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    long timestamp = System.currentTimeMillis();
                    try {
                        Calendar calendar = Calendar.getInstance();
                        String time = cursor.getString(1);
                        calendar.set(Calendar.MONTH, Integer.parseInt(time.substring(0, 2)) - 1);
                        calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(time.substring(3, 5)));
                        calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(time.substring(6, 8)));
                        calendar.set(Calendar.MINUTE, Integer.parseInt(time.substring(9, 11)));
                        calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0);
                        if (calendar.getTimeInMillis() > timestamp) calendar.add(Calendar.YEAR, -1);
                        timestamp = calendar.getTimeInMillis();
                    } catch (Exception ignored) {}
                    ids.add(cursor.getLong(0));
                    timestamps.add(timestamp);
                }
            }
            for (int i = 0; i < ids.size(); i++) {
                ContentValues values = new ContentValues(); values.put("created_at", timestamps.get(i));
                db.update("events", values, "_id=?", new String[]{Long.toString(ids.get(i))});
            }
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE events ADD COLUMN event_type TEXT NOT NULL DEFAULT 'legacy'");
    }
    /** 插入一条设备自动开/关记录。 */
    public void addDeviceTransition(String title, String detail) {
        ContentValues values = new ContentValues();
        values.put("time", new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date()));
        values.put("title", title); values.put("detail", detail);
        values.put("event_type", "device_transition");
        values.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insert("events", null, values);
    }

    /** 按 ID 删一条记录。 */
    public void delete(long id) { getWritableDatabase().delete("events", "_id=?", new String[]{Long.toString(id)}); }

    /** 删除一周以前的过期记录，返回删除条数。 */
    public int deleteOlderThanWeek() {
        return getWritableDatabase().delete("events", "created_at < ?",
                new String[]{Long.toString(System.currentTimeMillis() - WEEK_MS)});
    }

    /** 读取所有设备自动操作记录，按时间倒序。 */
    public List<Entry> recentEntries() {
        List<Entry> items = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("events", new String[]{"_id", "time", "title", "detail"}, "event_type=?", new String[]{"device_transition"}, null, null, "_id DESC", null)) {
            while (cursor.moveToNext()) items.add(new Entry(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3)));
        }
        return items;
    }
}
