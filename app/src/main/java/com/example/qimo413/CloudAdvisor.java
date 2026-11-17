package com.example.qimo413;

import android.graphics.Bitmap;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 联网问诊：调用智谱 GLM-4V-Flash 视觉模型（免费），根据作物、症状文字、
 * 棚内环境和照片直接"看图"诊断，给出多种病症、处理步骤、常用药剂和参考稀释倍数。
 */
final class CloudAdvisor {
    private CloudAdvisor() {}

    private static final String API_KEY = "205d487ae6934ce394e6219c8e61f77a.rZ5gy8bAsWiW8AzM";
    private static final String API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    private static final String MODEL = "glm-4v-flash";
    private static final int CONNECT_TIMEOUT = 8000;
    private static final int READ_TIMEOUT = 15000;
    private static final int MAX_PHOTO_DIMENSION = 1024;

    /**
     * 向智谱 GLM-4V-Flash 提问。
     * @param crop       当前作物名
     * @param message    用户本轮输入
     * @param photo      患者上传的照片，可为 null
     * @param temperature 棚内温度 °C
     * @param humidity   棚内空气湿度 %
     * @param soil       棚内土壤湿度 %
     * @param history    已发生的多轮对话，元素形如 "user: ..." 或 "assistant: ..."
     * @return 模型回复文本
     */
    static String ask(String crop, String message, Bitmap photo,
                      float temperature, float humidity, float soil,
                      java.util.List<String> history) throws IOException {
        String systemPrompt = "你是面向中国小规模大棚种植户的植物植保助手。"
                + "根据用户描述的作物、症状文字、上传的照片和棚内温度/湿度/土壤湿度，"
                + "先描述照片中观察到的叶片、果实或茎部异常，再给出1至3种可能的病症，"
                + "每种给出常见表现、处理步骤、常用药剂（有效成分或常见登记商品名方向）和参考稀释倍数；"
                + "必须提醒用户以产品标签为准，核对当地登记的作物和防治对象、个人防护和采收安全间隔期；"
                + "不确定时明确说需要补拍叶片正反面、茎基部或根区照片；不要编造农药登记证号或精确剂量。"
                + "用简体中文回答，结构清晰。";

        JSONArray messages = new JSONArray();
        JSONObject payload;
        try {
            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
            messages.put(new JSONObject().put("role", "system")
                    .put("content", String.format(Locale.CHINA,
                            "当前作物：%s；棚内温度 %.1f°C、空气湿度 %.0f%%、土壤湿度 %.0f%%。",
                            crop, temperature, humidity, soil)));
            if (history != null) {
                for (String turn : history) {
                    if (turn == null || turn.isEmpty()) continue;
                    if (turn.startsWith("user: ")) {
                        messages.put(new JSONObject().put("role", "user").put("content", turn.substring(6)));
                    } else if (turn.startsWith("assistant: ")) {
                        messages.put(new JSONObject().put("role", "assistant").put("content", turn.substring(11)));
                    }
                }
            }

            // 本轮用户消息：有照片用多模态数组（文字+图片），没照片用纯字符串
            String userMessage = message == null ? "" : message.trim();
            if (photo != null) {
                JSONArray userContent = new JSONArray();
                userContent.put(new JSONObject().put("type", "text").put("text", userMessage));
                String base64 = bitmapToBase64Jpeg(photo);
                userContent.put(new JSONObject().put("type", "image_url")
                        .put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + base64)));
                messages.put(new JSONObject().put("role", "user").put("content", userContent));
            } else {
                messages.put(new JSONObject().put("role", "user").put("content", userMessage));
            }

            payload = new JSONObject();
            payload.put("model", MODEL);
            payload.put("messages", messages);
            payload.put("temperature", 0.4);
            payload.put("max_tokens", 1000);
        } catch (JSONException jsonError) {
            throw new IOException("请求构造失败: " + jsonError.getMessage());
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("Authorization", "Bearer " + API_KEY);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            int code = connection.getResponseCode();
            String raw = NetUtil.readUtf8(code >= 400
                    ? connection.getErrorStream() : connection.getInputStream());
            if (code != 200) {
                throw new IOException("GLM-4V HTTP " + code + ": " + raw);
            }
            JSONObject data = new JSONObject(raw);
            JSONArray choices = data.optJSONArray("choices");
            if (choices == null || choices.length() == 0) throw new IOException("GLM-4V empty response");
            JSONObject messageObj = choices.getJSONObject(0).optJSONObject("message");
            String content = messageObj == null ? null : messageObj.optString("content", "");
            if (content == null || content.trim().isEmpty()) throw new IOException("GLM-4V blank content");
            return content.trim();
        } catch (JSONException parseError) {
            throw new IOException("响应解析失败: " + parseError.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** 把 Bitmap 压缩成 JPEG 并 base64 编码，限制最长边避免请求过大。 */
    private static String bitmapToBase64Jpeg(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        float scale = Math.min(1f, MAX_PHOTO_DIMENSION / (float) Math.max(width, height));
        Bitmap scaled = scale < 1f
                ? Bitmap.createScaledBitmap(source, (int) (width * scale), (int) (height * scale), true)
                : source;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    /** AI 识别植物结果。 */
    static final class CropResult {
        final String name;
        final float tempMin;
        final float tempMax;
        final String description;
        CropResult(String name, float tempMin, float tempMax, String description) {
            this.name = name; this.tempMin = tempMin; this.tempMax = tempMax; this.description = description;
        }
    }

    /**
     * 用智谱 GLM-4V-Flash 识别照片里的植物，返回中文名、适宜温度范围和一句描述。
     * 模型返回 JSON：{"name":"...","tempMin":..,"tempMax":..,"desc":"..."}
     */
    static CropResult recognizeCrop(Bitmap photo) throws IOException {
        String systemPrompt = "你是植物识别专家。根据用户上传的植物照片，判断这是什么植物，"
                + "返回 JSON：{\"name\":\"中文名\",\"tempMin\":15,\"tempMax\":28,\"desc\":\"一句简短的植物习性描述\"}。"
                + "温度单位 °C，只返回 JSON，不要其他文字。";
        JSONArray messages = new JSONArray();
        JSONObject payload;
        try {
            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
            JSONArray userContent = new JSONArray();
            userContent.put(new JSONObject().put("type", "text").put("text", "识别这是什么植物，给出适宜温度范围。"));
            if (photo != null) {
                userContent.put(new JSONObject().put("type", "image_url")
                        .put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + bitmapToBase64Jpeg(photo))));
            }
            messages.put(new JSONObject().put("role", "user").put("content", userContent));
            payload = new JSONObject();
            payload.put("model", MODEL);
            payload.put("messages", messages);
            payload.put("temperature", 0.2);
            payload.put("max_tokens", 300);
        } catch (JSONException e) { throw new IOException("请求构造失败: " + e.getMessage()); }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("Authorization", "Bearer " + API_KEY);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) { out.write(body); }
            int code = connection.getResponseCode();
            String raw = NetUtil.readUtf8(code >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (code != 200) throw new IOException("识别失败 HTTP " + code);
            JSONObject data = new JSONObject(raw);
            JSONArray choices = data.optJSONArray("choices");
            if (choices == null || choices.length() == 0) throw new IOException("识别返回为空");
            String content = choices.getJSONObject(0).optJSONObject("message").optString("content", "");
            String json = content.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) json = json.substring(start, end + 1);
            JSONObject obj = new JSONObject(json);
            String name = obj.optString("name", "未知植物");
            float tempMin = (float) obj.optDouble("tempMin", 15);
            float tempMax = (float) obj.optDouble("tempMax", 30);
            String desc = obj.optString("desc", "");
            return new CropResult(name, tempMin, tempMax, desc);
        } catch (JSONException e) {
            throw new IOException("识别结果解析失败: " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
