package com.inplayer;

import android.annotation.SuppressLint;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen logic
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        webView = new WebView(this);
        setContentView(webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);

        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidPortal");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl("file:///android_asset/index.html");
    }

    // ✅ PIP CODE: Triggered when Home button is pressed
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        enterPiPMode();
    }

    private void enterPiPMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                PictureInPictureParams.Builder pipBuilder = new PictureInPictureParams.Builder();
                // Aspect ratio 16:9 for video feel
                Rational aspectRatio = new Rational(16, 9);
                pipBuilder.setAspectRatio(aspectRatio);
                enterPictureInPictureMode(pipBuilder.build());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            enterPictureInPictureMode();
        }
    }

    public class WebAppInterface {
        Context mContext;
        WebAppInterface(Context c) { mContext = c; }

        @JavascriptInterface
        public void launchNative(String url, String name, String url2, String cat) { // ✅ Must have 4 Strings
            try {
                Intent intent = new Intent(mContext, NativePlayerActivity.class);
                intent.putExtra("url", url);
                intent.putExtra("name", name);
                intent.putExtra("url2", url2);
                intent.putExtra("category", cat);
                mContext.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public void openExternal(String url) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse(url), "video/*");
                mContext.startActivity(Intent.createChooser(intent, "Stream with:"));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(mContext, "No external player found", Toast.LENGTH_SHORT).show());
            }
        }

        @JavascriptInterface
        public void exitApp() {
            runOnUiThread(() -> {
                finishAffinity();
                System.exit(0);
            });
        }
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            webView.evaluateJavascript("handleBackAction();", null);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }
}