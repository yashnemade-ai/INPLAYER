package com.inplayer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.inplayer.NativePlayerActivity;

public class PlayerActivity extends AppCompatActivity {

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. FULLSCREEN
        applyFullScreen();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Performance Fixes
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE); // Testing ke liye cache off

        // 🚀 BRIDGE: JavaScript Connection
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidPortal");

        // 🛠️ DEBUGGING: Agar file nahi mili toh alert aayega
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                String errorMsg = "WebView Error: " + error.getDescription();
                Toast.makeText(PlayerActivity.this, errorMsg, Toast.LENGTH_LONG).show();
            }
        });

        webView.setWebChromeClient(new WebChromeClient());

        // 📂 LOAD FILE: Pakka karein file assets folder mein hai
        webView.loadUrl("file:///android_asset/index.html");
    }

    // JS Bridge Functions
    public class WebAppInterface {
        Context mContext;
        WebAppInterface(Context c) { mContext = c; }

        @JavascriptInterface
        public void launchNative(String url, String name) {
            Intent intent = new Intent(mContext, NativePlayerActivity.class);
            intent.putExtra("url", url);
            intent.putExtra("name", name);
            mContext.startActivity(intent);
        }

        @JavascriptInterface
        public void openExternal(String url) {
            try {
                android.net.Uri uri = android.net.Uri.parse(url);
                Intent intent = new Intent(Intent.ACTION_VIEW);

                // 🔥 Sabse important line: Data aur Type dono ek saath set karna
                intent.setDataAndType(uri, "video/*");

                // VLC aur MX Player ko force karne ke liye
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // "Open With" menu dikhana
                Intent chooser = Intent.createChooser(intent, "Select Video Player");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                mContext.startActivity(chooser);
            } catch (Exception e) {
                // Agar koi video player na mile toh error
                runOnUiThread(() ->
                        android.widget.Toast.makeText(mContext, "No Video Player (VLC/MX) Found!", android.widget.Toast.LENGTH_SHORT).show()
                );
            }
        }
    }

    private void applyFullScreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            webView.evaluateJavascript("if(window.handleBackAction){handleBackAction();}else{history.back();}", null);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}