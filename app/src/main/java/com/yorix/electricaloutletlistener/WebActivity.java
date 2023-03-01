package com.yorix.electricaloutletlistener;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.util.ArrayList;

public class WebActivity extends AppCompatActivity {
    private static int tryCount;
    private final IO io = new IO(this);
    private String
            scheduleHtmlFilename,
            scheduleImageFilename,
            wrapperHtmlFilename,
            tableProxy,
            tableParentId,
            jsRequestsFilename,
            cellClass;
    private ArrayList<String> jsRequests;
    private WebView webView;

    @JavascriptInterface
    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface", "SourceLockedOrientationActivity"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        jsRequestsFilename = getString(R.string.js_requests_filename);
        scheduleHtmlFilename = getString(R.string.schedule_html_filename);
        scheduleImageFilename = getString(R.string.schedule_image_filename);
        wrapperHtmlFilename = getString(R.string.wrapper_html_filename);
        tableProxy = getString(R.string.table_proxy);
        String url = getIntent().getExtras().getString("scheduleUrl");
        tableParentId = getIntent().getExtras().getString("tableParentId");
        cellClass = getIntent().getExtras().getString("cellClass");
        long delay = getIntent().getExtras().getLong("delay");

        if (!new File(getFilesDir().getAbsolutePath(), wrapperHtmlFilename).exists())
            io.fromResToFileDir(R.raw.wrapper, wrapperHtmlFilename);


        webView = findViewById(R.id.web_view);
        WebSettings settings = webView.getSettings();
        settings.setUseWideViewPort(true);
        settings.setJavaScriptEnabled(true);
        settings.setSupportMultipleWindows(false);
        settings.setLoadsImagesAutomatically(true);

        webView.loadUrl(url);
        webView.addJavascriptInterface(new HtmlHandlerJsInterface(), "HtmlHandler");
        webView.setWebViewClient(new WebParser(delay));
    }

    @Override
    protected void onResume() {
        super.onResume();
        jsRequests = io.readFromFileToListString(jsRequestsFilename);
    }

    private class WebParser extends WebViewClient {
        IO io;
        private final long delay;

        WebParser(long delay) {
            io = new IO(WebActivity.this);
            this.delay = delay;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            view.setWebViewClient(new WebViewClient());

            for (int i = 0; i < jsRequests.size(); i++) {
                int countValue = i;
                view.postDelayed(() ->
                                view.evaluateJavascript(jsRequests.get(countValue), null),
                        delay * i);
            }
            view.postDelayed(() ->
                            view.evaluateJavascript(
                                    "javascript:window.HtmlHandler.handleHtml(" +
                                            "'<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');",
                                    handled -> {
                                        if (Boolean.parseBoolean(handled)) {
                                            view.evaluateJavascript(
                                                    "javascript:window.HtmlHandler.render();",
                                                    null);
                                            tryCount = 0;
                                        } else if (tryCount++ < 2) {
                                            WebActivity.this.sendBroadcast(new Intent("checkSchedule"));
                                            finish();
                                        } else {
                                            tryCount = 0;
                                            finish();
                                        }
                                    }),
                    delay * jsRequests.size());
        }
    }

    private class HtmlHandlerJsInterface {
        @JavascriptInterface
        public boolean handleHtml(String html) {
            Document document = Jsoup.parse(html);

            if (document.getElementById(tableParentId) == null
                    || document.getElementById(tableParentId).getElementsByClass(cellClass).isEmpty()
                    || !WebActivity.this.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED))
                return false;
            else
                webView.post(() -> {
                    String table = document.getElementById(tableParentId).getAllElements().get(0).html();
                    String wrapper = io.readData(wrapperHtmlFilename);
                    String doc = wrapper.replace(tableProxy, table);
                    io.saveData(scheduleHtmlFilename, doc, MODE_PRIVATE);
                    webView.loadDataWithBaseURL(null, doc, "text/html", "utf-8", null);
                });
            return true;
        }

        @JavascriptInterface
        public void render() {
            webView.postDelayed(() -> {
                Bitmap bitmap;
                try {
                    bitmap = Bitmap.createBitmap(webView.getWidth(), webView.getHeight(), Bitmap.Config.RGB_565);
                    Canvas canvas = new Canvas(bitmap);
                    webView.draw(canvas);
                    io.savePicture(bitmap, scheduleImageFilename);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                finish();
            }, 1000);
        }
    }
}
