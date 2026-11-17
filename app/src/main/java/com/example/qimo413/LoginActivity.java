package com.example.qimo413;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** 登录页：课设硬编码账号 farmer/123456，支持"记住密码"自动回填。 */
public class LoginActivity extends AppCompatActivity {
    /** 启动：读取"记住密码"状态，自动回填账号密码。 */
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        android.content.SharedPreferences session = getSharedPreferences("session", MODE_PRIVATE);
        setContentView(R.layout.activity_login);
        EditText user = findViewById(R.id.username);
        EditText pass = findViewById(R.id.password);
        MaterialCheckBox remember = findViewById(R.id.remember_password);
        boolean remembered = session.getBoolean("remember_password", false);
        remember.setChecked(remembered);
        if (remembered) {
            user.setText(session.getString("saved_username", ""));
            pass.setText(session.getString("saved_password", ""));
        }
        // 登录按钮：校验账号 farmer/123456，成功跳主页，失败弹窗提示
        findViewById(R.id.login_button).setOnClickListener(v -> {
            if ("farmer".equals(user.getText().toString().trim()) && "123456".equals(pass.getText().toString())) {
                android.content.SharedPreferences.Editor editor = session.edit().putBoolean("logged_in", true).putBoolean("remember_password", remember.isChecked());
                if (remember.isChecked()) editor.putString("saved_username", user.getText().toString().trim()).putString("saved_password", pass.getText().toString());
                else editor.remove("saved_username").remove("saved_password");
                editor.apply();
                openHome();
            } else {
                pass.setText("");
                new MaterialAlertDialogBuilder(this).setTitle(R.string.login_failed)
                    .setMessage(R.string.login_failed_hint).setPositiveButton(R.string.ok, null).show();
            }
        });
    }
    private void openHome() {
        startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_AUTHENTICATED, true));
        finish();
    }
}
