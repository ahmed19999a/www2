package site.csahati.apppro;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String START_URL = "https://apppro.csahati.site/mobile-preview";
    private static final String ALLOWED_HOST = "apppro.csahati.site";
    private static final String VERSION_URL = "https://apppro.csahati.site/mobile-version.json";
    private static final long VERSION_CHECK_MS = 30_000L;
    private static final int FILE_CHOOSER_REQUEST = 8001;
    private static final int WEB_PERMISSION_REQUEST = 8002;
    private static final int GEO_PERMISSION_REQUEST = 8003;

    private FrameLayout root;
    private WebView webView;
    private ProgressBar pageProgress;
    private ProgressBar firstLoadSpinner;
    private LinearLayout errorPanel;
    private TextView errorDetails;

    private ValueCallback<Uri[]> fileChooserCallback;
    private PermissionRequest pendingWebPermission;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private String lastRemoteVersion;
    private boolean forceNoCacheUntilFinish = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        buildUi();
        configureWebView();

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(START_URL);
        }

        startVersionWatcher();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        pageProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pageProgress.setMax(100);
        pageProgress.setProgress(0);
        pageProgress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP
        );
        root.addView(pageProgress, progressLp);

        firstLoadSpinner = new ProgressBar(this);
        FrameLayout.LayoutParams spinnerLp = new FrameLayout.LayoutParams(
                dp(44), dp(44), Gravity.CENTER
        );
        root.addView(firstLoadSpinner, spinnerLp);

        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setPadding(dp(32), dp(32), dp(32), dp(32));
        errorPanel.setBackgroundColor(Color.WHITE);
        errorPanel.setVisibility(View.GONE);
        errorPanel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = new TextView(this);
        title.setText("تعذر الاتصال");
        title.setTextSize(22);
        title.setTextColor(Color.rgb(15, 23, 42));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        errorPanel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        errorDetails = new TextView(this);
        errorDetails.setText("تحقق من اتصال الإنترنت ثم حاول مرة أخرى");
        errorDetails.setTextSize(14);
        errorDetails.setTextColor(Color.rgb(100, 116, 139));
        errorDetails.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(-1, -2);
        detailsLp.topMargin = dp(10);
        detailsLp.bottomMargin = dp(22);
        errorPanel.addView(errorDetails, detailsLp);

        Button retry = new Button(this);
        retry.setText("إعادة المحاولة");
        retry.setTextSize(16);
        retry.setAllCaps(false);
        retry.setTextColor(Color.WHITE);
        retry.setBackgroundColor(Color.rgb(15, 118, 110));
        retry.setOnClickListener(v -> {
            hideError();
            if (webView.getUrl() == null) webView.loadUrl(START_URL);
            else webView.reload();
        });
        errorPanel.addView(retry, new LinearLayout.LayoutParams(dp(190), dp(52)));

        root.addView(errorPanel, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void configureWebView() {
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " AlMonjezAndroid/2.0");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settings.setOffscreenPreRaster(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        webView.setBackgroundColor(Color.WHITE);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                hideError();
                pageProgress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                firstLoadSpinner.setVisibility(View.GONE);
                pageProgress.setVisibility(View.GONE);
                injectEnhancements();
                if (forceNoCacheUntilFinish) {
                    forceNoCacheUntilFinish = false;
                    view.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    String message = error == null ? "تعذر تحميل الصفحة" : String.valueOf(error.getDescription());
                    showError(message);
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                showError("تعذر التحقق من أمان الاتصال بالموقع");
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                runOnUiThread(() -> recreate());
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                pageProgress.setProgress(newProgress);
                if (newProgress >= 100) pageProgress.setVisibility(View.GONE);
                else pageProgress.setVisibility(View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    fileChooserCallback = null;
                    Toast.makeText(MainActivity.this, "تعذر فتح الملفات", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> handleWebPermissionRequest(request));
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (!isAllowedOrigin(origin)) {
                    callback.invoke(origin, false, false);
                    return;
                }
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false);
                } else {
                    pendingGeoCallback = callback;
                    pendingGeoOrigin = origin;
                    requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, GEO_PERMISSION_REQUEST);
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView child = new WebView(MainActivity.this);
                child.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, String url) {
                        openPopupUrl(url);
                        v.destroy();
                        return true;
                    }
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        openPopupUrl(request.getUrl().toString());
                        v.destroy();
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(child);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                webView.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url != null && (url.startsWith("blob:") || url.startsWith("data:"))) {
                requestBlobDownload(url, contentDisposition);
            } else {
                downloadNormally(url, userAgent, contentDisposition, mimeType);
            }
        });
    }

    private boolean handleNavigation(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();

        if ("http".equals(scheme) || "https".equals(scheme)) {
            if (isAllowedHost(uri.getHost())) return false;
            openExternal(uri);
            return true;
        }

        if ("blob".equals(scheme) || "data".equals(scheme)) return false;

        if ("intent".equals(scheme)) {
            try {
                Intent intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
                else if (intent.getPackage() != null) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=" + intent.getPackage())));
                }
            } catch (Exception ignored) { }
            return true;
        }

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) { }
        return true;
    }

    private boolean isAllowedHost(String host) {
        return host != null && (host.equalsIgnoreCase(ALLOWED_HOST) || host.endsWith("." + ALLOWED_HOST));
    }

    private boolean isAllowedOrigin(String origin) {
        try { return isAllowedHost(Uri.parse(origin).getHost()); }
        catch (Exception e) { return false; }
    }

    private void openPopupUrl(String url) {
        Uri uri = Uri.parse(url);
        if (isAllowedHost(uri.getHost())) webView.loadUrl(url);
        else openExternal(uri);
    }

    private void openExternal(Uri uri) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (Exception e) { Toast.makeText(this, "لا يوجد تطبيق لفتح الرابط", Toast.LENGTH_SHORT).show(); }
    }

    private void handleWebPermissionRequest(PermissionRequest request) {
        if (!isAllowedOrigin(request.getOrigin().toString())) {
            request.deny();
            return;
        }

        ArrayList<String> androidPermissions = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                androidPermissions.add(Manifest.permission.CAMERA);
            }
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                androidPermissions.add(Manifest.permission.RECORD_AUDIO);
            }
        }

        if (androidPermissions.isEmpty()) {
            request.grant(request.getResources());
        } else {
            pendingWebPermission = request;
            requestPermissions(androidPermissions.toArray(new String[0]), WEB_PERMISSION_REQUEST);
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void downloadNormally(String url, String userAgent, String contentDisposition, String mimeType) {
        if (url == null || url.isEmpty()) return;
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            if (mimeType != null && !mimeType.isEmpty()) request.setMimeType(mimeType);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) request.addRequestHeader("Cookie", cookie);
            String name = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType);
            request.setTitle(name);
            request.setDescription("جاري تنزيل الملف");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
            Toast.makeText(this, "بدأ تنزيل الملف", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "تعذر تنزيل الملف", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestBlobDownload(String url, String contentDisposition) {
        String safeUrl = JSONObject.quote(url);
        String name = android.webkit.URLUtil.guessFileName(url, contentDisposition, null);
        String safeName = JSONObject.quote(name == null ? "document" : name);
        String js = "(async function(){try{" +
                "var r=await fetch(" + safeUrl + ");var b=await r.blob();" +
                "var fr=new FileReader();fr.onload=function(){AndroidNative.saveBase64(fr.result," + safeName + ");};" +
                "fr.readAsDataURL(b);" +
                "}catch(e){AndroidNative.toast('تعذر تنزيل الملف');}})();";
        webView.evaluateJavascript(js, null);
    }

    private void injectEnhancements() {
        String js = "(function(){" +
                "if(window.__ALMONJEZ_ANDROID_V2__)return;window.__ALMONJEZ_ANDROID_V2__=true;" +
                "try{if(!navigator.share){Object.defineProperty(navigator,'share',{configurable:true,value:function(d){d=d||{};AndroidNative.share(d.title||'',d.text||'',d.url||location.href);return Promise.resolve();}});}}catch(e){}" +
                "document.addEventListener('click',function(ev){var a=ev.target&&ev.target.closest?ev.target.closest('a[download]'):null;if(!a)return;var h=a.href||'';if(h.indexOf('blob:')===0||h.indexOf('data:')===0){ev.preventDefault();fetch(h).then(function(r){return r.blob();}).then(function(b){var f=new FileReader();f.onload=function(){AndroidNative.saveBase64(f.result,a.download||'document');};f.readAsDataURL(b);}).catch(function(){AndroidNative.toast('تعذر تنزيل الملف');});}},true);" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private void saveBase64ToDownloads(String dataUrl, String requestedName) {
        io.execute(() -> {
            try {
                int comma = dataUrl.indexOf(',');
                if (comma < 0) throw new IllegalArgumentException("Invalid data URL");
                String header = dataUrl.substring(0, comma);
                String encoded = dataUrl.substring(comma + 1);
                String mime = "application/octet-stream";
                int colon = header.indexOf(':');
                int semi = header.indexOf(';');
                if (colon >= 0 && semi > colon) mime = header.substring(colon + 1, semi);
                byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
                String fileName = sanitizeFileName(requestedName);
                if (!fileName.contains(".")) fileName += extensionForMime(mime);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Downloads.MIME_TYPE, mime);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AlMonjez");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new IllegalStateException("Cannot create download");
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new IllegalStateException("Cannot open download");
                        out.write(bytes);
                    }
                } else {
                    java.io.File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists()) dir.mkdirs();
                    java.io.File file = new java.io.File(dir, fileName);
                    try (OutputStream out = new java.io.FileOutputStream(file)) { out.write(bytes); }
                }

                runOnUiThread(() -> Toast.makeText(this, "تم حفظ الملف في التنزيلات", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "تعذر حفظ الملف", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) return "document_" + System.currentTimeMillis();
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private String extensionForMime(String mime) {
        if ("application/pdf".equalsIgnoreCase(mime)) return ".pdf";
        if ("image/png".equalsIgnoreCase(mime)) return ".png";
        if ("image/jpeg".equalsIgnoreCase(mime)) return ".jpg";
        return ".bin";
    }

    private void share(String title, String text, String url) {
        String body = "";
        if (text != null && !text.trim().isEmpty()) body = text.trim();
        if (url != null && !url.trim().isEmpty()) body += (body.isEmpty() ? "" : "\n") + url.trim();
        if (body.isEmpty()) body = webView.getUrl() == null ? START_URL : webView.getUrl();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, body);
        if (title != null && !title.isEmpty()) intent.putExtra(Intent.EXTRA_TITLE, title);
        startActivity(Intent.createChooser(intent, title == null || title.isEmpty() ? "مشاركة" : title));
    }

    private void showError(String details) {
        runOnUiThread(() -> {
            firstLoadSpinner.setVisibility(View.GONE);
            pageProgress.setVisibility(View.GONE);
            errorDetails.setText(details == null || details.trim().isEmpty()
                    ? "تحقق من اتصال الإنترنت ثم حاول مرة أخرى" : details);
            errorPanel.setVisibility(View.VISIBLE);
        });
    }

    private void hideError() {
        errorPanel.setVisibility(View.GONE);
    }

    private void startVersionWatcher() {
        handler.post(new Runnable() {
            @Override public void run() {
                checkRemoteVersion();
                handler.postDelayed(this, VERSION_CHECK_MS);
            }
        });
    }

    private void checkRemoteVersion() {
        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(VERSION_URL + "?_=" + System.currentTimeMillis());
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) return;
                StringBuilder text = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line; while ((line = reader.readLine()) != null) text.append(line);
                }
                JSONObject json = new JSONObject(text.toString());
                String version = json.optString("web_version", json.optString("version", "")).trim();
                if (version.isEmpty()) return;
                runOnUiThread(() -> {
                    if (lastRemoteVersion == null) lastRemoteVersion = version;
                    else if (!lastRemoteVersion.equals(version)) {
                        lastRemoteVersion = version;
                        hardRefresh(version);
                    }
                });
            } catch (Exception ignored) {
                // Remote refresh is optional; normal WebView operation continues if the file is absent.
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void hardRefresh(String version) {
        String current = webView.getUrl();
        if (current == null || current.startsWith("about:")) current = START_URL;
        try {
            String fragment = "";
            int hash = current.indexOf('#');
            if (hash >= 0) { fragment = current.substring(hash); current = current.substring(0, hash); }
            String separator = current.contains("?") ? "&" : "?";
            current += separator + "_android_web_version=" + URLEncoder.encode(version, "UTF-8")
                    + "&_android_refresh=" + System.currentTimeMillis() + fragment;
        } catch (Exception ignored) { current = START_URL; }
        forceNoCacheUntilFinish = true;
        webView.stopLoading();
        webView.clearCache(true);
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.loadUrl(current);
    }

    private void hideCustomView() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        webView.setVisibility(View.VISIBLE);
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        checkRemoteVersion();
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        io.shutdownNow();
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidNative");
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (customView != null) { hideCustomView(); return; }
        if (webView.canGoBack()) { webView.goBack(); return; }
        super.onBackPressed();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || fileChooserCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                result = new Uri[count];
                for (int i = 0; i < count; i++) result[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        fileChooserCallback.onReceiveValue(result);
        fileChooserCallback = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WEB_PERMISSION_REQUEST && pendingWebPermission != null) {
            boolean allGranted = true;
            for (int result : grantResults) if (result != PackageManager.PERMISSION_GRANTED) allGranted = false;
            if (allGranted) pendingWebPermission.grant(pendingWebPermission.getResources());
            else pendingWebPermission.deny();
            pendingWebPermission = null;
        } else if (requestCode == GEO_PERMISSION_REQUEST && pendingGeoCallback != null) {
            boolean granted = hasLocationPermission();
            pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class AndroidBridge {
        @JavascriptInterface
        public void share(String title, String text, String url) {
            runOnUiThread(() -> MainActivity.this.share(title, text, url));
        }

        @JavascriptInterface
        public void shareUrl(String url, String title) {
            runOnUiThread(() -> MainActivity.this.share(title, "", url));
        }

        @JavascriptInterface
        public void refresh() {
            runOnUiThread(() -> webView.reload());
        }

        @JavascriptInterface
        public void saveBase64(String dataUrl, String fileName) {
            saveBase64ToDownloads(dataUrl, fileName);
        }

        @JavascriptInterface
        public void toast(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }
    }
}
