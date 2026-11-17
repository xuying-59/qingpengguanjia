package com.example.qimo413;

import android.content.Context;
import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import android.app.Instrumentation;
import android.widget.EditText;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.checkbox.MaterialCheckBox;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class GreenhouseFlowTest {
    @Test public void loginDashboardAndDetails() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("session", Context.MODE_PRIVATE).edit().clear().commit();
        LoginActivity login = (LoginActivity) instrumentation.startActivitySync(new Intent(context, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(() -> {
            ((EditText) login.findViewById(R.id.username)).setText("wrong");
            ((EditText) login.findViewById(R.id.password)).setText("bad");
            login.findViewById(R.id.login_button).performClick();
        });
        instrumentation.waitForIdleSync();
        assertFalse(context.getSharedPreferences("session", Context.MODE_PRIVATE).getBoolean("logged_in", false));
        instrumentation.runOnMainSync(() -> {
            ((EditText) login.findViewById(R.id.username)).setText("farmer");
            ((EditText) login.findViewById(R.id.password)).setText("123456");
            login.findViewById(R.id.login_button).performClick();
        });
        instrumentation.waitForIdleSync();
        assertTrue(context.getSharedPreferences("session", Context.MODE_PRIVATE).getBoolean("logged_in", false));
        MainActivity main = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .putExtra(MainActivity.EXTRA_AUTHENTICATED, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        assertNotNull(main.findViewById(R.id.bottom_navigation));
        assertNotNull(main.findViewById(R.id.crop_spinner));
        assertEquals(6, ((android.widget.Spinner) main.findViewById(R.id.crop_spinner)).getAdapter().getCount());
        assertNotNull(main.findViewById(R.id.crop_temperature_range));
        assertNotNull(main.findViewById(R.id.recognize_crop_button));
        assertNotNull(main.findViewById(R.id.weather_location_button));
        assertNotNull(main.findViewById(R.id.weather_region_button));
        assertNotNull(main.findViewById(R.id.threshold_comparison));
        assertEquals(3, ((ViewPager2) main.findViewById(R.id.crop_carousel)).getAdapter().getItemCount());
        instrumentation.runOnMainSync(() -> ((BottomNavigationView) main.findViewById(R.id.bottom_navigation)).setSelectedItemId(R.id.nav_devices));
        instrumentation.waitForIdleSync();
        assertNotNull(main.findViewById(R.id.switch_pump));
        assertNotNull(main.findViewById(R.id.mode_auto_pump));
        assertNotNull(main.findViewById(R.id.mode_manual_pump));
        new EventDbHelper(context).addDeviceTransition("西红柿 · 通风机自动开启", "测试值：空气温度 30.0°C\n设备开关：通风机开启");
        instrumentation.runOnMainSync(() -> ((BottomNavigationView) main.findViewById(R.id.bottom_navigation)).setSelectedItemId(R.id.nav_records));
        instrumentation.waitForIdleSync();
        RecyclerView records = main.findViewById(R.id.records_list);
        assertNotNull(records);
        assertTrue(records.getAdapter().getItemCount() > 0);
        AdviceActivity advice = (AdviceActivity) instrumentation.startActivitySync(new Intent(context, AdviceActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        assertNotNull(advice.findViewById(R.id.advice_result));
        assertNotNull(advice.findViewById(R.id.chat_send_button));
        assertNotNull(advice.findViewById(R.id.photo_button));
        instrumentation.runOnMainSync(() -> { advice.finish(); main.finish(); login.finish(); });
    }

    @Test public void historyContainsOnlyAutomaticThresholdTransitions() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("greenhouse_demo", Context.MODE_PRIVATE).edit().clear().commit();
        context.deleteDatabase("greenhouse_events.db");
        GreenhouseStore.setCrop(context, "西红柿");
        GreenhouseStore.setSensor(context, "temperature", 24f);
        GreenhouseStore.setSensor(context, "humidity", 60f);
        GreenhouseStore.setSensor(context, "soil", 50f);
        GreenhouseStore.setSensor(context, "light", 18f);
        GreenhouseStore.applyAutomation(context);
        EventDbHelper events = new EventDbHelper(context);
        assertEquals(0, events.recentEntries().size());
        GreenhouseStore.setThreshold(context, "西红柿", "temperature", 23f);
        GreenhouseStore.applyAutomation(context);
        assertEquals(0, events.recentEntries().size());
        GreenhouseStore.setThreshold(context, "西红柿", "temperature", 28f);
        GreenhouseStore.applyAutomation(context);
        GreenhouseStore.setSensor(context, "temperature", 30f);
        GreenhouseStore.applyAutomation(context, true);
        assertEquals(1, events.recentEntries().size());
        assertTrue(events.recentEntries().get(0).title.contains("通风机自动开启"));
        assertTrue(events.recentEntries().get(0).detail.contains("测试值"));
        GreenhouseStore.setSensor(context, "temperature", 31f);
        GreenhouseStore.applyAutomation(context, true);
        assertEquals(1, events.recentEntries().size());
        GreenhouseStore.setSensor(context, "temperature", 26f);
        GreenhouseStore.applyAutomation(context, true);
        assertEquals(2, events.recentEntries().size());
        assertTrue(events.recentEntries().get(0).title.contains("通风机自动关闭"));
        events.close();
    }

    @Test public void manualDeviceChoiceIsNotOverriddenByAutomaticThresholds() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("greenhouse_demo", Context.MODE_PRIVATE).edit().clear().commit();
        context.deleteDatabase("greenhouse_events.db");
        GreenhouseStore.setCrop(context, "西红柿");
        GreenhouseStore.setSensor(context, "temperature", 24f);
        GreenhouseStore.setSensor(context, "humidity", 60f);
        GreenhouseStore.setSensor(context, "soil", 50f);
        GreenhouseStore.setSensor(context, "light", 18f);
        GreenhouseStore.applyAutomation(context);

        GreenhouseStore.setAutomaticMode(context, "pump", false);
        GreenhouseStore.setDevice(context, "pump", true);
        GreenhouseStore.setSensor(context, "soil", 20f);
        GreenhouseStore.applyAutomation(context, true);
        assertTrue(GreenhouseStore.deviceOn(context, "pump"));
        assertFalse(GreenhouseStore.automaticMode(context, "pump"));
        EventDbHelper events = new EventDbHelper(context);
        assertEquals(0, events.recentEntries().size());

        GreenhouseStore.setDevice(context, "pump", false);
        GreenhouseStore.applyAutomation(context);
        assertFalse(GreenhouseStore.deviceOn(context, "pump"));
        GreenhouseStore.setAutomaticMode(context, "pump", true);
        GreenhouseStore.applyAutomation(context);
        assertTrue(GreenhouseStore.deviceOn(context, "pump"));

        GreenhouseStore.setSensor(context, "soil", 50f);
        GreenhouseStore.applyAutomation(context, true);
        assertFalse(GreenhouseStore.deviceOn(context, "pump"));
        assertEquals(1, events.recentEntries().size());
        events.close();
    }

    @Test public void shopNavigationOpensMarketplaceSearch() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        MainActivity main = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .putExtra(MainActivity.EXTRA_AUTHENTICATED, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(() -> ((BottomNavigationView) main.findViewById(R.id.bottom_navigation)).setSelectedItemId(R.id.nav_shop));
        instrumentation.waitForIdleSync();
        assertNotNull(main.findViewById(R.id.shop_taobao));
        assertNotNull(main.findViewById(R.id.shop_jd));
        assertEquals(3, ((ViewPager2) main.findViewById(R.id.crop_carousel)).getAdapter().getItemCount());
        assertEquals("农药", ((EditText) main.findViewById(R.id.shop_search_query)).getText().toString());
        assertEquals("农药", ShopFragment.searchUri("taobao", "农药").getQueryParameter("q"));
        assertEquals("农药", ShopFragment.searchUri("jd", "农药").getQueryParameter("keyword"));
        instrumentation.runOnMainSync(main::finish);
    }

    @Test public void rememberedCredentialsRemainAfterLogout() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        context.getSharedPreferences("session", Context.MODE_PRIVATE).edit().clear().commit();
        LoginActivity login = (LoginActivity) instrumentation.startActivitySync(new Intent(context, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(() -> {
            ((EditText) login.findViewById(R.id.username)).setText("farmer");
            ((EditText) login.findViewById(R.id.password)).setText("123456");
            ((MaterialCheckBox) login.findViewById(R.id.remember_password)).setChecked(true);
            login.findViewById(R.id.login_button).performClick();
        });
        instrumentation.waitForIdleSync();
        assertEquals("farmer", context.getSharedPreferences("session", Context.MODE_PRIVATE).getString("saved_username", ""));
        assertEquals("123456", context.getSharedPreferences("session", Context.MODE_PRIVATE).getString("saved_password", ""));
        context.getSharedPreferences("session", Context.MODE_PRIVATE).edit().putBoolean("logged_in", false).commit();
        LoginActivity again = (LoginActivity) instrumentation.startActivitySync(new Intent(context, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        assertEquals("farmer", ((EditText) again.findViewById(R.id.username)).getText().toString());
        assertEquals("123456", ((EditText) again.findViewById(R.id.password)).getText().toString());
        instrumentation.runOnMainSync(() -> { again.finish(); login.finish(); });
    }
}
