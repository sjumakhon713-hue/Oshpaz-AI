package tj.oshpaz.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final int PICK = 1001;
    private WebView web;
    private ValueCallback<Uri[]> pending;
    private Uri cameraUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(0xFFF4EEE6);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setTextZoom(100);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pending != null) pending.onReceiveValue(null);
                pending = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("image/*");
                Intent chooser = Intent.createChooser(pick, "Акс");
                try {
                    File f = new File(getCacheDir(), "photo_" + System.currentTimeMillis() + ".jpg");
                    cameraUri = FileProvider.getUriForFile(MainActivity.this, "tj.oshpaz.app.files", f);
                    Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                    cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
                } catch (Exception e) {
                    cameraUri = null;
                }
                try {
                    startActivityForResult(chooser, PICK);
                } catch (ActivityNotFoundException e) {
                    pending.onReceiveValue(null);
                    pending = null;
                    return false;
                }
                return true;
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return openOutside(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return openOutside(Uri.parse(url));
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl("file:///android_asset/www/index.html");
        }
    }

    private boolean openOutside(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        try {
            if (scheme.equals("tel")) {
                startActivity(new Intent(Intent.ACTION_DIAL, uri));
                return true;
            }
            if (scheme.equals("http") || scheme.equals("https") || scheme.equals("mailto")) {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
                return true;
            }
        } catch (ActivityNotFoundException ignored) {
            return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK || pending == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            } else if (cameraUri != null) {
                result = new Uri[]{cameraUri};
            }
        }
        pending.onReceiveValue(result);
        pending = null;
    }

    /** Saves a pattern file and opens the share sheet (Telegram, Files, print…). */
    class Bridge {
        @JavascriptInterface
        public void share(String name, String content) {
            try {
                File f = new File(getCacheDir(), name);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(content.getBytes(StandardCharsets.UTF_8));
                }
                Uri uri = FileProvider.getUriForFile(MainActivity.this, "tj.oshpaz.app.files", f);
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("image/svg+xml");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(send, "Қолибро фиристодан"));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Захира нашуд", Toast.LENGTH_SHORT).show());
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript(
            "(function(){var o=document.getElementById('ov');" +
            "if(o&&o.classList.contains('show')){o.classList.remove('show');return 'sheet';}" +
            "if(typeof view!=='undefined'&&view==='cook'){stopCook();return 'cook';}" +
            "if(typeof view!=='undefined'&&view){view=null;render();return 'view';}" +
            "if(typeof tab!=='undefined'&&tab!=='home'){tab='home';render();return 'tab';}" +
            "return 'exit';})()",
            value -> {
                if ("\"exit\"".equals(value)) {
                    MainActivity.super.onBackPressed();
                }
            });
    }
}
