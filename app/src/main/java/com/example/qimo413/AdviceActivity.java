package com.example.qimo413;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** AI 植物问诊页：联网调用智谱 GLM-4V-Flash 视觉模型，根据症状文字、照片和棚内环境识别多种病症并给用药建议。 */
public class AdviceActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<String> photoPicker;
    private LinearLayout messages;
    private ScrollView chatScroll;
    private EditText input;
    private MaterialButton sendButton, photoButton;
    private TextView status;
    private View attachment;
    private ImageView preview;
    private Bitmap attachedPhoto;
    private final List<String> chatHistory = new ArrayList<>();
    private boolean requestInFlight;

    /** 页面创建：绑定拍照/发送按钮，显示当前作物和棚内环境，发欢迎语泡。 */
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_advice);
        photoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), this::loadPhoto);
        findViewById(R.id.back_button).setOnClickListener(v -> finish());
        findViewById(R.id.photo_button).setOnClickListener(v -> photoPicker.launch("image/*"));
        findViewById(R.id.remove_photo_button).setOnClickListener(v -> clearPhoto());
        findViewById(R.id.chat_send_button).setOnClickListener(v -> sendQuestion());
        messages = findViewById(R.id.chat_messages);
        chatScroll = findViewById(R.id.chat_scroll);
        input = findViewById(R.id.chat_input);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
        sendButton = findViewById(R.id.chat_send_button);
        photoButton = findViewById(R.id.photo_button);
        status = findViewById(R.id.chat_status);
        attachment = findViewById(R.id.photo_attachment);
        preview = findViewById(R.id.photo_preview);

        CropProfile profile = CropProfile.forName(GreenhouseStore.crop(this));
        ((TextView)findViewById(R.id.crop_detail)).setText(String.format(java.util.Locale.CHINA,
                "当前作物：%s · 适宜温度 %.0f–%.0f°C", profile.name, profile.temperatureMin, profile.temperatureMax));
        ((TextView)findViewById(R.id.advice_result)).setText(GreenhouseStore.advice(this));
        appendBubble("描述叶片、茎或果实的异常症状即可，AI 会联网分析可能病症、处理方案和用药建议。", false, null);
    }

    /** 从相册选图后：后台线程解码，主线程显示缩略图预览。 */
    private void loadPhoto(Uri uri) {
        if (uri == null) return;
        status.setText("正在读取照片…");
        setInputsEnabled(false);
        executor.execute(() -> {
            Bitmap bitmap = null;
            Exception failure = null;
            try { bitmap = PhotoDecoder.read(getContentResolver(), uri); }
            catch (Exception exception) { failure = exception; }
            Bitmap selected = bitmap;
            Exception error = failure;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    if (selected != null) selected.recycle();
                    return;
                }
                setInputsEnabled(true);
                if (selected == null) {
                    status.setText(error == null ? "照片读取失败，请重试。" : error.getMessage());
                    return;
                }
                clearPhoto();
                attachedPhoto = selected;
                preview.setImageBitmap(selected);
                attachment.setVisibility(View.VISIBLE);
                status.setText("照片已添加，AI 会直接看图诊断。");
            });
        });
    }

    /** 发送提问：用户气泡先上屏，后台调智谱 API，成功后把回复追加到对话并记录历史。 */
    private void sendQuestion() {
        String question = input.getText() == null ? "" : input.getText().toString().trim();
        if (question.isEmpty() && attachedPhoto == null) return;
        if (question.isEmpty()) question = "请根据植物照片判断最可能的病症，并给出处理和药剂方向。";
        String message = question;
        Bitmap photoForAnalysis = attachedPhoto;
        View userBubble = appendBubble(message, true, photoForAnalysis);
        setInputsEnabled(false);
        status.setText("正在联网分析症状和照片…");
        requestInFlight = true;
        executor.execute(() -> {
            String cloudReply = null;
            Exception cloudError = null;
            try {
                android.content.Context context = getApplicationContext();
                cloudReply = CloudAdvisor.ask(
                        GreenhouseStore.crop(context), message, photoForAnalysis,
                        GreenhouseStore.sensor(context, "temperature"),
                        GreenhouseStore.sensor(context, "humidity"),
                        GreenhouseStore.sensor(context, "soil"),
                        chatHistory);
            }
            catch (Exception exception) { cloudError = exception; }

            String finalReply = cloudReply;
            Exception finalError = cloudError;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                requestInFlight = false;
                setInputsEnabled(true);
                if (finalReply == null) {
                    messages.removeView(userBubble);
                    status.setText(errorMessage(finalError));
                    return;
                }
                chatHistory.add("user: " + message);
                chatHistory.add("assistant: " + finalReply);
                appendBubble(finalReply, false, null);
                input.setText("");
                clearPhoto();
                status.setText("分析完成；用药前请核对当地登记和产品标签。");
            });
        });
    }

    /** 动态生成一条聊天气泡：用户右侧绿色、AI 左侧白色，可选附带缩略图。 */
    private View appendBubble(String text, boolean fromUser, Bitmap photo) {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setGravity(fromUser ? Gravity.END : Gravity.START);
        MaterialCardView bubble = new MaterialCardView(this);
        bubble.setRadius(dp(14));
        bubble.setCardElevation(dp(1));
        bubble.setCardBackgroundColor(ContextCompat.getColor(this, fromUser ? R.color.forest : R.color.card));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(10), dp(14), dp(10));
        if (photo != null) {
            int size = Math.min(120, Math.max(1, Math.max(photo.getWidth(), photo.getHeight())));
            float scale = size / (float) Math.max(photo.getWidth(), photo.getHeight());
            Bitmap thumbnail = Bitmap.createScaledBitmap(photo,
                    Math.max(1, (int) (photo.getWidth() * scale)), Math.max(1, (int) (photo.getHeight() * scale)), true);
            ImageView image = new ImageView(this);
            image.setImageBitmap(thumbnail);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            content.addView(image, new LinearLayout.LayoutParams(dp(120), dp(120)));
        }
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(ContextCompat.getColor(this, fromUser ? R.color.white : R.color.black));
        label.setTextSize(15);
        label.setLineSpacing(dp(3), 1f);
        label.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.78f));
        content.addView(label);
        bubble.addView(content);
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bubbleParams.bottomMargin = dp(10);
        row.addView(bubble, bubbleParams);
        messages.addView(row);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        return row;
    }

    private String errorMessage(Exception error) {
        if (error == null || error.getMessage() == null) return "联网问诊失败，请检查网络和 API Key 后重试。";
        return "联网问诊失败：" + error.getMessage();
    }

    private void setInputsEnabled(boolean enabled) {
        if (input == null) return;
        input.setEnabled(enabled);
        sendButton.setEnabled(enabled);
        photoButton.setEnabled(enabled);
        findViewById(R.id.remove_photo_button).setEnabled(enabled);
    }

    private void clearPhoto() {
        if (attachedPhoto != null && !attachedPhoto.isRecycled()) attachedPhoto.recycle();
        attachedPhoto = null;
        if (preview != null) preview.setImageDrawable(null);
        if (attachment != null) attachment.setVisibility(View.GONE);
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() {
        if (!requestInFlight) clearPhoto();
        else {
            attachedPhoto = null;
            if (preview != null) preview.setImageDrawable(null);
        }
        executor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
