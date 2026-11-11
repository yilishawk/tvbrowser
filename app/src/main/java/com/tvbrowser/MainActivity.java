package com.tvbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
    private GridView grid;
    private SiteAdapter adapter;
    private List<Sites.Site> sites;
    private LocalServer localServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sites = Sites.load(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0b0e14"));
        root.setPadding(dp(24), dp(16), dp(24), dp(16));

        TextView title = new TextView(this);
        title.setText("TV 影视浏览器");
        title.setTextSize(24);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        // 顶部按钮行：添加 + 扫码
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        Button add = new Button(this);
        add.setText("+ 添加站点");
        add.setTextSize(18);
        add.setPadding(dp(20), dp(10), dp(20), dp(10));
        add.setOnClickListener(v -> showAddDialog());
        Button scan = new Button(this);
        scan.setText("扫码添加 (手机填网址)");
        scan.setTextSize(18);
        scan.setPadding(dp(20), dp(10), dp(20), dp(10));
        scan.setOnClickListener(v -> showQrDialog());
        btnRow.addView(add, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnRow.addView(scan, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ViewGroup.MarginLayoutParams brlp = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brlp.bottomMargin = dp(12);
        root.addView(btnRow, brlp);

        grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(dp(14));
        grid.setHorizontalSpacing(dp(14));
        grid.setGravity(Gravity.CENTER);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        adapter = new SiteAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> openSite(sites.get(position)));
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmDelete(position);
            return true;
        });
        root.addView(grid, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);

        // 启动本地服务，收手机 H5 添加的站点
        try {
            localServer = new LocalServer(8080, new LocalServer.OnSiteAdded() {
                @Override
                public void onSiteAdded(Sites.Site s) {
                    runOnUiThread(() -> {
                        sites.add(s);
                        Sites.save(MainActivity.this, sites);
                        adapter.notifyDataSetChanged();
                        Toast.makeText(MainActivity.this, "已添加：" + s.name, Toast.LENGTH_SHORT).show();
                    });
                }
            });
            localServer.start();
        } catch (Exception e) {
            Toast.makeText(this, "本地服务启动失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openSite(Sites.Site s) {
        if (s == null || s.url == null || s.url.isEmpty()) return;
        Intent it = new Intent(this, BrowserActivity.class);
        it.putExtra("url", s.url);
        it.putExtra("name", s.name);
        it.putExtra("player", s.player);
        startActivity(it);
    }

    private void showAddDialog() {
        LinearLayout lay = new LinearLayout(this);
        lay.setOrientation(LinearLayout.VERTICAL);
        lay.setPadding(dp(24), dp(12), dp(24), dp(12));
        EditText nameEt = new EditText(this);
        nameEt.setHint("站点名称（如：华视界）");
        nameEt.setInputType(InputType.TYPE_CLASS_TEXT);
        nameEt.setTextSize(18);
        EditText urlEt = new EditText(this);
        urlEt.setHint("网址（如：huashijie.tv）");
        urlEt.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        urlEt.setTextSize(18);
        lay.addView(nameEt, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        lay.addView(urlEt, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView pLabel = new TextView(this);
        pLabel.setText("播放方式：");
        pLabel.setTextSize(18);
        pLabel.setTextColor(Color.WHITE);
        lay.addView(pLabel, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton exo = new RadioButton(this);
        exo.setText("ExoPlayer（m3u8/mp4 直链）");
        exo.setTextSize(16);
        RadioButton med = new RadioButton(this);
        med.setText("MediaPlayer（系统播放器）");
        med.setTextSize(16);
        RadioButton web = new RadioButton(this);
        web.setText("WebView（加密源/直播兜底）");
        web.setTextSize(16);
        web.setChecked(true);
        rg.addView(exo, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rg.addView(med, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rg.addView(web, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        lay.addView(rg, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("添加站点")
                .setView(lay)
                .setPositiveButton("保存", (d, w) -> {
                    String n = nameEt.getText().toString().trim();
                    String u = urlEt.getText().toString().trim();
                    if (u.isEmpty()) {
                        Toast.makeText(this, "网址不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!u.startsWith("http")) u = "https://" + u;
                    if (n.isEmpty()) n = u;
                    String p = exo.isChecked() ? "EXO" : (med.isChecked() ? "MED" : "WEB");
                    sites.add(new Sites.Site(n, u, p));
                    Sites.save(this, sites);
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showQrDialog() {
        new AlertDialog.Builder(this)
                .setTitle("扫码添加")
                .setMessage("用手机浏览器扫下方二维码，在手机上填网址/名称/播放方式，提交后自动同步到电视。电视和手机需同一 Wi-Fi。")
                .setPositiveButton("查看二维码", (d, w) -> {
                    Intent it = new Intent(this, BrowserActivity.class);
                    it.putExtra("url", "file:///android_asset/qr.html");
                    it.putExtra("name", "扫码添加");
                    it.putExtra("player", "WEB");
                    startActivity(it);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDelete(int pos) {
        Sites.Site s = sites.get(pos);
        new AlertDialog.Builder(this)
                .setTitle("删除站点")
                .setMessage("确定删除「" + s.name + "」？")
                .setPositiveButton("删除", (d, w) -> {
                    sites.remove(pos);
                    Sites.save(this, sites);
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (localServer != null) localServer.stop();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private ViewGroup.MarginLayoutParams lp(int w, int h) {
        return new ViewGroup.MarginLayoutParams(w, h);
    }

    class SiteAdapter extends android.widget.BaseAdapter {
        @Override
        public int getCount() {
            return sites.size();
        }

        @Override
        public Object getItem(int p) {
            return sites.get(p);
        }

        @Override
        public long getItemId(int p) {
            return p;
        }

        @Override
        public android.view.View getView(int p, android.view.View cv, ViewGroup parent) {
            FrameLayout f = (FrameLayout) cv;
            if (f == null) {
                f = new FrameLayout(MainActivity.this);
                f.setMinimumHeight(dp(100));
                f.setFocusable(true);
                f.setFocusableInTouchMode(false);
            }
            Sites.Site s = sites.get(p);
            final int TAG_NAME = 0x01;
            final int TAG_BADGE = 0x02;
            TextView tv = (TextView) f.getTag(TAG_NAME);
            if (tv == null) {
                tv = new TextView(MainActivity.this);
                tv.setTextSize(20);
                tv.setTextColor(Color.WHITE);
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(dp(10), dp(24), dp(10), dp(24));
                f.setTag(TAG_NAME, tv);
                f.addView(tv, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            }
            tv.setText(s.name);
            TextView badge = (TextView) f.getTag(TAG_BADGE);
            if (badge == null) {
                badge = new TextView(MainActivity.this);
                badge.setTextSize(14);
                badge.setTextColor(Color.WHITE);
                badge.setBackgroundColor(Color.parseColor("#1f6feb"));
                badge.setPadding(dp(8), dp(4), dp(8), dp(4));
                badge.setTypeface(Typeface.DEFAULT_BOLD);
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                bl.gravity = Gravity.TOP | Gravity.END;
                badge.setLayoutParams(bl);
                f.setTag(TAG_BADGE, badge);
                f.addView(badge);
            }
            badge.setText("EXO".equals(s.player) ? "EXO" : ("MED".equals(s.player) ? "MED" : "WEB"));
            badge.setBackgroundColor(Color.parseColor(
                    "EXO".equals(s.player) ? "#1f6feb" : ("MED".equals(s.player) ? "#2ea043" : "#3a3f47")));
            f.setBackground(createBg(false));
            f.setOnFocusChangeListener((v, focused) -> {
                FrameLayout fl = (FrameLayout) v;
                fl.setBackground(createBg(focused));
                TextView b = (TextView) fl.getTag(TAG_BADGE);
                if (b != null) {
                    int bg = Color.parseColor(s.isExo() ? "#1f6feb" : "#3a3f47");
                    int focusBg = Color.parseColor("#58a6ff");
                    b.setBackgroundColor(focused ? focusBg : bg);
                }
            });
            return f;
        }
    }

    private GradientDrawable createBg(boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(12));
        if (focused) {
            d.setColor(Color.parseColor("#1f6feb"));
            d.setStroke(dp(3), Color.parseColor("#58a6ff"));
        } else {
            d.setColor(Color.parseColor("#161b22"));
            d.setStroke(dp(2), Color.parseColor("#30363d"));
        }
        return d;
    }
}
