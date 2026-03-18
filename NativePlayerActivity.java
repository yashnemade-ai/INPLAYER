package com.inplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TrackSelectionDialogBuilder;
import okhttp3.OkHttpClient;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@OptIn(markerClass = UnstableApi.class)
public class NativePlayerActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView playerView;
    private ImageButton btnSettings, btnAspectRatio;
    private View btnNext;
    private LinearLayout topBar;
    private String currentUrl, backupUrl, category;
    private boolean isBackupTried = false, isSwiping = false;
    private int currentRatioMode = 0;

    private float baseX, baseY;
    private int screenWidth, screenHeight;
    private AudioManager audioManager;
    private SharedPreferences sharedPrefs;
    private TextView gestureIndicator;

    private boolean isInitialLoad = true;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable switchRunnable, nextEpRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUI();
        setContentView(R.layout.activity_native_player);

        // 1. View Initialization
        playerView = findViewById(R.id.player_view);
        btnSettings = findViewById(R.id.btn_settings);
        btnAspectRatio = findViewById(R.id.btn_aspect_ratio);
        btnNext = findViewById(R.id.btn_next_episode);
        topBar = findViewById(R.id.top_controls_bar);

        // 2. Data from Intent
        currentUrl = getIntent().getStringExtra("url");
        backupUrl = getIntent().getStringExtra("url2");
        category = getIntent().getStringExtra("category");
        if (category == null) category = "Live";

        // 3. Persistence & System Services
        sharedPrefs = getSharedPreferences("ChannelRatios", MODE_PRIVATE);
        currentRatioMode = sharedPrefs.getInt(currentUrl, 0);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;

        initIndicator();

        // 4. ExoPlayer Setup
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true);

        player = new ExoPlayer.Builder(this, renderersFactory).build();
        playerView.setPlayer(player);
        player.setPlayWhenReady(true);
        applySavedAspectRatio(currentRatioMode);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    // ✅ Check: Agar video pehli baar load ho rahi hai, toh controls dikhao
                    if (isInitialLoad) {
                        playerView.setControllerShowTimeoutMs(0); // Hamesha dikhega
                        playerView.showController();
                    } else {
                        // ✅ Agar beech mein buffer liya, toh controls hide hi rakho
                        playerView.hideController();
                    }

                    if (!"Web Series".equalsIgnoreCase(category)) startSwitchTimer();
                }
                else if (state == Player.STATE_READY) {
                    stopSwitchTimer();
                    hideIndicator();
                    if ("Web Series".equalsIgnoreCase(category)) startNextEpisodeCheck();

                    // Paused state mein controls dikhana zaroori hai
                    if (!player.getPlayWhenReady()) {
                        playerView.setControllerShowTimeoutMs(0);
                        playerView.showController();
                    }
                }
                else if (state == Player.STATE_ENDED) {
                    if ("Web Series".equalsIgnoreCase(category)) playNextEpisode();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    // ✅ Video start hote hi flag false kar do
                    isInitialLoad = false;
                    playerView.setControllerShowTimeoutMs(1000); // 1 sec auto-hide
                    playerView.hideController();
                } else {
                    // Sirf tabhi controls dikhao jab buffering na ho (yani pause ho)
                    if (player.getPlaybackState() != Player.STATE_BUFFERING) {
                        playerView.setControllerShowTimeoutMs(0);
                        playerView.showController();
                    }
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                stopSwitchTimer();
                isInitialLoad = true; // Error ke baad reset karein taaki backup load pe controls dikhein
                if (!"Web Series".equalsIgnoreCase(category)) handleBackupSwitch();
            }
        });

        // 5. Restore Button Listeners
        btnAspectRatio.setOnClickListener(v -> toggleAspectRatio());
        btnSettings.setOnClickListener(v -> showSettingsMenu());
        if (btnNext != null) btnNext.setOnClickListener(v -> playNextEpisode());

        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) v -> {
            if (topBar != null) topBar.setVisibility(v);
        });

        // 6. Restore Swipe Gestures
        playerView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    baseX = event.getX(); baseY = event.getY();
                    isSwiping = false; break;
                case MotionEvent.ACTION_MOVE:
                    float deltaY = baseY - event.getY();
                    if (Math.abs(deltaY) > 30) {
                        isSwiping = true;
                        if (baseX < (float) screenWidth / 2) adjustBrightness(deltaY);
                        else adjustVolume(deltaY);
                        baseY = event.getY();
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    mainHandler.postDelayed(this::hideIndicator, 500);
                    if (isSwiping) return true;
                    break;
            }
            return false;
        });

        startAdvancedPlayback();
    }

    private void showSettingsMenu() {
        String[] options = {"Video Quality", "Audio Language"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        TextView titleView = new TextView(this);
        titleView.setText("Settings");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(22);
        titleView.setPadding(60, 40, 0, 30);
        titleView.setTypeface(null, Typeface.BOLD);
        builder.setCustomTitle(titleView);
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) new TrackSelectionDialogBuilder(this, "Select Quality", player, C.TRACK_TYPE_VIDEO).build().show();
            else new TrackSelectionDialogBuilder(this, "Select Audio", player, C.TRACK_TYPE_AUDIO).build().show();
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#333333")));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(window.getAttributes());
            lp.width = (int) (screenWidth * 0.65);
            window.setAttributes(lp);
        }
    }

    private void toggleAspectRatio() {
        currentRatioMode = (currentRatioMode + 1) % 3;
        applySavedAspectRatio(currentRatioMode);
        sharedPrefs.edit().putInt(currentUrl, currentRatioMode).apply();
    }

    private void applySavedAspectRatio(int mode) {
        if (playerView == null) return;
        if (mode == 0) playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        else if (mode == 1) playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
        else if (mode == 2) playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
    }

    private void startAdvancedPlayback() {
        isInitialLoad = true;
        if (currentUrl == null || currentUrl.isEmpty()) return;
        String decodedUrl = currentUrl.replace("%7C", "|").replace("%7c", "|");
        String streamUrl = decodedUrl;
        Map<String, String> requestHeaders = new HashMap<>();
        String drmKeyString = null;

        if (decodedUrl.contains("|")) {
            String[] urlParts = decodedUrl.split("\\|");
            streamUrl = urlParts[0].trim();
            if (urlParts.length > 1) {
                for (String pair : urlParts[1].split("&")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim();
                        String value = kv[1].trim();
                        if (key.equalsIgnoreCase("cookie")) requestHeaders.put("Cookie", value);
                        else if (key.equalsIgnoreCase("user-agent")) requestHeaders.put("User-Agent", value);
                        else if (key.equalsIgnoreCase("referer")) requestHeaders.put("Referer", value);
                        else if (key.equalsIgnoreCase("origin")) requestHeaders.put("Origin", value);
                        else if (key.equalsIgnoreCase("drmLicense")) drmKeyString = value;
                    }
                }
            }
        }

        if (streamUrl.contains("sunnxt")) {
            requestHeaders.put("Referer", "https://www.sunnxt.com/");
            requestHeaders.put("Origin", "https://www.sunnxt.com");
        }

        HttpDataSource.Factory dataSourceFactory = new OkHttpDataSource.Factory(new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS).build()).setDefaultRequestProperties(requestHeaders);

        DefaultMediaSourceFactory msFactory = new DefaultMediaSourceFactory(this, new DefaultExtractorsFactory()
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES))
                .setDataSourceFactory(dataSourceFactory);

        MediaItem.Builder mb = new MediaItem.Builder().setUri(streamUrl);
        String lower = streamUrl.toLowerCase();
        if (lower.contains(".m3u8") || lower.contains(".php")) mb.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (lower.contains(".mpd")) mb.setMimeType(MimeTypes.APPLICATION_MPD);
        else if (lower.contains(".ts")) mb.setMimeType(MimeTypes.VIDEO_MP2T);

        if (drmKeyString != null && drmKeyString.contains(":")) {
            try {
                String[] keys = drmKeyString.split(":");
                String drmJson = "{\"keys\":[{\"kty\":\"oct\",\"k\":\"" + base64UrlEncode(keys[1]) + "\",\"kid\":\"" + base64UrlEncode(keys[0]) + "\"}],\"type\":\"temporary\"}";
                LocalMediaDrmCallback drmCallback = new LocalMediaDrmCallback(drmJson.getBytes());
                DrmSessionManager drmSessionManager = new DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .build(drmCallback);
                player.setMediaSource(msFactory.setDrmSessionManagerProvider(unused -> drmSessionManager).createMediaSource(mb.build()));
            } catch (Exception e) { player.setMediaSource(msFactory.createMediaSource(mb.build())); }
        } else {
            player.setMediaSource(msFactory.createMediaSource(mb.build()));
        }
        player.prepare();
        player.play();
    }

    private void handleBackupSwitch() {
        if (backupUrl != null && !backupUrl.isEmpty() && !isBackupTried) {
            isBackupTried = true; currentUrl = backupUrl;
            if (category != null && !category.equalsIgnoreCase("Movies") && !category.equalsIgnoreCase("Web Series")) {
                runOnUiThread(() -> Toast.makeText(this, "Switched to Backup", Toast.LENGTH_SHORT).show());
            }
            startAdvancedPlayback();
        }
    }

    private void startNextEpisodeCheck() {
        if (nextEpRunnable != null) mainHandler.removeCallbacks(nextEpRunnable);
        nextEpRunnable = new Runnable() {
            @Override
            public void run() {
                if (player != null && player.isPlaying() && "Web Series".equalsIgnoreCase(category)) {
                    long timeLeft = player.getDuration() - player.getCurrentPosition();
                    if (timeLeft <= 10000 && timeLeft > 500 && backupUrl != null && !backupUrl.isEmpty()) {
                        if (btnNext != null) btnNext.setVisibility(View.VISIBLE);
                    } else {
                        if (btnNext != null) btnNext.setVisibility(View.GONE);
                    }
                }
                mainHandler.postDelayed(this, 1000);
            }
        };
        mainHandler.post(nextEpRunnable);
    }

    private void playNextEpisode() {
        if (backupUrl != null && !backupUrl.isEmpty()) {
            currentUrl = backupUrl; backupUrl = ""; isBackupTried = false;
            if (btnNext != null) btnNext.setVisibility(View.GONE);
            startAdvancedPlayback();
        }
    }

    private void initIndicator() {
        gestureIndicator = new TextView(this);
        gestureIndicator.setTextColor(Color.WHITE);
        gestureIndicator.setTextSize(22);
        gestureIndicator.setTypeface(null, Typeface.BOLD);
        gestureIndicator.setPadding(60, 30, 60, 30);
        gestureIndicator.setGravity(Gravity.CENTER);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(30);
        shape.setColor(Color.parseColor("#CC000000"));
        gestureIndicator.setBackground(shape);
        gestureIndicator.setVisibility(View.GONE);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        ((ViewGroup) playerView).addView(gestureIndicator, params);
    }

    private void showIndicator(String text) {
        gestureIndicator.setText(text);
        gestureIndicator.setVisibility(View.VISIBLE);
        gestureIndicator.setAlpha(1.0f);
    }

    private void hideIndicator() { gestureIndicator.setVisibility(View.GONE); }

    private void adjustVolume(float deltaY) {
        int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        currentVol = (deltaY > 0) ? Math.min(currentVol + 1, maxVol) : Math.max(currentVol - 1, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVol, 0);
        showIndicator("🔊  " + (int) (((float) currentVol / maxVol) * 100) + "%");
    }

    private void adjustBrightness(float deltaY) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        float brightness = lp.screenBrightness;
        if (brightness < 0) brightness = 0.5f;
        brightness = (deltaY > 0) ? Math.min(brightness + 0.04f, 1.0f) : Math.max(brightness - 0.04f, 0.01f);
        lp.screenBrightness = brightness;
        getWindow().setAttributes(lp);
        showIndicator("☀️  " + (int) (brightness * 100) + "%");
    }

    private void startSwitchTimer() {
        stopSwitchTimer();
        switchRunnable = () -> { if (player != null && player.getPlaybackState() == Player.STATE_BUFFERING) handleBackupSwitch(); };
        mainHandler.postDelayed(switchRunnable, 13000);
    }

    private void stopSwitchTimer() { if (switchRunnable != null) mainHandler.removeCallbacks(switchRunnable); }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private String base64UrlEncode(String hex) { try { byte[] data = new byte[hex.length() / 2]; for (int i = 0; i < data.length; i++) data[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16); return android.util.Base64.encodeToString(data, android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP).trim(); } catch (Exception e) { return ""; } }

    @Override protected void onPause() { super.onPause(); stopSwitchTimer(); if (player != null) player.pause(); }
    @Override protected void onDestroy() {
        super.onDestroy();
        stopSwitchTimer();
        mainHandler.removeCallbacksAndMessages(null);
        if (player != null) { player.stop(); player.release(); player = null; }
    }
}