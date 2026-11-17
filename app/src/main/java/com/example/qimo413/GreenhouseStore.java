package com.example.qimo413;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;

/** Local state for the greenhouse simulator. */
public final class GreenhouseStore {
    private static final String PREFS = "greenhouse_demo";
    private GreenhouseStore() {}
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    public static boolean deviceOn(Context context, String key) { return prefs(context).getBoolean("device_" + key, "vent".equals(key)); }
    public static void setDevice(Context context, String key, boolean on) { prefs(context).edit().putBoolean("device_" + key, on).apply(); }
    public static boolean automaticMode(Context context, String key) { return prefs(context).getBoolean("automatic_" + key, true); }
    public static void setAutomaticMode(Context context, String key, boolean automatic) { prefs(context).edit().putBoolean("automatic_" + key, automatic).apply(); }
    public static String crop(Context context) {
        String crop = prefs(context).getString("crop", "西红柿");
        return "番茄".equals(crop) ? "西红柿" : crop;
    }
    public static void setCrop(Context context, String crop) { prefs(context).edit().putString("crop", crop).apply(); }

    /** 保存 AI 识别到的自定义作物温度范围。 */
    public static void setCustomCrop(Context context, String name, float tempMin, float tempMax) {
        prefs(context).edit()
                .putFloat("crop_custom_" + name + "_min", tempMin)
                .putFloat("crop_custom_" + name + "_max", tempMax)
                .apply();
    }
    public static boolean isCustomCrop(Context context, String name) {
        return prefs(context).contains("crop_custom_" + name + "_min");
    }
    public static float customCropTempMin(Context context, String name) {
        return prefs(context).getFloat("crop_custom_" + name + "_min", 15f);
    }
    public static float customCropTempMax(Context context, String name) {
        return prefs(context).getFloat("crop_custom_" + name + "_max", 30f);
    }
    /** 返回所有 AI 识别过的自定义作物名列表。 */
    public static java.util.List<String> customCropNames(Context context) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String key : prefs(context).getAll().keySet()) {
            if (key.startsWith("crop_custom_") && key.endsWith("_min")) {
                result.add(key.substring("crop_custom_".length(), key.length() - 4));
            }
        }
        return result;
    }

    public static float sensor(Context context, String key) {
        float fallback = "temperature".equals(key) ? 26.4f : "humidity".equals(key) ? 68f : "soil".equals(key) ? 42f : 18f;
        return prefs(context).getFloat("sensor_" + key, fallback);
    }
    public static void setSensor(Context context, String key, float value) { prefs(context).edit().putFloat("sensor_" + key, value).apply(); }

    public static float threshold(Context context, String crop, String key) {
        CropProfile profile = CropProfile.forName(context, crop);
        float fallback;
        if ("temperature".equals(key)) fallback = profile.temperatureMax;
        else if ("humidity".equals(key)) fallback = profile.humidityMax;
        else if ("soil".equals(key)) fallback = profile.soilMoistureMin;
        else fallback = profile.lightMin;
        return prefs(context).getFloat("threshold_" + crop + "_" + key, fallback);
    }
    public static void setThreshold(Context context, String crop, String key, float value) { prefs(context).edit().putFloat("threshold_" + crop + "_" + key, value).apply(); }
    public static void resetThresholds(Context context, String crop) {
        SharedPreferences.Editor edit = prefs(context).edit();
        for (String key : new String[]{"temperature", "humidity", "soil", "light"}) edit.remove("threshold_" + crop + "_" + key);
        edit.apply();
    }
    public static boolean thresholdCustomized(Context context, String crop, String key) {
        return prefs(context).contains("threshold_" + crop + "_" + key);
    }

    public static String advice(Context context) {
        return String.format(Locale.CHINA, "当前%s：温度 %.1f°C、湿度 %.0f%%、土壤湿度 %.0f%%、光照 %.1f klx。\n自动控制：通风%s，灌溉%s，补光%s。",
                crop(context), sensor(context, "temperature"), sensor(context, "humidity"), sensor(context, "soil"), sensor(context, "light"),
                deviceOn(context, "vent") ? "开启" : "关闭", deviceOn(context, "pump") ? "开启" : "关闭", deviceOn(context, "lamp") ? "开启" : "关闭");
    }

    /** Recalculate automatic controls without adding history (startup, crop or threshold edits). */
    public static void applyAutomation(Context context) { applyAutomation(context, false); }

    /** Record automatic device transitions only after a simulated sensor update. */
    public static void applyAutomation(Context context, boolean recordSensorTransitions) {
        String crop = crop(context);
        float temperature = sensor(context, "temperature"), humidity = sensor(context, "humidity"), soil = sensor(context, "soil"), light = sensor(context, "light");
        // Devices are fail-safe on recovery: ventilation is forced off when
        // temperature falls below the crop minimum; irrigation and lighting stop
        // as soon as their low-side thresholds are reached.
        boolean ventilationNeeded = (humidity > threshold(context, crop, "humidity")
                || temperature > threshold(context, crop, "temperature"))
                && temperature >= CropProfile.forName(context, crop).temperatureMin;
        boolean irrigationNeeded = soil < threshold(context, crop, "soil");
        boolean lightingNeeded = light < threshold(context, crop, "light");
        boolean[] desired = {ventilationNeeded, irrigationNeeded, lightingNeeded};
        String[] keys = {"vent", "pump", "lamp"}, names = {"通风机", "灌溉水泵", "补光灯"};
        EventDbHelper history = recordSensorTransitions ? new EventDbHelper(context) : null;
        for (int i = 0; i < keys.length; i++) {
            boolean previous = deviceOn(context, keys[i]);
            boolean previousNeed = prefs(context).getBoolean("automation_need_" + keys[i], previous);
            boolean automatic = automaticMode(context, keys[i]);
            if (automatic && previous != desired[i]) setDevice(context, keys[i], desired[i]);
            if (history != null && automatic && previous != desired[i] && previousNeed != desired[i]) {
                String readings;
                if (i == 0) readings = String.format(Locale.CHINA, "空气温度 %.1f°C（上限 %.1f°C），空气湿度 %.0f%%（上限 %.0f%%）",
                        temperature, threshold(context, crop, "temperature"), humidity, threshold(context, crop, "humidity"));
                else if (i == 1) readings = String.format(Locale.CHINA, "土壤湿度 %.0f%%（下限 %.0f%%）", soil, threshold(context, crop, "soil"));
                else readings = String.format(Locale.CHINA, "光照强度 %.1f klx（下限 %.1f klx）", light, threshold(context, crop, "light"));
                history.addDeviceTransition(crop + " · " + names[i] + "自动" + (desired[i] ? "开启" : "关闭"),
                        "测试值：" + readings + "\n设备开关：" + names[i] + (desired[i] ? "开启" : "关闭"));
            }
            prefs(context).edit().putBoolean("automation_need_" + keys[i], desired[i]).apply();
        }
        if (history != null) history.close();
    }
}
