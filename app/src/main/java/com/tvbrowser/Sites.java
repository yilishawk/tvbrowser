package com.tvbrowser;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Sites {
    private static final String PREFS = "tvbrowser_sites";
    private static final String KEY = "list";

    public static class Site {
        public String name;
        public String url;
        /** "EXO" = 用 ExoPlayer 接管, "WEB" = 用 WebView 系统全屏（默认/兜底） */
        public String player;

        public Site(String n, String u) {
            this(n, u, "WEB");
        }

        public Site(String n, String u, String p) {
            name = n;
            url = u;
            player = (p == null || p.isEmpty()) ? "WEB" : p.toUpperCase();
        }

        public boolean isExo() {
            return "EXO".equals(player);
        }
    }

    public static List<Site> load(Context c) {
        List<Site> out = new ArrayList<>();
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY, null);
        if (raw == null) {
            // 首次打开给两个示例，方便直接测试（可删除）
            out.add(new Site("华视界", "https://huashijie.tv"));
            out.add(new Site("百度", "https://www.baidu.com"));
            save(c, out);
            return out;
        }
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Site(o.optString("name"), o.optString("url"), o.optString("player", "WEB")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void save(Context c, List<Site> list) {
        JSONArray a = new JSONArray();
        for (Site s : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("name", s.name);
                o.put("url", s.url);
                o.put("player", s.player == null ? "WEB" : s.player);
            } catch (Exception ignored) {
            }
            a.put(o);
        }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply();
    }
}
