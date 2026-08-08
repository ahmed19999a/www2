package site.csahati.apppro;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String WEB_URL = "https://apppro.csahati.site/mobile-preview";
    private static final String HOST = "apppro.csahati.site";
    private static final String VERSION_URL = "https://apppro.csahati.site/mobile-version.json";
    private static final long VERSION_CHECK_MS = 10000L;

    private FrameLayout root;
    private LinearLayout loginPanel;
    private EditText username;
    private EditText password;
    private CheckBox remember;
    private Button loginButton;
    private ProgressBar progress;
    private TextView errorText;
    private WebView webView;
    private boolean authenticated = false;
    private boolean loginPending = false;
    private String pendingUser = "";
    private String pendingPass = "";
    private String lastVersion = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        buildUi();
        configureWebView();
        webView.loadUrl(WEB_URL);
        startVersionWatcher();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        webView = new WebView(this);
        webView.setVisibility(View.INVISIBLE);
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));

        loginPanel = new LinearLayout(this);
        loginPanel.setOrientation(LinearLayout.VERTICAL);
        loginPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        loginPanel.setPadding(dp(28), dp(40), dp(28), dp(32));
        loginPanel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        loginPanel.setBackgroundColor(Color.WHITE);
        root.addView(loginPanel, new FrameLayout.LayoutParams(-1, -1));

        View spacer1 = new View(this);
        loginPanel.addView(spacer1, new LinearLayout.LayoutParams(1, 0, 1f));

        TextView mark = new TextView(this);
        mark.setText("✓");
        mark.setTextSize(42);
        mark.setTextColor(Color.rgb(15,118,110));
        mark.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(dp(92), dp(92));
        markLp.bottomMargin = dp(10);
        mark.setBackground(round(Color.rgb(240,253,250), 46, 0, Color.TRANSPARENT));
        loginPanel.addView(mark, markLp);

        TextView appName = new TextView(this);
        appName.setText("المنجز السريع");
        appName.setTextSize(28);
        appName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        appName.setTextColor(Color.rgb(15,23,42));
        appName.setGravity(Gravity.CENTER);
        loginPanel.addView(appName, new LinearLayout.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("تسجيل الدخول");
        title.setTextSize(17);
        title.setTextColor(Color.rgb(71,85,105));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(8);
        titleLp.bottomMargin = dp(30);
        loginPanel.addView(title, titleLp);

        username = field("اسم المستخدم");
        username.setInputType(InputType.TYPE_CLASS_TEXT);
        loginPanel.addView(username, new LinearLayout.LayoutParams(-1, dp(56)));

        password = field("كلمة المرور");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams passLp = new LinearLayout.LayoutParams(-1, dp(56));
        passLp.topMargin = dp(12);
        loginPanel.addView(password, passLp);

        remember = new CheckBox(this);
        remember.setText("تذكرني");
        remember.setChecked(true);
        remember.setTextSize(15);
        remember.setTextColor(Color.rgb(51,65,85));
        LinearLayout.LayoutParams rememberLp = new LinearLayout.LayoutParams(-1, dp(48));
        rememberLp.topMargin = dp(4);
        loginPanel.addView(remember, rememberLp);

        errorText = new TextView(this);
        errorText.setTextColor(Color.rgb(185,28,28));
        errorText.setTextSize(14);
        errorText.setGravity(Gravity.CENTER);
        errorText.setVisibility(View.GONE);
        loginPanel.addView(errorText, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout buttonWrap = new FrameLayout(this);
        LinearLayout.LayoutParams bwLp = new LinearLayout.LayoutParams(-1, dp(54));
        bwLp.topMargin = dp(12);
        loginPanel.addView(buttonWrap, bwLp);

        loginButton = new Button(this);
        loginButton.setText("تسجيل الدخول");
        loginButton.setAllCaps(false);
        loginButton.setTextSize(17);
        loginButton.setTextColor(Color.WHITE);
        loginButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        loginButton.setBackground(round(Color.rgb(15,118,110), 14, 0, Color.TRANSPARENT));
        buttonWrap.addView(loginButton, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        buttonWrap.addView(progress, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));

        TextView note = new TextView(this);
        note.setText("تسجيل الدخول من Android • بقية النظام WebView");
        note.setTextSize(12);
        note.setTextColor(Color.rgb(148,163,184));
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.topMargin = dp(18);
        loginPanel.addView(note, noteLp);

        View spacer2 = new View(this);
        loginPanel.addView(spacer2, new LinearLayout.LayoutParams(1, 0, 1f));

        loginButton.setOnClickListener(v -> attemptLogin());
        password.setOnEditorActionListener((v, actionId, event) -> { attemptLogin(); return true; });
        setContentView(root);
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(16);
        e.setTextColor(Color.rgb(15,23,42));
        e.setHintTextColor(Color.rgb(148,163,184));
        e.setPadding(dp(16),0,dp(16),0);
        e.setBackground(round(Color.WHITE, 12, 1, Color.rgb(203,213,225)));
        return e;
    }

    private GradientDrawable round(int fill, int radiusDp, int strokeDp, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), stroke);
        return d;
    }

    @SuppressLint({"SetJavaScriptEnabled","JavascriptInterface"})
    private void configureWebView() {
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " CsahatiAndroid/1.0");

        webView.addJavascriptInterface(new Bridge(), "AndroidNative");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme();
                if (scheme.equals("http") || scheme.equals("https")) {
                    String host = uri.getHost();
                    if (host != null && (host.equalsIgnoreCase(HOST) || host.endsWith("." + HOST))) return false;
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
                    return true;
                }
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
                return true;
            }

            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                installWatcher();
                if (loginPending) injectCredentials();
                else checkAuth();
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) -> {
            try {
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                r.setMimeType(mimeType);
                r.addRequestHeader("User-Agent", userAgent);
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) r.addRequestHeader("Cookie", cookie);
                String name = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType);
                r.setTitle(name);
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
                Toast.makeText(this, "بدأ تنزيل الملف", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "تعذر تنزيل الملف", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void attemptLogin() {
        String u = username.getText().toString().trim();
        String p = password.getText().toString();
        if (u.isEmpty()) { showError("اكتب اسم المستخدم"); return; }
        if (p.isEmpty()) { showError("اكتب كلمة المرور"); return; }
        hideError();
        setLoading(true);
        pendingUser = u;
        pendingPass = p;
        loginPending = true;
        injectCredentials();
    }

    private void injectCredentials() {
        if (!loginPending) return;
        String u = JSONObject.quote(pendingUser);
        String p = JSONObject.quote(pendingPass);
        String rem = remember.isChecked() ? "true" : "false";
        String js = "(function(){" +
                "function vis(e){if(!e)return false;var s=getComputedStyle(e);return s.display!=='none'&&s.visibility!=='hidden';}" +
                "function setv(e,v){if(!e)return;var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d.set.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}" +
                "var pwd=[].slice.call(document.querySelectorAll('input[type=password]')).find(vis);" +
                "if(!pwd){AndroidNative.authenticated();return;}" +
                "var form=pwd.closest('form');" +
                "var all=[].slice.call(document.querySelectorAll('input[type=text],input[type=email],input:not([type])')).filter(vis);" +
                "var usr=all.find(function(x){var n=((x.name||'')+' '+(x.id||'')+' '+(x.autocomplete||'')).toLowerCase();return /user|email|login|account/.test(n);})||all[0];" +
                "if(!usr){AndroidNative.loginError('لم أجد حقل اسم المستخدم');return;}" +
                "setv(usr," + u + ");setv(pwd," + p + ");" +
                "var cb=document.querySelector('input[type=checkbox]');if(cb&&cb.checked!==" + rem + ")cb.click();" +
                "setTimeout(function(){var b=(form&&form.querySelector('button[type=submit],input[type=submit],button:not([type])'))||document.querySelector('button[type=submit],input[type=submit]');if(form&&form.requestSubmit){try{form.requestSubmit(b||undefined);return;}catch(e){}}if(b)b.click();else if(form)form.submit();else AndroidNative.loginError('لم أجد زر تسجيل الدخول');},150);" +
                "})();";
        webView.evaluateJavascript(js, null);
        pendingPass = "";
    }

    private void installWatcher() {
        String js = "(function(){if(window.__androidAuthWatch)return;window.__androidAuthWatch=true;function vis(e){if(!e)return false;var s=getComputedStyle(e);return s.display!=='none'&&s.visibility!=='hidden';}function c(){var p=[].slice.call(document.querySelectorAll('input[type=password]')).find(vis);if(!p){AndroidNative.authenticated();}else if(window.__androidWasAuthed){AndroidNative.loggedOut();}}window.__androidCheck=c;new MutationObserver(function(){setTimeout(c,100);}).observe(document.documentElement,{childList:true,subtree:true,attributes:true});setInterval(c,1000);c();})();";
        webView.evaluateJavascript(js, null);
    }

    private void checkAuth() {
        webView.evaluateJavascript("(function(){var p=[].slice.call(document.querySelectorAll('input[type=password]')).find(function(e){var s=getComputedStyle(e);return s.display!=='none'&&s.visibility!=='hidden';});return p?'login':'app';})();", value -> {
            if ("\"app\"".equals(value)) onAuthenticated();
        });
    }

    private void onAuthenticated() {
        if (authenticated) return;
        authenticated = true;
        loginPending = false;
        pendingPass = "";
        CookieManager.getInstance().flush();
        runOnUiThread(() -> {
            setLoading(false);
            hideError();
            loginPanel.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            webView.evaluateJavascript("window.__androidWasAuthed=true;", null);
        });
    }

    private void showLoginAgain() {
        authenticated = false;
        loginPending = false;
        runOnUiThread(() -> {
            webView.setVisibility(View.INVISIBLE);
            loginPanel.setVisibility(View.VISIBLE);
            password.setText("");
            setLoading(false);
        });
    }

    private void setLoading(boolean on) {
        loginButton.setEnabled(!on);
        username.setEnabled(!on);
        password.setEnabled(!on);
        remember.setEnabled(!on);
        loginButton.setText(on ? "" : "تسجيل الدخول");
        progress.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private void showError(String msg) {
        setLoading(false);
        errorText.setText(msg);
        errorText.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errorText.setText("");
        errorText.setVisibility(View.GONE);
    }

    private void startVersionWatcher() {
        handler.post(new Runnable() {
            @Override public void run() {
                if (authenticated) checkVersion();
                handler.postDelayed(this, VERSION_CHECK_MS);
            }
        });
    }

    private void checkVersion() {
        io.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection)new URL(VERSION_URL + "?_=" + System.currentTimeMillis()).openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(4000);
                c.setUseCaches(false);
                c.setRequestProperty("Cache-Control", "no-cache, no-store");
                if (c.getResponseCode() < 200 || c.getResponseCode() >= 300) return;
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                String v = new JSONObject(sb.toString()).optString("web_version", "").trim();
                if (v.isEmpty()) return;
                runOnUiThread(() -> {
                    if (lastVersion == null) lastVersion = v;
                    else if (!lastVersion.equals(v)) {
                        lastVersion = v;
                        webView.stopLoading();
                        webView.clearCache(true);
                        String url = webView.getUrl();
                        if (url == null || url.isEmpty()) url = WEB_URL;
                        webView.loadUrl(url + (url.contains("?") ? "&" : "?") + "_wv=" + v + "&_t=" + System.currentTimeMillis());
                    }
                });
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    private class Bridge {
        @JavascriptInterface public void authenticated() { runOnUiThread(MainActivity.this::onAuthenticated); }
        @JavascriptInterface public void loggedOut() { runOnUiThread(MainActivity.this::showLoginAgain); }
        @JavascriptInterface public void loginError(String m) { runOnUiThread(() -> showError(m)); }
        @JavascriptInterface public void share(String text, String title) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, title == null ? "مشاركة" : title));
            });
        }
    }

    @Override public void onBackPressed() {
        if (authenticated && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        io.shutdownNow();
        webView.removeJavascriptInterface("AndroidNative");
        webView.destroy();
        super.onDestroy();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
