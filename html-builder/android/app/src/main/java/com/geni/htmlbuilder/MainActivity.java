package com.geni.htmlbuilder;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_EXPORT = 2301;

    private WebView webView;
    private String pendingHtml = "";
    private String pendingName = "pagina.html";
    private boolean pendingOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(4, 10, 19));
        getWindow().setNavigationBarColor(Color.rgb(4, 10, 19));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(7, 17, 31));
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new NativeBridge(), "CoderBuilderNative");
        webView.loadUrl("file:///android_asset/coderbuilder.html");
        setContentView(webView);
    }

    private String safeName(String raw) {
        String value = raw == null ? "pagina.html" : raw.trim();
        if (value.isEmpty()) value = "pagina.html";
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".html") && !lower.endsWith(".htm")) value += ".html";
        return value.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1F]", "_");
    }

    private void requestExport(String name, String html, boolean openAfter) {
        pendingName = safeName(name);
        pendingHtml = html == null ? "" : html;
        pendingOpen = openAfter;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/html");
        intent.putExtra(Intent.EXTRA_TITLE, pendingName);
        startActivityForResult(intent, REQ_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_EXPORT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new Exception("Não foi possível criar o arquivo.");
            out.write(pendingHtml.getBytes(StandardCharsets.UTF_8));
            out.flush();
            Toast.makeText(this, "HTML exportado com sucesso.", Toast.LENGTH_SHORT).show();
            if (webView != null) {
                webView.evaluateJavascript("window.CoderBuilderApp && window.CoderBuilderApp.onNativeExportSuccess && window.CoderBuilderApp.onNativeExportSuccess()", null);
            }
            if (pendingOpen) openInBrowser(uri);
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao exportar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openInBrowser(Uri uri) {
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(uri, "text/html");
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(view, "Abrir HTML com"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Arquivo salvo. Abra-o pelo app Arquivos usando seu navegador.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        webView.evaluateJavascript("window.CoderBuilderApp && window.CoderBuilderApp.handleBack ? window.CoderBuilderApp.handleBack() : false", value -> {
            if (!"true".equals(value)) super.onBackPressed();
        });
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    public class NativeBridge {
        @JavascriptInterface
        public void exportHtml(String name, String html, boolean openAfter) {
            runOnUiThread(() -> requestExport(name, html, openAfter));
        }

        @JavascriptInterface
        public String platform() {
            return "android";
        }

        @JavascriptInterface
        public String version() {
            return "2.3.0-alpha.1";
        }
    }
}
