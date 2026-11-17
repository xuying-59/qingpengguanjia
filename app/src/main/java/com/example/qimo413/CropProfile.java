package com.example.qimo413;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Illustrative greenhouse targets; actual targets vary by cultivar and growth stage. */
public final class CropProfile {
    public final String name;
    public final float temperatureMin;
    public final float temperatureMax;
    public final float humidityMax;
    public final float soilMoistureMin;
    public final float lightMin;

    private static final List<CropProfile> ALL = Collections.unmodifiableList(Arrays.asList(
            new CropProfile("草莓", 15f, 25f, 85f, 50f, 12f),
            new CropProfile("西红柿", 20f, 28f, 75f, 45f, 12f),
            new CropProfile("黄瓜", 20f, 30f, 85f, 50f, 15f),
            new CropProfile("蝴蝶兰", 18f, 28f, 80f, 35f, 8f),
            new CropProfile("桃子", 18f, 28f, 75f, 40f, 18f),
            new CropProfile("葡萄", 20f, 30f, 75f, 45f, 18f)
    ));

    private CropProfile(String name, float temperatureMin, float temperatureMax,
                        float humidityMax, float soilMoistureMin, float lightMin) {
        this.name = name;
        this.temperatureMin = temperatureMin;
        this.temperatureMax = temperatureMax;
        this.humidityMax = humidityMax;
        this.soilMoistureMin = soilMoistureMin;
        this.lightMin = lightMin;
    }

    public static List<CropProfile> all() { return ALL; }

    public static CropProfile forName(String name) {
        if ("番茄".equals(name)) return ALL.get(1);
        for (CropProfile profile : ALL) if (profile.name.equals(name)) return profile;
        return ALL.get(1);
    }

    /** 带 Context 的版本：AI 识别到的自定义作物也能拿到温度范围。 */
    public static CropProfile forName(android.content.Context context, String name) {
        if ("番茄".equals(name)) return ALL.get(1);
        for (CropProfile profile : ALL) if (profile.name.equals(name)) return profile;
        if (context != null && GreenhouseStore.isCustomCrop(context, name)) {
            float min = GreenhouseStore.customCropTempMin(context, name);
            float max = GreenhouseStore.customCropTempMax(context, name);
            return new CropProfile(name, min, max, 75f, 40f, 10f);
        }
        return ALL.get(1);
    }
}
