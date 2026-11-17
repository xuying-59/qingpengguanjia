package com.example.qimo413;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 首页：棚内传感器读数、作物切换、拍照识别、阈值设置、室外天气与对比建议。 */
public class DashboardFragment extends Fragment {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextView weatherValue, weatherStatus, weatherClimate, advice, cropTemperatureRange;
    private View dashboard;
    private CropCarousel carousel;
    private Spinner cropSpinner;
    private java.util.ArrayList<String> cropNames;
    private ActivityResultLauncher<String> imagePicker;
    private ActivityResultLauncher<Void> cameraCapture;
    private ActivityResultLauncher<String> cameraPermission;
    private Float outdoorTemperature;
    private String forecastSummary = "";
    private int weatherRequestId;
    private static final int LOCATION_PERMISSION_REQUEST = 431;
    private static final String[] SENSOR_KEYS = {"temperature", "humidity", "soil", "light"};
    private static final String[] SENSOR_NAMES = {"空气温度 (°C)", "空气湿度 (%)", "土壤湿度 (%)", "光照强度 (klx)"};
    private static final String[] THRESHOLD_NAMES = {"最高温度 (°C)", "最高空气湿度 (%)", "最低土壤湿度 (%)", "最低光照强度 (klx)"};

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        imagePicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null && dashboard != null) identifyCrop(uri);
        });
        cameraCapture = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
            if (bitmap != null && dashboard != null) analyzeLocalImage(bitmap);
        });
        cameraPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted && dashboard != null) cameraCapture.launch(null);
            else Toast.makeText(requireContext(), "需要相机权限才能直接拍照，也可以从相册选择。", Toast.LENGTH_LONG).show();
        });
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        View view = inflater.inflate(R.layout.fragment_dashboard, parent, false);
        dashboard = view;
        carousel = new CropCarousel(view);
        weatherValue = view.findViewById(R.id.weather_value); weatherStatus = view.findViewById(R.id.weather_status);
        weatherClimate = view.findViewById(R.id.weather_climate); advice = view.findViewById(R.id.advice_text);
        cropTemperatureRange = view.findViewById(R.id.crop_temperature_range);
        bindSensors(view);
        cropSpinner = view.findViewById(R.id.crop_spinner);
        cropNames = new java.util.ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.crops)));
        // 把 AI 识别过的自定义作物也加进下拉框
        for (String custom : GreenhouseStore.customCropNames(requireContext())) {
            if (!cropNames.contains(custom)) cropNames.add(custom);
        }
        refreshCropSpinner();
        view.findViewById(R.id.recognize_crop_button).setOnClickListener(v -> showImageSourceDialog());
        view.findViewById(R.id.threshold_button).setOnClickListener(v -> editValues(true));
        view.findViewById(R.id.edit_sensor_button).setOnClickListener(v -> editValues(false));
        view.findViewById(R.id.weather_card).setOnClickListener(v -> loadSavedWeather());
        view.findViewById(R.id.weather_location_button).setOnClickListener(v -> requestLocationWeather());
        view.findViewById(R.id.weather_region_button).setOnClickListener(v -> showRegionDialog());
        view.findViewById(R.id.detail_button).setOnClickListener(v -> startActivity(new Intent(requireContext(), AdviceActivity.class)));
        refreshDashboardState();
        loadSavedWeather();
        return view;
    }

    private void refreshCropSpinner() {
        if (cropSpinner == null) return;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, cropNames);
        cropSpinner.setAdapter(adapter);
        int position = cropNames.indexOf(GreenhouseStore.crop(requireContext()));
        cropSpinner.setSelection(Math.max(0, position));
        cropSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View child, int position, long id) {
                GreenhouseStore.setCrop(requireContext(), cropNames.get(position));
                refreshDashboardState();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void refreshDashboardState() { refreshDashboardState(false); }

    private void refreshDashboardState(boolean recordSensorTransitions) {
        if (!isAdded() || dashboard == null) return;
        GreenhouseStore.applyAutomation(requireContext(), recordSensorTransitions);
        refreshSensors();
        refreshAdvice();
        refreshComparison();
        refreshCropProfile();
        refreshClimate();
    }

    private void bindSensors(View view) {
        int[] ids = {R.id.temperature_value, R.id.humidity_value, R.id.soil_value, R.id.light_value};
        for (int i = 0; i < ids.length; i++) {
            final int index = i;
            view.findViewById(ids[i]).setOnClickListener(v -> editSingleSensor(index));
        }
    }

    private void refreshSensors() {
        if (dashboard == null) return;
        int[] ids = {R.id.temperature_value, R.id.humidity_value, R.id.soil_value, R.id.light_value};
        String[] units = {"°C", "%", "%", " klx"};
        for (int i = 0; i < ids.length; i++) ((TextView) dashboard.findViewById(ids[i])).setText(String.format(java.util.Locale.CHINA, i == 0 || i == 3 ? "%.1f%s" : "%.0f%s", GreenhouseStore.sensor(requireContext(), SENSOR_KEYS[i]), units[i]));
    }

    private void refreshAdvice() { if (advice != null) advice.setText(GreenhouseStore.advice(requireContext())); }

    private void refreshCropProfile() {
        if (cropTemperatureRange == null) return;
        CropProfile profile = CropProfile.forName(requireContext(), GreenhouseStore.crop(requireContext()));
        cropTemperatureRange.setText(getString(R.string.crop_temperature_format, profile.temperatureMin, profile.temperatureMax));
    }

    private void refreshClimate() {
        if (weatherClimate == null) return;
        CropProfile profile = CropProfile.forName(requireContext(), GreenhouseStore.crop(requireContext()));
        StringBuilder summary = new StringBuilder();
        if (!forecastSummary.isEmpty()) summary.append(forecastSummary).append('\n');
        summary.append(profile.name).append("适宜温度 ")
                .append(String.format(Locale.CHINA, "%.0f–%.0f°C", profile.temperatureMin, profile.temperatureMax));
        if (outdoorTemperature != null) {
            summary.append("；室外 ").append(String.format(Locale.CHINA, "%.1f°C", outdoorTemperature));
            if (outdoorTemperature < profile.temperatureMin) summary.append("，偏冷");
            else if (outdoorTemperature > profile.temperatureMax) summary.append("，偏热");
            else summary.append("，处于适温范围");
        }
        summary.append("。室外天气仅供参考，棚内管理请以实际传感器和生长阶段为准。");
        weatherClimate.setText(summary.toString());
    }

    private void refreshComparison() {
        if (dashboard == null) return;
        String crop = GreenhouseStore.crop(requireContext());
        StringBuilder comparison = new StringBuilder(crop).append(" · 当前读数 / 控制阈值\n");
        String[] units = {"°C", "%", "%", " klx"};
        CropProfile cropProfile = CropProfile.forName(requireContext(), crop);
        for (int i = 0; i < SENSOR_KEYS.length; i++) {
            String key = SENSOR_KEYS[i];
            boolean upper = i < 2;
            comparison.append(SENSOR_NAMES[i].replace(" (°C)", "").replace(" (%)", "").replace(" (klx)", ""))
                    .append("：").append(String.format(java.util.Locale.CHINA, "%.1f", GreenhouseStore.sensor(requireContext(), key)))
                    .append(units[i]).append(" / ");
            if (i == 0) comparison.append("适宜 ").append(String.format(java.util.Locale.CHINA, "%.0f–", cropProfile.temperatureMin));
            else comparison.append(upper ? "上限 " : "下限 ");
            comparison.append(String.format(java.util.Locale.CHINA, "%.1f", GreenhouseStore.threshold(requireContext(), crop, key))).append(units[i]);
            if (GreenhouseStore.thresholdCustomized(requireContext(), crop, key)) comparison.append("（自定义）");
            boolean triggered = i == 0
                    ? GreenhouseStore.sensor(requireContext(), key) < cropProfile.temperatureMin || GreenhouseStore.sensor(requireContext(), key) > GreenhouseStore.threshold(requireContext(), crop, key)
                    : upper
                    ? GreenhouseStore.sensor(requireContext(), key) > GreenhouseStore.threshold(requireContext(), crop, key)
                    : GreenhouseStore.sensor(requireContext(), key) < GreenhouseStore.threshold(requireContext(), crop, key);
            comparison.append(triggered ? " · 已触发" : " · 正常");
            if (i < SENSOR_KEYS.length - 1) comparison.append('\n');
        }
        ((TextView) dashboard.findViewById(R.id.threshold_comparison)).setText(comparison.toString());
    }

    private void editSingleSensor(int index) {
        EditText input = numberInput(GreenhouseStore.sensor(requireContext(), SENSOR_KEYS[index]));
        new MaterialAlertDialogBuilder(requireContext()).setTitle("修改" + SENSOR_NAMES[index])
                .setMessage(String.format(java.util.Locale.CHINA, "当前%s阈值：%.1f", GreenhouseStore.crop(requireContext()), GreenhouseStore.threshold(requireContext(), GreenhouseStore.crop(requireContext()), SENSOR_KEYS[index])))
                .setView(input).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.confirm, (d, w) -> saveSensor(index, input)).show();
    }

    private void editValues(boolean thresholds) {
        String crop = GreenhouseStore.crop(requireContext());
        String[] names = thresholds ? THRESHOLD_NAMES : SENSOR_NAMES;
        android.widget.LinearLayout fields = new android.widget.LinearLayout(requireContext());
        fields.setOrientation(android.widget.LinearLayout.VERTICAL);
        fields.setPadding(36, 8, 36, 0);
        EditText[] inputs = new EditText[4];
        for (int i = 0; i < inputs.length; i++) {
            float value = thresholds ? GreenhouseStore.threshold(requireContext(), crop, SENSOR_KEYS[i]) : GreenhouseStore.sensor(requireContext(), SENSOR_KEYS[i]);
            TextView label = new TextView(requireContext());
            label.setText(names[i] + String.format(java.util.Locale.CHINA, thresholds ? " · 当前监测 %.1f" : " · 阈值 %.1f",
                    thresholds ? GreenhouseStore.sensor(requireContext(), SENSOR_KEYS[i]) : GreenhouseStore.threshold(requireContext(), crop, SENSOR_KEYS[i])));
            label.setTextColor(getResources().getColor(R.color.muted, requireContext().getTheme()));
            label.setTextSize(13);
            fields.addView(label);
            inputs[i] = numberInput(value);
            inputs[i].setContentDescription(names[i]);
            fields.addView(inputs[i]);
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(requireContext());
        scroll.addView(fields);
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(thresholds ? crop + " · 自动控制阈值" : "棚内模拟监测数据")
                .setView(scroll).setNegativeButton(R.string.cancel, null);
        if (thresholds) dialog.setNeutralButton("恢复预设", (d, w) -> {
            GreenhouseStore.resetThresholds(requireContext(), crop);
            refreshDashboardState();
        });
        dialog.setPositiveButton(R.string.confirm, (d, w) -> {
                    Float[] values = new Float[inputs.length];
                    for (int i = 0; i < inputs.length; i++) {
                        values[i] = readValue(inputs[i], i);
                        if (values[i] == null) {
                            android.widget.Toast.makeText(requireContext(), "请输入有效数值：温度 0–50°C，湿度 0–100%，光照 0–200 klx", android.widget.Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    for (int i = 0; i < inputs.length; i++) {
                        if (thresholds) GreenhouseStore.setThreshold(requireContext(), crop, SENSOR_KEYS[i], values[i]);
                        else GreenhouseStore.setSensor(requireContext(), SENSOR_KEYS[i], values[i]);
                    }
                    refreshDashboardState(!thresholds);
                }).show();
    }

    private EditText numberInput(float value) {
        TextInputEditText input = new TextInputEditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(String.format(java.util.Locale.CHINA, "%.1f", value));
        return input;
    }

    private Float readValue(EditText input, int index) {
        try {
            float value = Float.parseFloat(input.getText().toString().trim());
            float max = index == 0 ? 50f : index == 3 ? 200f : 100f;
            return Float.isFinite(value) && value >= 0f && value <= max ? value : null;
        } catch (Exception ignored) { return null; }
    }

    private void saveSensor(int index, EditText input) {
        Float value = readValue(input, index);
        if (value == null) { android.widget.Toast.makeText(requireContext(), "请输入有效的模拟监测值", android.widget.Toast.LENGTH_SHORT).show(); return; }
        GreenhouseStore.setSensor(requireContext(), SENSOR_KEYS[index], value);
        refreshDashboardState(true);
    }

    private void showImageSourceDialog() {
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.recognize_crop)
                .setItems(R.array.image_sources, (dialog, which) -> {
                    if (which == 0) {
                        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) cameraCapture.launch(null);
                        else cameraPermission.launch(Manifest.permission.CAMERA);
                    }
                    else imagePicker.launch("image/*");
                }).show();
    }

    private void identifyCrop(Uri imageUri) {
        setRecognitionBusy(true);
        android.content.Context context = requireContext().getApplicationContext();
        executor.execute(() -> {
            Bitmap bitmap = null;
            String error = null;
            try {
                bitmap = PhotoDecoder.read(context.getContentResolver(), imageUri, 720);
            } catch (Exception exception) { error = exception.getMessage(); }
            Bitmap loaded = bitmap;
            String failure = error;
            mainHandler.post(() -> {
                if (!isAdded() || dashboard == null) return;
                setRecognitionBusy(false);
                if (loaded == null) Toast.makeText(requireContext(), getString(R.string.recognize_failed), Toast.LENGTH_LONG).show();
                else analyzeLocalImage(loaded);
                if (loaded == null && failure != null) showManualCropDialog();
            });
        });
    }

    private void setRecognitionBusy(boolean busy) {
        if (dashboard == null) return;
        View button = dashboard.findViewById(R.id.recognize_crop_button);
        button.setEnabled(!busy);
        ((com.google.android.material.button.MaterialButton) button).setText(busy ? R.string.recognize_working : R.string.recognize_crop);
    }

    /** 拍照识别作物：后台调智谱 GLM-4V 识别植物，成功后弹窗确认加入下拉列表 */
    private void analyzeLocalImage(Bitmap bitmap) {
        setRecognitionBusy(true);
        executor.execute(() -> {
            CloudAdvisor.CropResult result = null;
            String error = null;
            try {
                result = CloudAdvisor.recognizeCrop(bitmap);
            } catch (Exception e) { error = e.getMessage(); }
            CloudAdvisor.CropResult finalResult = result;
            String failure = error;
            mainHandler.post(() -> {
                if (!isAdded() || dashboard == null) return;
                setRecognitionBusy(false);
                if (finalResult == null) {
                    Toast.makeText(requireContext(), "AI 识别失败：" + (failure == null ? "网络异常" : failure), Toast.LENGTH_LONG).show();
                    showManualCropDialog();
                } else {
                    showRecognitionConfirmation(bitmap, finalResult);
                }
            });
        });
    }

    private void showRecognitionConfirmation(Bitmap bitmap, CloudAdvisor.CropResult result) {
        android.widget.LinearLayout content = new android.widget.LinearLayout(requireContext());
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.ImageView preview = new android.widget.ImageView(requireContext());
        preview.setImageBitmap(bitmap);
        preview.setAdjustViewBounds(true);
        preview.setMaxHeight((int) (200 * getResources().getDisplayMetrics().density));
        preview.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        content.addView(preview, new android.widget.LinearLayout.LayoutParams(-1, -2));
        TextView detail = new TextView(requireContext());
        detail.setText(String.format(Locale.CHINA,
                "AI 识别结果：%s\n适宜温度 %.0f–%.0f°C\n%s",
                result.name, result.tempMin, result.tempMax, result.description));
        detail.setTextSize(14);
        detail.setPadding(0, 16, 0, 0);
        content.addView(detail);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("切换为 " + result.name + "？")
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton("手动搜索", (dialog, which) -> showManualCropDialog())
                .setPositiveButton("确认切换", (dialog, which) -> applyRecognizedCrop(result))
                .show();
    }

    private void applyRecognizedCrop(CloudAdvisor.CropResult result) {
        String name = result.name;
        if (!cropNames.contains(name)) {
            cropNames.add(name);
            GreenhouseStore.setCustomCrop(requireContext(), name, result.tempMin, result.tempMax);
        } else if (!GreenhouseStore.isCustomCrop(requireContext(), name)) {
            // 预置作物，直接用预设参数
        } else {
            GreenhouseStore.setCustomCrop(requireContext(), name, result.tempMin, result.tempMax);
        }
        refreshCropSpinner();
        int position = cropNames.indexOf(name);
        if (position >= 0) cropSpinner.setSelection(position);
    }

    private void showManualCropDialog() {
        String[] options = new String[cropNames.size() + 1];
        for (int i = 0; i < cropNames.size(); i++) options[i] = cropNames.get(i);
        options[cropNames.size()] = "＋ 搜索新作物…";
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.crop_select_title)
                .setItems(options, (dialog, which) -> {
                    if (which == cropNames.size()) {
                        showSearchCropDialog();
                    } else {
                        cropSpinner.setSelection(which);
                    }
                }).show();
    }

    private void showSearchCropDialog() {
        TextInputEditText input = new TextInputEditText(requireContext());
        input.setSingleLine(true);
        input.setHint("输入作物名称，如：薄荷、罗勒、生菜");
        new MaterialAlertDialogBuilder(requireContext()).setTitle("搜索新作物")
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("添加", (dialog, which) -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    if (!cropNames.contains(name)) {
                        cropNames.add(name);
                        GreenhouseStore.setCustomCrop(requireContext(), name, 15f, 30f);
                    }
                    refreshCropSpinner();
                    int position = cropNames.indexOf(name);
                    if (position >= 0) cropSpinner.setSelection(position);
                }).show();
    }

    private void loadSavedWeather() {
        android.content.SharedPreferences settings = requireContext().getSharedPreferences("weather_settings", android.content.Context.MODE_PRIVATE);
        double latitude = Double.longBitsToDouble(settings.getLong("latitude", Double.doubleToLongBits(39.9042)));
        double longitude = Double.longBitsToDouble(settings.getLong("longitude", Double.doubleToLongBits(116.4074)));
        String place = settings.getString("place", "北京市 · 北京市 · 东城区");
        loadWeather(latitude, longitude, place);
    }

    /** 联网查询 Open-Meteo 天气：当前温度湿度 + 未来 3 天预报，与作物适温对比 */
    private void loadWeather(double latitude, double longitude, String place) {
        if (weatherValue == null || !isAdded()) return;
        int requestId = ++weatherRequestId;
        weatherValue.setText(R.string.weather_loading);
        weatherStatus.setText(place + " · Open-Meteo");
        forecastSummary = "";
        outdoorTemperature = null;
        refreshClimate();
        String retryText = getString(R.string.weather_retry);
        executor.execute(() -> {
            String value;
            String status;
            String forecast = "";
            Float temperature = null;
            HttpURLConnection connection = null;
            try {
                String address = "https://api.open-meteo.com/v1/forecast?latitude=" + latitude + "&longitude=" + longitude
                        + "&current=temperature_2m,relative_humidity_2m,precipitation"
                        + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code&forecast_days=3&timezone=auto";
                connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setConnectTimeout(8000); connection.setReadTimeout(8000); connection.setRequestProperty("Accept", "application/json");
                if (connection.getResponseCode() != 200) throw new IllegalStateException("Weather service unavailable");
                String json;
                try (InputStream input = connection.getInputStream()) { json = NetUtil.readUtf8(input); }
                JSONObject data = new JSONObject(json);
                JSONObject current = data.getJSONObject("current");
                temperature = (float) current.getDouble("temperature_2m");
                value = String.format(Locale.CHINA, "%.1f°C · 湿度 %d%%", temperature, current.getInt("relative_humidity_2m"));
                status = place + " · Open-Meteo · " + current.optString("time", "");
                JSONObject daily = data.getJSONObject("daily");
                JSONArray dates = daily.getJSONArray("time"), highs = daily.getJSONArray("temperature_2m_max"), lows = daily.getJSONArray("temperature_2m_min");
                JSONArray rain = daily.optJSONArray("precipitation_probability_max");
                JSONArray codes = daily.optJSONArray("weather_code");
                StringBuilder days = new StringBuilder("未来3天：");
                for (int i = 0; i < dates.length(); i++) {
                    String date = dates.getString(i);
                    days.append('\n').append(date.length() >= 10 ? date.substring(5) : date).append(' ');
                    if (codes != null && i < codes.length() && !codes.isNull(i)) days.append(weatherDescription(codes.getInt(i))).append(' ');
                    days.append(String.format(Locale.CHINA, "%.0f–%.0f°C", lows.getDouble(i), highs.getDouble(i)));
                    if (rain != null && i < rain.length() && !rain.isNull(i)) days.append(" 降水概率 ").append(rain.getInt(i)).append('%');
                }
                forecast = days.toString();
            } catch (Exception exception) {
                value = retryText;
                status = "网络不可用或天气服务暂时异常 · 点击刷新";
                forecast = "";
            } finally { if (connection != null) connection.disconnect(); }
            String result = value, caption = status, days = forecast;
            Float currentTemperature = temperature;
            mainHandler.post(() -> {
                if (isAdded() && dashboard != null && requestId == weatherRequestId) {
                    weatherValue.setText(result); weatherStatus.setText(caption);
                    outdoorTemperature = currentTemperature; forecastSummary = days; refreshClimate();
                }
            });
        });
    }

    private String weatherDescription(int code) {
        if (code == 0) return "晴";
        if (code <= 3) return "多云";
        if (code == 45 || code == 48) return "雾";
        if (code >= 51 && code <= 57) return "毛毛雨";
        if (code >= 61 && code <= 67) return "雨";
        if (code >= 71 && code <= 77) return "雪";
        if (code >= 80 && code <= 82) return "阵雨";
        if (code >= 85 && code <= 86) return "阵雪";
        if (code >= 95) return "雷雨";
        return "天气";
    }

    private void showRegionDialog() {
        TextInputEditText input = new TextInputEditText(requireContext());
        input.setSingleLine(true);
        input.setHint(R.string.weather_region_hint);
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.weather_region)
                .setView(input).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, which) -> searchRegion(input.getText() == null ? "" : input.getText().toString().trim()))
                .show();
    }

    private void searchRegion(String query) {
        if (query.isEmpty()) return;
        weatherStatus.setText("正在查询 " + query + "…");
        android.content.Context context = requireContext().getApplicationContext();
        executor.execute(() -> {
            String error = null;
            double latitude = 0d, longitude = 0d;
            String place = query;
            HttpURLConnection connection = null;
            try {
                String address = "https://geocoding-api.open-meteo.com/v1/search?name=" + URLEncoder.encode(query, "UTF-8") + "&count=10&language=zh&format=json";
                connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
                if (connection.getResponseCode() != 200) throw new IllegalStateException("Geocoding failed");
                String response;
                try (InputStream input = connection.getInputStream()) { response = NetUtil.readUtf8(input); }
                JSONArray results = new JSONObject(response).optJSONArray("results");
                if (results == null || results.length() == 0) throw new IllegalArgumentException("No region found");
                JSONObject first = results.getJSONObject(0);
                latitude = first.getDouble("latitude"); longitude = first.getDouble("longitude");
                place = composePlace(first, query);
                if (place.contains("区县未知")) {
                    String reverseGeocoded = resolveAdministrativeName(context, latitude, longitude);
                    if (!reverseGeocoded.contains("区县未知")) place = reverseGeocoded;
                }
            } catch (Exception exception) { error = exception.getMessage(); }
            finally { if (connection != null) connection.disconnect(); }
            String failure = error, foundPlace = place;
            double lat = latitude, lon = longitude;
            mainHandler.post(() -> {
                if (!isAdded() || dashboard == null) return;
                if (failure != null) Toast.makeText(requireContext(), getString(R.string.weather_search_failed), Toast.LENGTH_LONG).show();
                else {
                    requireContext().getSharedPreferences("weather_settings", android.content.Context.MODE_PRIVATE).edit()
                            .putLong("latitude", Double.doubleToLongBits(lat)).putLong("longitude", Double.doubleToLongBits(lon)).putString("place", foundPlace).apply();
                    loadWeather(lat, lon, foundPlace);
                }
            });
        });
    }

    private String composePlace(JSONObject result, String query) {
        String province = result.optString("admin1", "").trim();
        String city = result.optString("admin2", "").trim();
        String district = result.optString("admin3", "").trim();
        if (district.isEmpty()) district = result.optString("admin4", "").trim();
        String name = result.optString("name", "").trim();
        if (district.isEmpty() && !name.isEmpty() && !name.equals(city) && !name.equals(province)) district = name;
        String normalizedQuery = query == null ? "" : query.trim();
        if (district.isEmpty() && (normalizedQuery.endsWith("区") || normalizedQuery.endsWith("县")
                || normalizedQuery.endsWith("旗") || normalizedQuery.endsWith("镇") || normalizedQuery.endsWith("乡"))) {
            district = normalizedQuery;
        }
        return administrativeLabel(province, city, district);
    }

    private String administrativeLabel(String province, String city, String district) {
        return (province.isEmpty() ? "省份未知" : province) + " · "
                + (city.isEmpty() ? "城市未知" : city) + " · "
                + (district.isEmpty() ? "区县未知" : district);
    }

    private void requestLocationWeather() {
        if (requireContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && requireContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }
        locateWeather();
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_PERMISSION_REQUEST) return;
        boolean allowed = false;
        for (int result : grantResults) allowed |= result == PackageManager.PERMISSION_GRANTED;
        if (allowed) locateWeather();
        else Toast.makeText(requireContext(), R.string.weather_permission, Toast.LENGTH_LONG).show();
    }

    /** GPS 定位当前位置，反解析省市区县后查询天气 */
    private void locateWeather() {
        LocationManager manager = (LocationManager) requireContext().getSystemService(android.content.Context.LOCATION_SERVICE);
        String provider = null;
        try {
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) provider = LocationManager.NETWORK_PROVIDER;
            else if (requireContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) provider = LocationManager.GPS_PROVIDER;
        } catch (SecurityException ignored) { }
        if (provider == null) {
            Toast.makeText(requireContext(), R.string.weather_location_failed, Toast.LENGTH_LONG).show();
            return;
        }
        weatherStatus.setText("正在获取当前位置…");
        final String selectedProvider = provider;
        Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] finished = {false};
        Location cached = null;
        try { cached = manager.getLastKnownLocation(provider); } catch (SecurityException ignored) { }
        Location fallback = cached;
        Runnable timeout = () -> {
            if (finished[0]) return;
            finished[0] = true;
            if (!isAdded() || dashboard == null) return;
            if (fallback != null) useCurrentLocation(fallback);
            else Toast.makeText(requireContext(), R.string.weather_location_failed, Toast.LENGTH_LONG).show();
        };
        handler.postDelayed(timeout, 12000L);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                manager.getCurrentLocation(selectedProvider, new CancellationSignal(), command -> handler.post(command), location -> {
                    if (finished[0]) return;
                    finished[0] = true; handler.removeCallbacks(timeout);
                    if (!isAdded() || dashboard == null) return;
                    if (location != null) useCurrentLocation(location);
                    else if (fallback != null) useCurrentLocation(fallback);
                    else Toast.makeText(requireContext(), R.string.weather_location_failed, Toast.LENGTH_LONG).show();
                });
            } catch (SecurityException exception) { handler.removeCallbacks(timeout); Toast.makeText(requireContext(), R.string.weather_location_failed, Toast.LENGTH_LONG).show(); }
        } else {
            LocationListener listener = new LocationListener() {
                @Override public void onLocationChanged(@NonNull Location location) {
                    if (finished[0]) return;
                    finished[0] = true; handler.removeCallbacks(timeout); manager.removeUpdates(this); useCurrentLocation(location);
                }
                @Override public void onProviderEnabled(@NonNull String name) { }
                @Override public void onProviderDisabled(@NonNull String name) { }
            };
            try { manager.requestSingleUpdate(provider, listener, Looper.getMainLooper()); }
            catch (SecurityException exception) { handler.removeCallbacks(timeout); Toast.makeText(requireContext(), R.string.weather_location_failed, Toast.LENGTH_LONG).show(); }
        }
    }

    private void useCurrentLocation(Location location) {
        if (!isAdded() || dashboard == null) return;
        double latitude = location.getLatitude(), longitude = location.getLongitude();
        weatherStatus.setText("正在解析省、市、区县…");
        android.content.Context context = requireContext().getApplicationContext();
        executor.execute(() -> {
            String place = resolveAdministrativeName(context, latitude, longitude);
            mainHandler.post(() -> {
                if (!isAdded() || dashboard == null) return;
                context.getSharedPreferences("weather_settings", android.content.Context.MODE_PRIVATE).edit()
                        .putLong("latitude", Double.doubleToLongBits(latitude))
                        .putLong("longitude", Double.doubleToLongBits(longitude)).putString("place", place).apply();
                loadWeather(latitude, longitude, place);
            });
        });
    }

    private String resolveAdministrativeName(android.content.Context context, double latitude, double longitude) {
        try {
            if (!Geocoder.isPresent()) return administrativeLabel("", "", "");
            Geocoder geocoder = new Geocoder(context, Locale.CHINA);
            java.util.List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses == null || addresses.isEmpty()) return administrativeLabel("", "", "");
            Address address = addresses.get(0);
            String province = address.getAdminArea();
            String city = address.getLocality();
            String district = address.getSubLocality();
            if (city == null || city.trim().isEmpty()) city = address.getSubAdminArea();
            if ((district == null || district.trim().isEmpty()) && address.getSubAdminArea() != null
                    && !address.getSubAdminArea().equals(city)) district = address.getSubAdminArea();
            return administrativeLabel(province == null ? "" : province.trim(), city == null ? "" : city.trim(), district == null ? "" : district.trim());
        } catch (Exception ignored) {
            return administrativeLabel("", "", "");
        }
    }

    @Override public void onResume() { super.onResume(); if (carousel != null) carousel.start(); refreshDashboardState(); }
    @Override public void onPause() { if (carousel != null) carousel.stop(); super.onPause(); }
    @Override public void onDestroyView() { if (carousel != null) carousel.release(); carousel = null; dashboard = null; cropSpinner = null; cropTemperatureRange = null; weatherValue = null; weatherStatus = null; weatherClimate = null; advice = null; super.onDestroyView(); }
    @Override public void onDestroy() { executor.shutdownNow(); mainHandler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
