package com.example.qimo413;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.widget.PopupMenu;

/** 主框架：校验登录态、底部导航切换四个 Fragment、顶部菜单（关于/退出），并定时重算自动化。 */
public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_AUTHENTICATED = "authenticated_this_launch";
    private final Handler historyHandler = new Handler(Looper.getMainLooper());
    private final Runnable hourlyHistory = new Runnable() {
        @Override public void run() {
            if (isFinishing() || isDestroyed()) return;
            GreenhouseStore.applyAutomation(MainActivity.this);
            historyHandler.postDelayed(this, 60 * 60 * 1000L);
        }
    };

    /** 启动时：未登录跳登录页；已登录则清理一周前记录、启动定时清理、重算自动化、设置底部导航。 */
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean justAuthenticated = getIntent().getBooleanExtra(EXTRA_AUTHENTICATED, false);
        boolean restoringSession = savedInstanceState != null
                && getSharedPreferences("session", MODE_PRIVATE).getBoolean("logged_in", false);
        if (!justAuthenticated && !restoringSession) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        new EventDbHelper(this).deleteOlderThanWeek(); // 启动时清理过期记录
        HistoryCleanupReceiver.schedule(this);          // 每周定时清理
        GreenhouseStore.applyAutomation(this);         // 按当前传感器重算设备
        historyHandler.postDelayed(hourlyHistory, 60 * 60 * 1000L); // 每小时重算一次
        setupNavigation(savedInstanceState == null);
        setupToolbarMenu();
    }

    /** 底部导航切换四个 Fragment：首页/设备/历史/购物。 */
    private void setupNavigation(boolean selectDashboard) {
        BottomNavigationView navigation = findViewById(R.id.bottom_navigation);
        navigation.setOnItemSelectedListener(item -> {
            Fragment fragment;
            int itemId = item.getItemId();
            if (itemId == R.id.nav_devices) fragment = new DevicesFragment();
            else if (itemId == R.id.nav_records) fragment = new RecordsFragment();
            else if (itemId == R.id.nav_shop) fragment = new ShopFragment();
            else fragment = new DashboardFragment();

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            ((TextView) findViewById(R.id.toolbar_title)).setText(item.getTitle());
            return true;
        });
        if (selectDashboard) navigation.setSelectedItemId(R.id.nav_dashboard);
    }

    private void setupToolbarMenu() {
        findViewById(R.id.toolbar_menu).setOnClickListener(anchor -> {
            PopupMenu menu = new PopupMenu(this, anchor);
            menu.getMenu().add(0, 1, 0, R.string.menu_about);
            menu.getMenu().add(0, 2, 1, R.string.menu_logout);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) showAboutDialog();
                else logOut();
                return true;
            });
            menu.show();
        });
    }

    private void showAboutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.about_title)
                .setMessage(R.string.about_message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void logOut() {
        getSharedPreferences("session", MODE_PRIVATE).edit()
                .putBoolean("logged_in", false)
                .apply();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override protected void onDestroy() {
        historyHandler.removeCallbacks(hourlyHistory);
        super.onDestroy();
    }
}
