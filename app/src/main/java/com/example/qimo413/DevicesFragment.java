package com.example.qimo413;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.Locale;

/** 设备控制页：通风机/灌溉水泵/补光灯的自动-手动模式切换与手动开关。 */
public class DevicesFragment extends Fragment {
    /** 页面创建：先按当前传感器重算一次自动化，再绑定三台设备的开关和模式。 */
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        View view = inflater.inflate(R.layout.fragment_devices, parent, false);
        GreenhouseStore.applyAutomation(requireContext());
        bind(view, R.id.switch_vent, R.id.mode_vent, R.id.mode_auto_vent, R.id.mode_manual_vent,
                R.id.device_rule_vent, "vent");
        bind(view, R.id.switch_pump, R.id.mode_pump, R.id.mode_auto_pump, R.id.mode_manual_pump,
                R.id.device_rule_pump, "pump");
        bind(view, R.id.switch_light, R.id.mode_light, R.id.mode_auto_light, R.id.mode_manual_light,
                R.id.device_rule_light, "lamp");
        return view;
    }

    /** 绑定一台设备：开关状态、自动/手动模式切换、自动规则文案。 */
    private void bind(View root, int switchId, int groupId, int autoId, int manualId,
                      int ruleId, String key) {
        SwitchMaterial control = root.findViewById(switchId);
        MaterialButtonToggleGroup mode = root.findViewById(groupId);
        TextView rule = root.findViewById(ruleId);
        boolean automatic = GreenhouseStore.automaticMode(requireContext(), key);
        control.setChecked(GreenhouseStore.deviceOn(requireContext(), key));
        control.setEnabled(!automatic); // 自动模式下开关禁用，防止手动覆盖
        mode.check(automatic ? autoId : manualId);
        rule.setText(ruleFor(key));

        // 切换自动/手动模式
        mode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            boolean useAutomatic = checkedId == autoId;
            GreenhouseStore.setAutomaticMode(requireContext(), key, useAutomatic);
            control.setEnabled(!useAutomatic);
            if (useAutomatic) {
                GreenhouseStore.applyAutomation(requireContext()); // 切回自动时立刻按阈值重算
                control.setChecked(GreenhouseStore.deviceOn(requireContext(), key));
            }
        });
        // 手动模式下用户拨动开关，直接写状态
        control.setOnCheckedChangeListener((button, isChecked) -> {
            if (!GreenhouseStore.automaticMode(requireContext(), key)) {
                GreenhouseStore.setDevice(requireContext(), key, isChecked);
            }
        });
    }

    /** 根据当前作物和阈值，生成这台设备的自动控制规则说明文字。 */
    private String ruleFor(String key) {
        String crop = GreenhouseStore.crop(requireContext());
        if ("vent".equals(key)) {
            return String.format(Locale.CHINA,
                    "温度高于 %.1f°C 或空气湿度高于 %.0f%%，且温度不低于适宜下限时开启；异常解除后关闭。",
                    GreenhouseStore.threshold(requireContext(), crop, "temperature"),
                    GreenhouseStore.threshold(requireContext(), crop, "humidity"));
        }
        if ("pump".equals(key)) {
            return String.format(Locale.CHINA, "土壤湿度低于 %.0f%% 时开始灌溉，达到阈值后关闭。",
                    GreenhouseStore.threshold(requireContext(), crop, "soil"));
        }
        return String.format(Locale.CHINA, "光照低于 %.1f klx 时开启补光，达到阈值后关闭。",
                GreenhouseStore.threshold(requireContext(), crop, "light"));
    }
}
