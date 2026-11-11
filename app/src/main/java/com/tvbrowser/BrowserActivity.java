package com.tvbrowser;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class BrowserActivity extends Activity {
    private WebView wv;
    private MediaView mediaView;
    private ExoPlayerView exoView;
    private View customView;
    private WebChromeClient.CustomViewCallback cb;
    private String playerMode;
    private String siteName;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String url = getIntent().getStringExtra("url");
        siteName = getIntent().getStringExtra("name");
        playerMode = getIntent().getStringExtra("player");
        if (playerMode == null) playerMode = "WEB";
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        if (Build.VERSION.SDK_INT >= 17) {
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setPluginState(WebSettings.PluginState.ON);
        }
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String u) {
                view.loadUrl(u);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String u) {
                injectPurify();
                maybeHandoffToExo();
            }
        });
        wv.setWebChromeClient(new Chrome());

        ViewGroup root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(wv, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        if (url != null && !url.isEmpty()) wv.loadUrl(url);
    }

    /** 加载完网页后，EXO/MED 站点尝试抓视频 URL，分别切 ExoPlayer / MediaPlayer */
    private void maybeHandoffToExo() {
        if (!"EXO".equals(playerMode) && !"MED".equals(playerMode)) return;
        final boolean useExo = "EXO".equals(playerMode);
        // 给 JS 一点时间把 <video> 加载出来
        wv.postDelayed(() -> {
            String js = "(function(){var v=document.querySelector('video');if(!v)return null;"
                    + "var s=v.currentSrc||v.src;if(s&&s.length>4)return s;return null;})()";
            wv.evaluateJavascript(js, value -> {
                if (value == null || "null".equals(value) || value.isEmpty()) return;
                String src;
                try {
                    // evaluateJavascript 回传的是 JSON 字面量，包成数组再取第 0 项最稳
                    src = new org.json.JSONArray("[" + value + "]").getString(0);
                } catch (Exception e) {
                    src = value;
                }
                if (src.isEmpty() || src.startsWith("blob:")) {
                    toast("未拿到可用视频源，回退 WebView");
                    playerMode = "WEB";
                    return;
                }
                toast((useExo ? "切 ExoPlayer" : "切 MediaPlayer") + "：... "
                        + (src.length() > 40 ? src.substring(src.length() - 40) : src));
                if (useExo) handoffToExo(src);
                else handoffToMedia(src);
            });
        }, 3000);
    }

    private void handoffToExo(String videoUrl) {
        if (exoView == null) {
            exoView = new ExoPlayerView(this);
            exoView.setListener(new ExoPlayerView.OnExoListener() {
                @Override
                public void onExoPlaybackFailed(String reason) {
                    // 加密源 / 拿不到流：回退 WebView
                    toast("ExoPlayer 失败（" + reason + "），回退 WebView");
                    wv.setVisibility(View.VISIBLE);
                    if (exoView != null) exoView.release();
                    exoView = null;
                    playerMode = "WEB";
                }
            });
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            ((ViewGroup) wv.getParent()).addView(exoView, lp);
            exoView.setVisibility(View.VISIBLE);
        }
        wv.setVisibility(View.GONE);
        exoView.play(videoUrl, siteName);
    }

    private void handoffToMedia(String videoUrl) {
        if (mediaView == null) {
            mediaView = new MediaView(this);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            ((ViewGroup) wv.getParent()).addView(mediaView, lp);
            mediaView.setVisibility(View.VISIBLE);
        }
        wv.setVisibility(View.GONE);
        mediaView.play(videoUrl, siteName);
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    private void injectPurify() {
        try {
            InputStream is = getAssets().open("purify.js");
            BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            is.close();
            final String js = sb.toString();
            wv.post(() -> wv.evaluateJavascript(js, null));
        } catch (Exception ignored) {
            // 注入失败不致命
        }
    }

    private class Chrome extends WebChromeClient {
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            cb = callback;
            ViewGroup decor = (ViewGroup) getWindow().getDecorView();
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.gravity = Gravity.CENTER;
            decor.addView(view, lp);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            wv.setVisibility(View.GONE);
        }

        @Override
        public void onHideCustomView() {
            BrowserActivity.this.onHideCustomView();
        }
    }

    private void onHideCustomView() {
        if (customView == null) return;
        ViewGroup decor = (ViewGroup) getWindow().getDecorView();
        decor.removeView(customView);
        customView = null;
        if (cb != null) cb.onCustomViewHidden();
        cb = null;
        wv.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customView != null) {
                onHideCustomView();
                return true;
            }
            if (wv != null && wv.canGoBack()) {
                wv.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (wv != null) wv.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (wv != null) wv.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (exoView != null) exoView.release();
        if (mediaView != null) mediaView.release();
        if (wv != null) wv.destroy();
    }
}
