package com.geni.htmlbuilder;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQ_SAVE = 1001;
    private static final int REQ_SAVE_OPEN = 1002;

    private static final int BG = Color.rgb(7, 17, 31);
    private static final int ELEV = Color.rgb(11, 23, 40);
    private static final int CARD = Color.rgb(14, 29, 49);
    private static final int CARD_HI = Color.rgb(19, 40, 68);
    private static final int BORDER = Color.rgb(35, 70, 111);
    private static final int BLUE = Color.rgb(20, 121, 255);
    private static final int BRIGHT = Color.rgb(35, 169, 255);
    private static final int CYAN = Color.rgb(25, 213, 210);
    private static final int TEXT = Color.rgb(244, 248, 255);
    private static final int MUTED = Color.rgb(168, 183, 206);
    private static final int SUCCESS = Color.rgb(55, 213, 138);

    private EditText fileName;
    private EditText code;
    private WebView preview;
    private TextView status;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, int strokeColor, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeColor != Color.TRANSPARENT) d.setStroke(dp(1), strokeColor);
        return d;
    }

    private TextView text(String value, float sizeSp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(TEXT);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackground(rounded(primary ? BLUE : ELEV, primary ? BRIGHT : BORDER, 12));
        b.setStateListAnimator(null);
        return b;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14), dp(14), dp(14), dp(14));
        l.setBackground(rounded(CARD, BORDER, 16));
        l.setElevation(dp(2));
        return l;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(4, 10, 19));
        getWindow().setNavigationBarColor(Color.rgb(4, 10, 19));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        root.addView(buildHeader(), new LinearLayout.LayoutParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(8), dp(14), dp(18));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        content.addView(buildFileCard(), marginBottom(12));
        content.addView(buildEditorCard(), marginBottom(12));
        content.addView(buildActions(), marginBottom(12));
        content.addView(buildPreviewCard(), marginBottom(12));

        TextView version = text("CoderBuilder 2.0.0-alpha.1  •  Cole. Crie. Execute.", 11, false, MUTED);
        version.setGravity(Gravity.CENTER);
        content.addView(version, new LinearLayout.LayoutParams(-1, dp(34)));

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        updatePreview();
    }

    private LinearLayout.LayoutParams marginBottom(int dpValue) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(dpValue);
        return p;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(10), dp(16), dp(10));
        header.setBackgroundColor(Color.rgb(5, 12, 23));

        TextView logo = text("</>", 17, true, Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable logoBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{BRIGHT, BLUE, CYAN}
        );
        logoBg.setCornerRadius(dp(13));
        logo.setBackground(logoBg);
        header.addView(logo, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout titleWrap = new LinearLayout(this);
        titleWrap.setOrientation(LinearLayout.VERTICAL);
        titleWrap.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tw = new LinearLayout.LayoutParams(0, -1, 1f);
        tw.leftMargin = dp(12);

        TextView title = text("CoderBuilder", 22, true, TEXT);
        TextView subtitle = text("Cole. Crie. Execute.", 11, false, BRIGHT);
        titleWrap.addView(title);
        titleWrap.addView(subtitle);
        header.addView(titleWrap, tw);

        TextView badge = text("HTML", 11, true, CYAN);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(ELEV, BLUE, 10));
        header.addView(badge, new LinearLayout.LayoutParams(dp(58), dp(36)));
        return header;
    }

    private View buildFileCard() {
        LinearLayout box = card();
        TextView section = text("▣  ARQUIVO", 12, true, BRIGHT);
        box.addView(section, marginBottom(10));

        fileName = new EditText(this);
        fileName.setSingleLine(true);
        fileName.setText("meu_site.html");
        fileName.setHint("pagina.html");
        fileName.setTextColor(TEXT);
        fileName.setHintTextColor(MUTED);
        fileName.setTextSize(15);
        fileName.setPadding(dp(13), 0, dp(13), 0);
        fileName.setBackground(rounded(ELEV, BORDER, 11));
        box.addView(fileName, new LinearLayout.LayoutParams(-1, dp(48)));
        return box;
    }

    private View buildEditorCard() {
        LinearLayout box = card();

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView section = text("</>  EDITOR", 12, true, CYAN);
        status = text("● Pronto", 11, false, SUCCESS);
        head.addView(section, new LinearLayout.LayoutParams(0, dp(32), 1f));
        head.addView(status, new LinearLayout.LayoutParams(-2, dp(32)));
        box.addView(head, marginBottom(8));

        code = new EditText(this);
        code.setGravity(Gravity.TOP | Gravity.START);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextSize(12.5f);
        code.setTextColor(Color.rgb(219, 232, 248));
        code.setHintTextColor(MUTED);
        code.setPadding(dp(14), dp(14), dp(14), dp(14));
        code.setHorizontallyScrolling(true);
        code.setHorizontalScrollBarEnabled(true);
        code.setVerticalScrollBarEnabled(true);
        code.setBackground(rounded(Color.rgb(6, 16, 29), BORDER, 12));
        code.setText(defaultHtml());
        box.addView(code, new LinearLayout.LayoutParams(-1, dp(360)));
        return box;
    }

    private View buildActions() {
        LinearLayout box = card();
        TextView section = text("⚡  AÇÕES", 12, true, BRIGHT);
        box.addView(section, marginBottom(5));

        Button previewBtn = button("◉  Visualizar", true);
        Button saveBtn = button("▣  Salvar HTML", false);
        Button saveOpenBtn = button("↗  Salvar e abrir", false);
        Button clearBtn = button("＋  Novo arquivo", false);

        box.addView(previewBtn, actionParams());
        box.addView(saveBtn, actionParams());
        box.addView(saveOpenBtn, actionParams());
        box.addView(clearBtn, actionParams());

        previewBtn.setOnClickListener(v -> updatePreview());
        saveBtn.setOnClickListener(v -> chooseDestination(REQ_SAVE));
        saveOpenBtn.setOnClickListener(v -> chooseDestination(REQ_SAVE_OPEN));
        clearBtn.setOnClickListener(v -> {
            fileName.setText("novo_arquivo.html");
            code.setText("<!doctype html>\n<html lang=\"pt-BR\">\n<head>\n  <meta charset=\"utf-8\">\n  <title>Novo arquivo</title>\n</head>\n<body>\n\n</body>\n</html>");
            updatePreview();
        });
        return box;
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
        p.topMargin = dp(8);
        return p;
    }

    private View buildPreviewCard() {
        LinearLayout box = card();
        TextView section = text("◉  PRÉ-VISUALIZAÇÃO", 12, true, CYAN);
        box.addView(section, marginBottom(8));

        preview = new WebView(this);
        preview.setBackgroundColor(Color.WHITE);
        preview.setWebViewClient(new WebViewClient());
        preview.getSettings().setJavaScriptEnabled(true);
        preview.getSettings().setDomStorageEnabled(true);
        preview.getSettings().setAllowFileAccess(false);
        preview.setBackground(rounded(Color.WHITE, BORDER, 12));
        box.addView(preview, new LinearLayout.LayoutParams(-1, dp(300)));
        return box;
    }

    private String defaultHtml() {
        return "<!doctype html>\n" +
                "<html lang=\"pt-BR\">\n" +
                "<head>\n" +
                "  <meta charset=\"utf-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n" +
                "  <title>Meu Site</title>\n" +
                "  <style>\n" +
                "    body{margin:0;font-family:Arial,sans-serif;background:linear-gradient(135deg,#0d1117,#1a1f2e);color:white;min-height:100vh;display:grid;place-items:center}\n" +
                "    .box{text-align:center;padding:36px}\n" +
                "    h1{font-size:42px;margin:0 0 12px}\n" +
                "    p{color:#c8d2e1}\n" +
                "    button{padding:12px 20px;border:0;border-radius:10px;background:#1479ff;color:white;font-weight:bold}\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class=\"box\">\n" +
                "    <h1>Meu Site</h1>\n" +
                "    <p>Bem-vindo ao meu site criado com CoderBuilder!</p>\n" +
                "    <button>Vamos codar! 🚀</button>\n" +
                "  </div>\n" +
                "</body>\n" +
                "</html>";
    }

    private void updatePreview() {
        if (preview == null || code == null) return;
        preview.loadDataWithBaseURL(null, code.getText().toString(), "text/html", "UTF-8", null);
        if (status != null) status.setText("● Prévia atualizada");
    }

    private String normalizedName() {
        String name = fileName.getText().toString().trim();
        if (name.isEmpty()) name = "pagina.html";
        if (!name.toLowerCase().endsWith(".html") && !name.toLowerCase().endsWith(".htm")) name += ".html";
        return name;
    }

    private void chooseDestination(int requestCode) {
        if (code.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Cole um código HTML primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/html");
        intent.putExtra(Intent.EXTRA_TITLE, normalizedName());
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode != REQ_SAVE && requestCode != REQ_SAVE_OPEN) || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new Exception("Não foi possível criar o arquivo.");
            out.write(code.getText().toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            Toast.makeText(this, "HTML salvo com sucesso.", Toast.LENGTH_SHORT).show();
            if (status != null) status.setText("● HTML salvo");
            if (requestCode == REQ_SAVE_OPEN) openInBrowser(uri);
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao salvar: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
    protected void onDestroy() {
        if (preview != null) {
            preview.loadUrl("about:blank");
            preview.destroy();
        }
        super.onDestroy();
    }
}
