package com.tvbrowser;

import android.content.Context;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统 MediaPlayer 播放层 + 7 控件。
 * 控件：进度/暂停/切集/倍速/选集/返回/自动隐藏。
 * 5 秒无操作自动隐藏，点画面或按 OK 唤出。
 */
public class MediaView extends FrameLayout {
    private MediaPlayer mp;
    private SurfaceView surface;
    private SurfaceHolder holder;
    private LinearLayout bar;
    private SeekBar progress;
    private TextView timeText, speedText, titleText;
    private ImageButton btnBack, btnPrev, btnNext, btnEpisode;
    private TextView speedBtn;
    private PopupWindow episodePopup;
    private List<String> episodeUrls = new ArrayList<>();
    private List<String> episodeNames = new ArrayList<>();
    private int curEp = -1;
    private float[] speeds = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private int speedIdx = 2;
    private Runnable autoHideTask;
    private Runnable tickTask;
    private boolean controlsVisible = false;
    private boolean isPaused = false;

    public MediaView(Context c) {
        super(c);
        init(c);
    }

    public MediaView(Context c, AttributeSet a) {
        super(c, a);
        init(c);
    }

    private int dp2(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams barLp() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams barLpWeight(float w) {
        LinearLayout.LayoutParams lp = barLp();
        lp.weight = w;
        return lp;
    }

    private static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private void init(Context c) {
        setBackgroundColor(Color.BLACK);

        surface = new SurfaceView(c);
        holder = surface.getHolder();
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder h) {
                if (mp != null && !mp.isPlaying()) {
                    try {
                        mp.setDisplay(h);
                        mp.start();
                    } catch (Exception ignored) {}
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder h, int format, int w, int hgt) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder h) {
                if (mp != null && mp.isPlaying()) {
                    mp.pause();
                }
            }
        });
        addView(surface, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Color.parseColor("#88000000"));
        bar.setPadding(dp2(12), dp2(8), dp2(12), dp2(8));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        blp.gravity = 0b10;
        addView(bar, blp);

        btnBack = new ImageButton(c);
        btnBack.setPadding(dp2(8), dp2(8), dp2(8), dp2(8));
        btnBack.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        btnBack.setContentDescription("返回");
        btnBack.setOnClickListener(v -> getHostActivity().finish());
        bar.addView(btnBack, barLp());

        titleText = new TextView(c);
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(16);
        titleText.setPadding(dp2(8), 0, dp2(8), 0);
        titleText.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        titleText.setMaxLines(1);
        bar.addView(titleText, barLpWeight(0.3f));

        btnPrev = new ImageButton(c);
        btnPrev.setPadding(dp2(6), dp2(6), dp2(6), dp2(6));
        btnPrev.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        btnPrev.setContentDescription("上一集");
        btnPrev.setOnClickListener(v -> prevEp());
        bar.addView(btnPrev, barLp());

        btnNext = new ImageButton(c);
        btnNext.setPadding(dp2(6), dp2(6), dp2(6), dp2(6));
        btnNext.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        btnNext.setContentDescription("下一集");
        btnNext.setOnClickListener(v -> nextEp());
        bar.addView(btnNext, barLp());

        speedBtn = new TextView(c);
        speedBtn.setTextColor(Color.WHITE);
        speedBtn.setTextSize(14);
        speedBtn.setText("1.0x");
        speedBtn.setPadding(dp2(10), dp2(6), dp2(10), dp2(6));
        speedBtn.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        speedBtn.setOnClickListener(v -> cycleSpeed());
        bar.addView(speedBtn, barLp());

        btnEpisode = new ImageButton(c);
        btnEpisode.setPadding(dp2(6), dp2(6), dp2(6), dp2(6));
        btnEpisode.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        btnEpisode.setContentDescription("选集");
        btnEpisode.setOnClickListener(v -> showEpisodeList());
        bar.addView(btnEpisode, barLp());

        LinearLayout prog = new LinearLayout(c);
        prog.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams pplp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 0.6f);
        bar.addView(prog, pplp);
        progress = new SeekBar(c);
        progress.setMax(1000);
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser && mp != null && mp.getDuration() > 0) {
                    long total = mp.getDuration();
                    mp.seekTo((int) (total * p / 1000));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {}

            @Override
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        prog.addView(progress, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        timeText = new TextView(c);
        timeText.setTextColor(Color.WHITE);
        timeText.setTextSize(12);
        timeText.setPadding(dp2(8), 0, dp2(8), 0);
        timeText.setText("00:00 / 00:00");
        prog.addView(timeText, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        autoHideTask = new Runnable() {
            @Override
            public void run() {
                hideControls();
            }
        };
        tickTask = new Runnable() {
            @Override
            public void run() {
                refreshTime();
                postDelayed(this, 500);
            }
        };
        setControlsVisible(false);
    }

    private android.app.Activity getHostActivity() {
        Context c = getContext();
        while (c instanceof android.content.ContextWrapper && !(c instanceof android.app.Activity)) {
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return c instanceof android.app.Activity ? (android.app.Activity) c : null;
    }

    public void play(String url, String title) {
        if (url == null || url.isEmpty()) return;
        titleText.setText(title == null ? "" : title);
        if (mp == null) {
            mp = new MediaPlayer();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
        }
        try {
            mp.reset();
            mp.setDataSource(url);
            mp.setDisplay(holder);
            mp.setOnPreparedListener(p -> {
                try {
                    p.setPlaybackParams(new PlaybackParams().setSpeed(speeds[speedIdx]));
                } catch (Exception ignored) {}
                p.start();
                isPaused = false;
                setControlsVisible(true);
            });
            mp.prepareAsync();
            removeCallbacks(tickTask);
            postDelayed(tickTask, 500);
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法播放：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public void setEpisodes(List<String> urls, List<String> names, int current) {
        episodeUrls = urls;
        episodeNames = names;
        curEp = current;
    }

    private void prevEp() {
        if (curEp > 0) {
            curEp--;
            play(episodeUrls.get(curEp), episodeNames.get(curEp));
        }
    }

    private void nextEp() {
        if (curEp >= 0 && curEp < episodeUrls.size() - 1) {
            curEp++;
            play(episodeUrls.get(curEp), episodeNames.get(curEp));
        }
    }

    private void cycleSpeed() {
        speedIdx = (speedIdx + 1) % speeds.length;
        if (mp != null) {
            try {
                mp.setPlaybackParams(new PlaybackParams().setSpeed(speeds[speedIdx]));
            } catch (Exception ignored) {}
        }
        speedBtn.setText(String.format("%.2gx", speeds[speedIdx]));
    }

    private void showEpisodeList() {
        if (episodeUrls.isEmpty()) {
            Toast.makeText(getContext(), "未找到剧集列表", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout lay = new LinearLayout(getContext());
        lay.setOrientation(LinearLayout.VERTICAL);
        lay.setBackgroundColor(Color.parseColor("#161b22"));
        lay.setPadding(dp2(16), dp2(16), dp2(16), dp2(16));
        for (int i = 0; i < episodeNames.size(); i++) {
            final int idx = i;
            TextView t = new TextView(getContext());
            t.setText((i + 1) + ". " + episodeNames.get(i));
            t.setTextColor(i == curEp ? 0xff1f6feb : 0xffffffff);
            t.setTextSize(18);
            t.setPadding(0, dp2(8), 0, dp2(8));
            t.setOnClickListener(v -> {
                curEp = idx;
                play(episodeUrls.get(idx), episodeNames.get(idx));
                if (episodePopup != null) episodePopup.dismiss();
            });
            lay.addView(t, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        episodePopup = new PopupWindow(lay, dp2(280), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        episodePopup.showAtLocation(this, 0b11, 0, 0);
    }

    private void refreshTime() {
        if (mp == null) return;
        int dur = mp.getDuration();
        if (dur <= 0) return;
        int pos = mp.getCurrentPosition();
        progress.setProgress(pos * 1000 / dur);
        timeText.setText(fmt(pos) + " / " + fmt(dur));
    }

    private static String fmt(int ms) {
        long s = ms / 1000;
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    private void toggleControls() {
        setControlsVisible(true);
        removeCallbacks(autoHideTask);
        postDelayed(autoHideTask, 5000);
    }

    private void hideControls() {
        setControlsVisible(false);
    }

    private void setControlsVisible(boolean vis) {
        if (controlsVisible == vis) return;
        controlsVisible = vis;
        if (bar != null) bar.setVisibility(vis ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (ev.getAction() == KeyEvent.ACTION_DOWN) {
            switch (ev.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    toggleControls();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (mp != null) {
                        int delta = ev.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN ? -5000 : 5000;
                        int dur = mp.getDuration();
                        int pos = mp.getCurrentPosition() + delta;
                        if (pos >= 0 && pos <= dur) mp.seekTo(pos);
                    }
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    if (mp != null) {
                        if (mp.isPlaying()) {
                            mp.pause();
                            isPaused = true;
                        } else {
                            mp.start();
                            isPaused = false;
                        }
                    }
                    return true;
            }
        }
        return super.dispatchKeyEvent(ev);
    }

    public void pause() {
        if (mp != null && mp.isPlaying()) {
            mp.pause();
            isPaused = true;
        }
    }

    public void resume() {
        if (mp != null && isPaused) {
            mp.start();
            isPaused = false;
        }
    }

    public void release() {
        removeCallbacks(tickTask);
        removeCallbacks(autoHideTask);
        if (mp != null) {
            try {
                mp.release();
            } catch (Exception ignored) {}
            mp = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }
}
