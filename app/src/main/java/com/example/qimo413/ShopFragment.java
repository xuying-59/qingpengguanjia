package com.example.qimo413;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/** Opens a user's pesticide search in an external marketplace app or browser. */
public class ShopFragment extends Fragment {
    private CropCarousel carousel;
    /** 页面创建：初始化农资搜索框，绑定淘宝/京东两个跳转按钮。 */
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        View root = inflater.inflate(R.layout.fragment_shop, parent, false);
        carousel = new CropCarousel(root);
        EditText query = root.findViewById(R.id.shop_search_query);
        root.findViewById(R.id.shop_taobao).setOnClickListener(v -> openMarketplace("taobao", query));
        root.findViewById(R.id.shop_jd).setOnClickListener(v -> openMarketplace("jd", query));
        return root;
    }

    @Override public void onResume() { super.onResume(); if (carousel != null) carousel.start(); }
    @Override public void onPause() { if (carousel != null) carousel.stop(); super.onPause(); }
    @Override public void onDestroyView() { if (carousel != null) carousel.release(); carousel = null; super.onDestroyView(); }

    static Uri searchUri(String platform, String query) {
        if ("taobao".equals(platform)) {
            return Uri.parse("https://s.taobao.com/search").buildUpon().appendQueryParameter("q", query).build();
        }
        if ("jd".equals(platform)) {
            return Uri.parse("https://search.jd.com/Search").buildUpon().appendQueryParameter("keyword", query).build();
        }
        throw new IllegalArgumentException("Unknown marketplace");
    }

    private void openMarketplace(String platform, EditText searchInput) {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            searchInput.setError("请输入农药或农资关键词");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, searchUri(platform, query));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(requireContext(), "未找到可打开购物网站的应用", Toast.LENGTH_LONG).show();
        }
    }
}
