package com.geni.htmlbuilder;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int REQ_SAVE = 1001;
    private static final int REQ_SAVE_OPEN = 1002;
    private static final String PREFS = "coderbuilder_v22";
    private static final String KEY_PROJECTS = "projects";
    private static final String KEY_LISTS = "lists";
    private static final String KEY_EXPORTS = "exports";

    private static final int BG = Color.rgb(7, 17, 31);
    private static final int ELEV = Color.rgb(11, 23, 40);
    private static final int CARD = Color.rgb(14, 29, 49);
    private static final int BORDER = Color.rgb(35, 70, 111);
    private static final int BLUE = Color.rgb(20, 121, 255);
    private static final int BRIGHT = Color.rgb(35, 169, 255);
    private static final int CYAN = Color.rgb(25, 213, 210);
    private static final int TEXT = Color.rgb(244, 248, 255);
    private static final int MUTED = Color.rgb(168, 183, 206);
    private static final int SUCCESS = Color.rgb(55, 213, 138);
    private static final int DANGER = Color.rgb(255, 97, 120);

    private SharedPreferences prefs;
    private EditText fileName;
    private EditText code;
    private WebView preview;
    private LinearLayout previewFrame;
    private TextView status;
    private TextView autosaveLabel;
    private TextView listPicker;
    private TextView previewModeLabel;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable autosaveRunnable;
    private Runnable previewRunnable;
    private boolean suppressChanges = false;
    private String currentProjectId;
    private String currentListId = "geral";
    private boolean autoSave = true;
    private boolean autoPreview = true;
    private boolean wrapLines = false;
    private boolean confirmDelete = true;
    private boolean autoVersions = true;
    private int fontSize = 13;
    private int previewZoom = 100;
    private String previewDevice = "auto";

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
        b.setPadding(dp(10), 0, dp(10), 0);
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

    private LinearLayout.LayoutParams bottom(int value) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(value);
        return p;
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
        p.topMargin = dp(8);
        return p;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadSettings();
        ensureDefaultLists();
        getWindow().setStatusBarColor(Color.rgb(4, 10, 19));
        getWindow().setNavigationBarColor(Color.rgb(4, 10, 19));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.addView(buildHeader(), new LinearLayout.LayoutParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(8), dp(14), dp(18));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        content.addView(buildProjectCard(), bottom(12));
        content.addView(buildEditorCard(), bottom(12));
        content.addView(buildActionsCard(), bottom(12));
        content.addView(buildPreviewCard(), bottom(12));

        TextView version = text("CoderBuilder 2.2.0-alpha.1  •  Cole. Crie. Execute.", 11, false, MUTED);
        version.setGravity(Gravity.CENTER);
        content.addView(version, new LinearLayout.LayoutParams(-1, dp(36)));

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        attachWatchers();
        restoreLastProject();
        updatePreview();
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(10), dp(12), dp(10));
        header.setBackgroundColor(Color.rgb(5, 12, 23));

        TextView logo = text("</>", 17, true, Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable logoBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{BRIGHT, BLUE, CYAN});
        logoBg.setCornerRadius(dp(13));
        logo.setBackground(logoBg);
        header.addView(logo, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout titleWrap = new LinearLayout(this);
        titleWrap.setOrientation(LinearLayout.VERTICAL);
        titleWrap.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -1, 1f);
        tp.leftMargin = dp(12);
        titleWrap.addView(text("CoderBuilder", 22, true, TEXT));
        titleWrap.addView(text("Cole. Crie. Execute.  •  2.2 alpha", 11, false, BRIGHT));
        header.addView(titleWrap, tp);

        Button more = button("⋮", false);
        more.setTextSize(23);
        more.setOnClickListener(this::showMainMenu);
        header.addView(more, new LinearLayout.LayoutParams(dp(48), dp(44)));
        return header;
    }

    private View buildProjectCard() {
        LinearLayout box = card();
        box.addView(text("▣  PROJETO ATUAL", 12, true, BRIGHT), bottom(9));

        fileName = new EditText(this);
        fileName.setSingleLine(true);
        fileName.setText("meu_site.html");
        fileName.setTextColor(TEXT);
        fileName.setHintTextColor(MUTED);
        fileName.setTextSize(15);
        fileName.setPadding(dp(13), 0, dp(13), 0);
        fileName.setBackground(rounded(ELEV, BORDER, 11));
        box.addView(fileName, bottom(10));

        TextView listLabel = text("Lista", 11, false, MUTED);
        box.addView(listLabel, bottom(5));
        listPicker = text("Geral  ▾", 14, true, TEXT);
        listPicker.setGravity(Gravity.CENTER_VERTICAL);
        listPicker.setPadding(dp(13), 0, dp(13), 0);
        listPicker.setBackground(rounded(ELEV, BORDER, 11));
        listPicker.setOnClickListener(v -> chooseCurrentList());
        box.addView(listPicker, new LinearLayout.LayoutParams(-1, dp(46)));

        autosaveLabel = text(autoSave ? "● Salvamento automático ativo" : "○ Salvamento automático desligado", 11, false,
                autoSave ? SUCCESS : MUTED);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
        ap.topMargin = dp(10);
        box.addView(autosaveLabel, ap);
        return box;
    }

    private View buildEditorCard() {
        LinearLayout box = card();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("</>  EDITOR", 12, true, CYAN), new LinearLayout.LayoutParams(0, dp(32), 1f));
        status = text("● Pronto", 11, false, SUCCESS);
        head.addView(status, new LinearLayout.LayoutParams(-2, dp(32)));
        box.addView(head, bottom(8));

        code = new EditText(this);
        code.setGravity(Gravity.TOP | Gravity.START);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextSize(fontSize);
        code.setTextColor(Color.rgb(219, 232, 248));
        code.setHintTextColor(MUTED);
        code.setPadding(dp(14), dp(14), dp(14), dp(14));
        code.setHorizontalScrollBarEnabled(true);
        code.setVerticalScrollBarEnabled(true);
        code.setHorizontallyScrolling(!wrapLines);
        code.setBackground(rounded(Color.rgb(6, 16, 29), BORDER, 12));
        code.setText(defaultHtml());
        box.addView(code, new LinearLayout.LayoutParams(-1, dp(390)));
        return box;
    }

    private View buildActionsCard() {
        LinearLayout box = card();
        box.addView(text("⚡  AÇÕES", 12, true, BRIGHT), bottom(4));
        Button previewBtn = button("◉  Atualizar prévia", true);
        Button saveProjectBtn = button("✓  Salvar projeto", false);
        Button exportBtn = button("↓  Exportar HTML", false);
        Button browserBtn = button("↗  Salvar e abrir no navegador", false);
        Button newBtn = button("＋  Novo projeto", false);
        box.addView(previewBtn, actionParams());
        box.addView(saveProjectBtn, actionParams());
        box.addView(exportBtn, actionParams());
        box.addView(browserBtn, actionParams());
        box.addView(newBtn, actionParams());
        previewBtn.setOnClickListener(v -> updatePreview());
        saveProjectBtn.setOnClickListener(v -> saveCurrentProject(true));
        exportBtn.setOnClickListener(v -> chooseDestination(REQ_SAVE));
        browserBtn.setOnClickListener(v -> chooseDestination(REQ_SAVE_OPEN));
        newBtn.setOnClickListener(v -> newProject());
        return box;
    }

    private View buildPreviewCard() {
        LinearLayout box = card();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("◉  PRÉ-VISUALIZAÇÃO", 12, true, CYAN), new LinearLayout.LayoutParams(0, dp(42), 1f));
        previewModeLabel = text("Auto · 100%", 10, false, BRIGHT);
        previewModeLabel.setGravity(Gravity.CENTER);
        head.addView(previewModeLabel, new LinearLayout.LayoutParams(dp(92), dp(36)));
        Button tools = button("☰", false);
        tools.setTextSize(19);
        tools.setOnClickListener(this::showPreviewMenu);
        head.addView(tools, new LinearLayout.LayoutParams(dp(48), dp(40)));
        box.addView(head, bottom(8));

        previewFrame = new LinearLayout(this);
        previewFrame.setGravity(Gravity.CENTER);
        previewFrame.setBackground(rounded(Color.rgb(5, 13, 23), BORDER, 12));
        previewFrame.setPadding(dp(8), dp(8), dp(8), dp(8));

        preview = createWebView();
        previewFrame.addView(preview, new LinearLayout.LayoutParams(-1, dp(320)));
        box.addView(previewFrame, new LinearLayout.LayoutParams(-1, -2));
        applyPreviewDevice();
        return box;
    }

    private WebView createWebView() {
        WebView web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        web.setWebViewClient(new WebViewClient());
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        return web;
    }

    private void configureWebView(WebView web) {
        web.setInitialScale(previewZoom);
    }

    private void showPreviewMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("− Diminuir zoom");
        menu.getMenu().add("100% zoom");
        menu.getMenu().add("＋ Aumentar zoom");
        menu.getMenu().add("↻ Atualizar");
        menu.getMenu().add("▯ Em pé");
        menu.getMenu().add("▭ Deitado");
        menu.getMenu().add("▣ Automático");
        menu.getMenu().add("⛶ Tela cheia");
        menu.getMenu().add("↗ Salvar e abrir no navegador");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.startsWith("−")) setPreviewZoom(previewZoom - 10);
            else if (title.startsWith("100")) setPreviewZoom(100);
            else if (title.startsWith("＋")) setPreviewZoom(previewZoom + 10);
            else if (title.startsWith("↻")) updatePreview();
            else if (title.contains("Em pé")) setPreviewDevice("portrait");
            else if (title.contains("Deitado")) setPreviewDevice("landscape");
            else if (title.contains("Automático")) setPreviewDevice("auto");
            else if (title.contains("Tela cheia")) showFullscreenPreview();
            else if (title.contains("navegador")) chooseDestination(REQ_SAVE_OPEN);
            return true;
        });
        menu.show();
    }

    private void setPreviewZoom(int zoom) {
        previewZoom = Math.max(50, Math.min(200, zoom));
        configureWebView(preview);
        updatePreviewLabel();
        updatePreview();
    }

    private void setPreviewDevice(String mode) {
        previewDevice = mode;
        applyPreviewDevice();
        updatePreviewLabel();
    }

    private void applyPreviewDevice() {
        if (preview == null || previewFrame == null) return;
        int available = getResources().getDisplayMetrics().widthPixels - dp(56);
        LinearLayout.LayoutParams p;
        if ("portrait".equals(previewDevice)) {
            p = new LinearLayout.LayoutParams(Math.min(dp(320), available), dp(520));
        } else if ("landscape".equals(previewDevice)) {
            p = new LinearLayout.LayoutParams(-1, dp(240));
        } else {
            p = new LinearLayout.LayoutParams(-1, dp(320));
        }
        p.gravity = Gravity.CENTER;
        preview.setLayoutParams(p);
    }

    private void updatePreviewLabel() {
        if (previewModeLabel == null) return;
        String mode = "auto".equals(previewDevice) ? "Auto" : ("portrait".equals(previewDevice) ? "Em pé" : "Deitado");
        previewModeLabel.setText(mode + " · " + previewZoom + "%");
    }

    private void showFullscreenPreview() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(8), dp(10), dp(8));
        bar.setBackgroundColor(Color.rgb(5, 12, 23));
        Button back = button("← Editor", false);
        TextView title = text("Prévia · " + previewZoom + "%", 13, true, TEXT);
        Button tools = button("☰", false);
        bar.addView(back, new LinearLayout.LayoutParams(dp(105), dp(42)));
        LinearLayout.LayoutParams tt = new LinearLayout.LayoutParams(0, dp(42), 1f);
        tt.leftMargin = dp(10);
        title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(title, tt);
        bar.addView(tools, new LinearLayout.LayoutParams(dp(52), dp(42)));
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout stage = new LinearLayout(this);
        stage.setGravity(Gravity.CENTER);
        stage.setBackgroundColor(Color.rgb(4, 10, 18));
        stage.setPadding(dp(8), dp(8), dp(8), dp(8));
        WebView full = createWebView();
        configureWebView(full);
        stage.addView(full, fullscreenPreviewParams());
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));
        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        full.loadDataWithBaseURL("https://coderbuilder.local/", code.getText().toString(), "text/html", "UTF-8", null);

        back.setOnClickListener(v -> dialog.dismiss());
        tools.setOnClickListener(v -> showFullscreenTools(v, full, stage, title));
        dialog.setOnDismissListener(d -> {
            full.loadUrl("about:blank");
            full.destroy();
        });
        dialog.show();
    }

    private LinearLayout.LayoutParams fullscreenPreviewParams() {
        int width = getResources().getDisplayMetrics().widthPixels - dp(24);
        int height = getResources().getDisplayMetrics().heightPixels - dp(100);
        LinearLayout.LayoutParams p;
        if ("portrait".equals(previewDevice)) {
            p = new LinearLayout.LayoutParams(Math.min(dp(390), width), Math.min(dp(720), height));
        } else if ("landscape".equals(previewDevice)) {
            p = new LinearLayout.LayoutParams(Math.min(dp(760), width), Math.min(dp(390), height));
        } else {
            p = new LinearLayout.LayoutParams(-1, -1);
        }
        p.gravity = Gravity.CENTER;
        return p;
    }

    private void showFullscreenTools(View anchor, WebView full, LinearLayout stage, TextView title) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("− Zoom");
        menu.getMenu().add("100% Zoom");
        menu.getMenu().add("＋ Zoom");
        menu.getMenu().add("▯ Em pé");
        menu.getMenu().add("▭ Deitado");
        menu.getMenu().add("▣ Automático");
        menu.getMenu().add("↻ Atualizar");
        menu.setOnMenuItemClickListener(item -> {
            String s = item.getTitle().toString();
            if (s.startsWith("−")) previewZoom = Math.max(50, previewZoom - 10);
            else if (s.startsWith("100")) previewZoom = 100;
            else if (s.startsWith("＋")) previewZoom = Math.min(200, previewZoom + 10);
            else if (s.contains("Em pé")) previewDevice = "portrait";
            else if (s.contains("Deitado")) previewDevice = "landscape";
            else if (s.contains("Automático")) previewDevice = "auto";
            configureWebView(full);
            full.setLayoutParams(fullscreenPreviewParams());
            full.loadDataWithBaseURL("https://coderbuilder.local/", code.getText().toString(), "text/html", "UTF-8", null);
            title.setText("Prévia · " + previewZoom + "%");
            applyPreviewDevice();
            updatePreviewLabel();
            return true;
        });
        menu.show();
    }

    private void showMainMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("▣ Projetos e listas");
        menu.getMenu().add("↓ Baixados / Exportados");
        menu.getMenu().add("⚙ Configurações");
        menu.getMenu().add("＋ Novo projeto");
        menu.getMenu().add("ⓘ Sobre o CoderBuilder");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.contains("Projetos")) showProjectsDialog(null);
            else if (title.contains("Baixados")) showExportsDialog();
            else if (title.contains("Configurações")) showSettingsDialog();
            else if (title.contains("Novo")) newProject();
            else if (title.contains("Sobre")) showAbout();
            return true;
        });
        menu.show();
    }

    private void attachWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (suppressChanges) return;
                scheduleAutosave();
                schedulePreview();
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        code.addTextChangedListener(watcher);
        fileName.addTextChangedListener(watcher);
    }

    private void scheduleAutosave() {
        if (!autoSave) return;
        if (autosaveRunnable != null) handler.removeCallbacks(autosaveRunnable);
        autosaveLabel.setText("● Salvando rascunho…");
        autosaveRunnable = () -> saveCurrentProject(false);
        handler.postDelayed(autosaveRunnable, 700);
    }

    private void schedulePreview() {
        if (!autoPreview) return;
        if (previewRunnable != null) handler.removeCallbacks(previewRunnable);
        previewRunnable = this::updatePreview;
        handler.postDelayed(previewRunnable, 250);
    }

    private void updatePreview() {
        if (preview == null || code == null) return;
        configureWebView(preview);
        preview.loadDataWithBaseURL("https://coderbuilder.local/", code.getText().toString(), "text/html", "UTF-8", null);
        status.setText("● Prévia atualizada");
        updatePreviewLabel();
    }

    private void saveCurrentProject(boolean manual) {
        try {
            JSONArray projects = getArray(KEY_PROJECTS);
            JSONObject p = findProject(projects, currentProjectId);
            long now = System.currentTimeMillis();
            if (p == null) {
                p = new JSONObject();
                currentProjectId = UUID.randomUUID().toString();
                p.put("id", currentProjectId);
                p.put("versions", new JSONArray());
                projects.put(p);
                createVersion(p, "Projeto criado", true);
            }
            String oldHtml = p.optString("html", "");
            p.put("name", normalizedName());
            p.put("html", code.getText().toString());
            p.put("listId", currentListId);
            p.put("updatedAt", now);
            if (autoVersions && (!oldHtml.equals(code.getText().toString()) || manual)) {
                JSONArray versions = p.optJSONArray("versions");
                JSONObject latest = versions != null && versions.length() > 0 ? versions.optJSONObject(0) : null;
                long last = latest == null ? 0 : latest.optLong("ts", 0);
                if (manual || now - last > 300000) createVersion(p, manual ? "Salvamento manual" : "Versão automática", false);
            }
            saveArray(KEY_PROJECTS, projects);
            prefs.edit().putString("currentProjectId", currentProjectId).apply();
            autosaveLabel.setText(autoSave ? "● Rascunho salvo" : "○ Autosave desligado");
            autosaveLabel.setTextColor(autoSave ? SUCCESS : MUTED);
            if (manual) Toast.makeText(this, "Projeto salvo.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao salvar projeto: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void createVersion(JSONObject project, String reason, boolean force) {
        if (!autoVersions && !force) return;
        try {
            JSONArray versions = project.optJSONArray("versions");
            if (versions == null) versions = new JSONArray();
            String html = project.optString("html", code == null ? "" : code.getText().toString());
            if (!force && versions.length() > 0) {
                JSONObject latest = versions.optJSONObject(0);
                if (latest != null && html.equals(latest.optString("html"))) return;
            }
            JSONObject v = new JSONObject();
            v.put("id", UUID.randomUUID().toString());
            v.put("html", html);
            v.put("name", project.optString("name", normalizedName()));
            v.put("listId", project.optString("listId", currentListId));
            v.put("ts", System.currentTimeMillis());
            v.put("reason", reason);
            JSONArray next = new JSONArray();
            next.put(v);
            for (int i = 0; i < versions.length() && i < 19; i++) next.put(versions.get(i));
            project.put("versions", next);
        } catch (Exception ignored) {}
    }

    private void restoreLastProject() {
        currentProjectId = prefs.getString("currentProjectId", null);
        if (currentProjectId == null) return;
        JSONObject p = findProject(getArray(KEY_PROJECTS), currentProjectId);
        if (p != null) loadProject(p);
    }

    private void loadProject(JSONObject p) {
        suppressChanges = true;
        currentProjectId = p.optString("id", null);
        currentListId = p.optString("listId", "geral");
        fileName.setText(p.optString("name", "pagina.html"));
        code.setText(p.optString("html", defaultHtml()));
        updateListPicker();
        suppressChanges = false;
        prefs.edit().putString("currentProjectId", currentProjectId).apply();
        updatePreview();
    }

    private void newProject() {
        if (autoSave && code != null && code.getText().length() > 0) saveCurrentProject(false);
        suppressChanges = true;
        currentProjectId = null;
        currentListId = "geral";
        fileName.setText("novo_projeto.html");
        code.setText(emptyHtml());
        updateListPicker();
        suppressChanges = false;
        prefs.edit().remove("currentProjectId").apply();
        updatePreview();
        Toast.makeText(this, "Novo projeto criado.", Toast.LENGTH_SHORT).show();
    }

    private void showProjectsDialog(String filterListId) {
        Dialog dialog = new Dialog(this);
        dialog.setTitle("Projetos");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Projetos e listas", 20, true, TEXT);
        Button addList = button("＋ Lista", false);
        Button close = button("Fechar", false);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        top.addView(addList, new LinearLayout.LayoutParams(dp(88), dp(42)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(82), dp(42));
        cp.leftMargin = dp(6);
        top.addView(close, cp);
        root.addView(top, bottom(10));

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Runnable render = () -> renderProjectsDialog(body, dialog, filterListId);
        addList.setOnClickListener(v -> addListDialog(() -> showProjectsDialog(null), dialog));
        close.setOnClickListener(v -> dialog.dismiss());
        render.run();
        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        dialog.show();
    }

    private void renderProjectsDialog(LinearLayout body, Dialog dialog, String filterListId) {
        body.removeAllViews();
        body.addView(text("LISTAS", 11, true, BRIGHT), bottom(6));
        JSONArray lists = getArray(KEY_LISTS);
        LinearLayout listButtons = new LinearLayout(this);
        listButtons.setOrientation(LinearLayout.VERTICAL);
        Button all = button("Todos os projetos (" + getArray(KEY_PROJECTS).length() + ")", filterListId == null);
        all.setOnClickListener(v -> { dialog.dismiss(); showProjectsDialog(null); });
        listButtons.addView(all, actionParams());
        for (int i = 0; i < lists.length(); i++) {
            JSONObject l = lists.optJSONObject(i);
            if (l == null) continue;
            String id = l.optString("id");
            String name = l.optString("name");
            Button b = button(name + " (" + countProjectsInList(id) + ")", id.equals(filterListId));
            b.setOnClickListener(v -> { dialog.dismiss(); showProjectsDialog(id); });
            b.setOnLongClickListener(v -> { if (!"geral".equals(id)) showListActions(id, name, dialog); return true; });
            listButtons.addView(b, actionParams());
        }
        body.addView(listButtons, bottom(14));
        body.addView(text("PROJETOS", 11, true, BRIGHT), bottom(6));

        JSONArray projects = getArray(KEY_PROJECTS);
        int shown = 0;
        for (int i = projects.length() - 1; i >= 0; i--) {
            JSONObject p = projects.optJSONObject(i);
            if (p == null) continue;
            if (filterListId != null && !filterListId.equals(p.optString("listId", "geral"))) continue;
            shown++;
            body.addView(projectRow(p, dialog), bottom(8));
        }
        if (shown == 0) {
            TextView empty = text("Nenhum projeto nesta lista.", 13, false, MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(30), 0, dp(30));
            body.addView(empty);
        }
    }

    private View projectRow(JSONObject p, Dialog dialog) {
        LinearLayout row = card();
        String id = p.optString("id");
        TextView name = text(p.optString("name", "projeto.html"), 15, true, TEXT);
        row.addView(name, bottom(4));
        JSONArray versions = p.optJSONArray("versions");
        String meta = listName(p.optString("listId", "geral")) + "  •  " + (versions == null ? 0 : versions.length()) + " versões  •  " + formatDate(p.optLong("updatedAt", 0));
        row.addView(text(meta, 10, false, MUTED), bottom(8));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button open = button("Abrir", true);
        Button versionsBtn = button("Versões", false);
        Button more = button("⋮", false);
        actions.addView(open, new LinearLayout.LayoutParams(0, dp(42), 1f));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        vp.leftMargin = dp(6);
        actions.addView(versionsBtn, vp);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(48), dp(42));
        mp.leftMargin = dp(6);
        actions.addView(more, mp);
        row.addView(actions);

        open.setOnClickListener(v -> { loadProject(p); dialog.dismiss(); });
        versionsBtn.setOnClickListener(v -> showVersionsDialog(id));
        more.setOnClickListener(v -> showProjectActions(v, id, dialog));
        return row;
    }

    private void showProjectActions(View anchor, String id, Dialog parent) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Renomear");
        menu.getMenu().add("Duplicar");
        menu.getMenu().add("Mover para lista");
        menu.getMenu().add("Versões");
        menu.getMenu().add("Excluir");
        menu.setOnMenuItemClickListener(item -> {
            String a = item.getTitle().toString();
            if (a.equals("Renomear")) renameProject(id);
            else if (a.equals("Duplicar")) duplicateProject(id);
            else if (a.startsWith("Mover")) chooseListForProject(id);
            else if (a.equals("Versões")) showVersionsDialog(id);
            else if (a.equals("Excluir")) deleteProject(id);
            parent.dismiss();
            handler.postDelayed(() -> showProjectsDialog(null), 120);
            return true;
        });
        menu.show();
    }

    private void renameProject(String id) {
        JSONArray projects = getArray(KEY_PROJECTS);
        JSONObject p = findProject(projects, id);
        if (p == null) return;
        EditText input = new EditText(this);
        input.setText(p.optString("name", "projeto.html"));
        new AlertDialog.Builder(this).setTitle("Renomear projeto").setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", (d, w) -> {
                    try {
                        createVersion(p, "Antes de renomear", true);
                        String n = input.getText().toString().trim();
                        if (!n.toLowerCase(Locale.ROOT).endsWith(".html") && !n.toLowerCase(Locale.ROOT).endsWith(".htm")) n += ".html";
                        p.put("name", n);
                        p.put("updatedAt", System.currentTimeMillis());
                        saveArray(KEY_PROJECTS, projects);
                        if (id.equals(currentProjectId)) fileName.setText(n);
                    } catch (Exception ignored) {}
                }).show();
    }

    private void duplicateProject(String id) {
        try {
            JSONArray projects = getArray(KEY_PROJECTS);
            JSONObject p = findProject(projects, id);
            if (p == null) return;
            JSONObject clone = new JSONObject();
            clone.put("id", UUID.randomUUID().toString());
            clone.put("name", p.optString("name", "projeto.html").replaceFirst("(?i)(\\.html?)$", " - copia$1"));
            clone.put("html", p.optString("html", ""));
            clone.put("listId", p.optString("listId", "geral"));
            clone.put("updatedAt", System.currentTimeMillis());
            clone.put("versions", new JSONArray());
            createVersion(clone, "Projeto duplicado", true);
            projects.put(clone);
            saveArray(KEY_PROJECTS, projects);
            Toast.makeText(this, "Projeto duplicado.", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
    }

    private void deleteProject(String id) {
        Runnable action = () -> {
            JSONArray old = getArray(KEY_PROJECTS);
            JSONArray next = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject p = old.optJSONObject(i);
                if (p != null && !id.equals(p.optString("id"))) next.put(p);
            }
            saveArray(KEY_PROJECTS, next);
            if (id.equals(currentProjectId)) {
                currentProjectId = null;
                prefs.edit().remove("currentProjectId").apply();
            }
            Toast.makeText(this, "Projeto excluído.", Toast.LENGTH_SHORT).show();
        };
        if (confirmDelete) new AlertDialog.Builder(this).setTitle("Excluir projeto?").setMessage("Essa ação remove o projeto do CoderBuilder.").setNegativeButton("Cancelar", null).setPositiveButton("Excluir", (d,w)->action.run()).show();
        else action.run();
    }

    private void chooseListForProject(String projectId) {
        JSONArray lists = getArray(KEY_LISTS);
        String[] names = new String[lists.length()];
        String[] ids = new String[lists.length()];
        for (int i = 0; i < lists.length(); i++) { JSONObject l = lists.optJSONObject(i); names[i] = l.optString("name"); ids[i] = l.optString("id"); }
        new AlertDialog.Builder(this).setTitle("Mover para lista").setItems(names, (d, which) -> {
            try {
                JSONArray projects = getArray(KEY_PROJECTS);
                JSONObject p = findProject(projects, projectId);
                if (p != null) { p.put("listId", ids[which]); p.put("updatedAt", System.currentTimeMillis()); saveArray(KEY_PROJECTS, projects); }
                if (projectId.equals(currentProjectId)) { currentListId = ids[which]; updateListPicker(); }
            } catch (Exception ignored) {}
        }).show();
    }

    private void showVersionsDialog(String projectId) {
        JSONArray projects = getArray(KEY_PROJECTS);
        JSONObject p = findProject(projects, projectId);
        if (p == null) return;
        JSONArray versions = p.optJSONArray("versions");
        if (versions == null || versions.length() == 0) {
            new AlertDialog.Builder(this).setTitle("Versões").setMessage("Ainda não há versões salvas para este projeto.").setPositiveButton("OK", null).show();
            return;
        }
        Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.setBackgroundColor(BG);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("Versões · " + p.optString("name"), 18, true, TEXT), new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button close = button("Fechar", false);
        head.addView(close, new LinearLayout.LayoutParams(dp(82), dp(42)));
        root.addView(head, bottom(8));
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        for (int i = 0; i < versions.length(); i++) {
            JSONObject ver = versions.optJSONObject(i); if (ver == null) continue;
            LinearLayout row = card();
            int number = versions.length() - i;
            row.addView(text("Versão " + number, 15, true, TEXT), bottom(3));
            row.addView(text(ver.optString("reason", "Automática") + "  •  " + formatDate(ver.optLong("ts", 0)), 10, false, MUTED), bottom(7));
            LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
            Button view = button("Visualizar", false); Button restore = button("Restaurar", true); Button dup = button("Duplicar", false);
            actions.addView(view, new LinearLayout.LayoutParams(0, dp(40), 1f));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(40), 1f); rp.leftMargin=dp(5); actions.addView(restore,rp);
            LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(0, dp(40), 1f); dp2.leftMargin=dp(5); actions.addView(dup,dp2);
            row.addView(actions); body.addView(row,bottom(7));
            view.setOnClickListener(v -> { dialog.dismiss(); showVersionPreview(ver); });
            restore.setOnClickListener(v -> { restoreVersion(projectId, ver); dialog.dismiss(); });
            dup.setOnClickListener(v -> { duplicateVersion(p, ver); dialog.dismiss(); });
        }
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(root); dialog.show();
        Window w = dialog.getWindow(); if (w != null) w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
    }

    private void showVersionPreview(JSONObject version) {
        Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        Button back = button("← Voltar", false); root.addView(back,new LinearLayout.LayoutParams(-1,dp(52)));
        WebView web = createWebView(); web.loadDataWithBaseURL("https://coderbuilder.local/", version.optString("html",""), "text/html","UTF-8",null);
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1f)); back.setOnClickListener(v->d.dismiss()); d.setOnDismissListener(x->{web.loadUrl("about:blank");web.destroy();}); d.setContentView(root); d.show();
    }

    private void restoreVersion(String projectId, JSONObject version) {
        try {
            JSONArray projects = getArray(KEY_PROJECTS); JSONObject p = findProject(projects,projectId); if(p==null)return;
            createVersion(p,"Antes de restaurar",true);
            p.put("html",version.optString("html","")); p.put("name",version.optString("name",p.optString("name"))); p.put("listId",version.optString("listId",p.optString("listId","geral"))); p.put("updatedAt",System.currentTimeMillis());
            saveArray(KEY_PROJECTS,projects); if(projectId.equals(currentProjectId))loadProject(p); Toast.makeText(this,"Versão restaurada.",Toast.LENGTH_SHORT).show();
        } catch(Exception ignored){}
    }

    private void duplicateVersion(JSONObject project, JSONObject version) {
        try {
            JSONArray projects=getArray(KEY_PROJECTS); JSONObject clone=new JSONObject(); clone.put("id",UUID.randomUUID().toString()); clone.put("name",project.optString("name","projeto.html").replaceFirst("(?i)(\\.html?)$"," - versao$1")); clone.put("html",version.optString("html","")); clone.put("listId",version.optString("listId",project.optString("listId","geral"))); clone.put("updatedAt",System.currentTimeMillis()); clone.put("versions",new JSONArray()); createVersion(clone,"Criado a partir de versão",true); projects.put(clone); saveArray(KEY_PROJECTS,projects); Toast.makeText(this,"Versão duplicada como projeto.",Toast.LENGTH_SHORT).show();
        }catch(Exception ignored){}
    }

    private void chooseCurrentList() {
        JSONArray lists = getArray(KEY_LISTS); String[] names=new String[lists.length()]; String[] ids=new String[lists.length()]; int selected=0;
        for(int i=0;i<lists.length();i++){JSONObject l=lists.optJSONObject(i);names[i]=l.optString("name");ids[i]=l.optString("id");if(ids[i].equals(currentListId))selected=i;}
        new AlertDialog.Builder(this).setTitle("Escolha a lista").setSingleChoiceItems(names,selected,(d,which)->{currentListId=ids[which];updateListPicker();d.dismiss();scheduleAutosave();}).setNeutralButton("＋ Nova lista",(d,w)->addListDialog(null,null)).show();
    }

    private void addListDialog(Runnable after, Dialog parent) {
        EditText input=new EditText(this);input.setHint("Ex.: Clientes");
        new AlertDialog.Builder(this).setTitle("Nova lista").setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Criar",(d,w)->{
            String name=input.getText().toString().trim();if(name.isEmpty())return;try{JSONArray lists=getArray(KEY_LISTS);JSONObject l=new JSONObject();l.put("id","list_"+UUID.randomUUID());l.put("name",name);lists.put(l);saveArray(KEY_LISTS,lists);if(parent!=null)parent.dismiss();if(after!=null)handler.postDelayed(after,100);updateListPicker();}catch(Exception ignored){}
        }).show();
    }

    private void showListActions(String id,String name,Dialog parent){
        String[] items={"Renomear lista","Excluir lista"};new AlertDialog.Builder(this).setTitle(name).setItems(items,(d,which)->{if(which==0)renameList(id,name);else deleteList(id);parent.dismiss();handler.postDelayed(()->showProjectsDialog(null),120);}).show();
    }

    private void renameList(String id,String oldName){EditText input=new EditText(this);input.setText(oldName);new AlertDialog.Builder(this).setTitle("Renomear lista").setView(input).setPositiveButton("Salvar",(d,w)->{String n=input.getText().toString().trim();if(n.isEmpty())return;try{JSONArray lists=getArray(KEY_LISTS);for(int i=0;i<lists.length();i++){JSONObject l=lists.optJSONObject(i);if(id.equals(l.optString("id")))l.put("name",n);}saveArray(KEY_LISTS,lists);updateListPicker();}catch(Exception ignored){}}).setNegativeButton("Cancelar",null).show();}

    private void deleteList(String id){if("geral".equals(id))return;Runnable action=()->{try{JSONArray lists=getArray(KEY_LISTS),next=new JSONArray();for(int i=0;i<lists.length();i++){JSONObject l=lists.optJSONObject(i);if(!id.equals(l.optString("id")))next.put(l);}saveArray(KEY_LISTS,next);JSONArray projects=getArray(KEY_PROJECTS);for(int i=0;i<projects.length();i++){JSONObject p=projects.optJSONObject(i);if(id.equals(p.optString("listId")))p.put("listId","geral");}saveArray(KEY_PROJECTS,projects);if(id.equals(currentListId))currentListId="geral";updateListPicker();}catch(Exception ignored){}};if(confirmDelete)new AlertDialog.Builder(this).setTitle("Excluir lista?").setMessage("Os projetos serão movidos para Geral.").setPositiveButton("Excluir",(d,w)->action.run()).setNegativeButton("Cancelar",null).show();else action.run();}

    private void showExportsDialog(){JSONArray ex=getArray(KEY_EXPORTS);StringBuilder b=new StringBuilder();for(int i=ex.length()-1;i>=0;i--){JSONObject x=ex.optJSONObject(i);if(x!=null)b.append("• ").append(x.optString("name")).append("\n  ").append(formatDate(x.optLong("ts",0))).append("\n\n");}if(b.length()==0)b.append("Nenhum HTML exportado ainda.");new AlertDialog.Builder(this).setTitle("Baixados / Exportados").setMessage(b.toString()).setPositiveButton("Fechar",null).show();}

    private void showSettingsDialog(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(8),dp(18),0);CheckBox a=check("Salvamento automático",autoSave),p=check("Prévia automática",autoPreview),w=check("Quebrar linhas do editor",wrapLines),v=check("Versões automáticas",autoVersions),del=check("Confirmar exclusões",confirmDelete);root.addView(a);root.addView(p);root.addView(w);root.addView(v);root.addView(del);TextView f=text("Tamanho da fonte: "+fontSize,13,false,Color.DKGRAY);SeekBar seek=new SeekBar(this);seek.setMax(11);seek.setProgress(fontSize-11);root.addView(f);root.addView(seek);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int progress,boolean fromUser){f.setText("Tamanho da fonte: "+(progress+11));}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});new AlertDialog.Builder(this).setTitle("Configurações").setView(root).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",(d,x)->{autoSave=a.isChecked();autoPreview=p.isChecked();wrapLines=w.isChecked();autoVersions=v.isChecked();confirmDelete=del.isChecked();fontSize=seek.getProgress()+11;saveSettings();applyEditorSettings();autosaveLabel.setText(autoSave?"● Salvamento automático ativo":"○ Salvamento automático desligado");autosaveLabel.setTextColor(autoSave?SUCCESS:MUTED);}).show();}

    private CheckBox check(String title,boolean checked){CheckBox c=new CheckBox(this);c.setText(title);c.setTextColor(Color.DKGRAY);c.setChecked(checked);c.setPadding(0,dp(5),0,dp(5));return c;}

    private void showAbout(){new AlertDialog.Builder(this).setTitle("CoderBuilder 2.2 alpha").setMessage("Cole. Crie. Execute.\n\nNesta versão:\n• Listas de projetos\n• Histórico automático de versões\n• Zoom da prévia de 50% a 200%\n• Prévia em pé, deitada ou automática\n• Tela cheia\n• Menu ☰ de ferramentas\n• Autosave e exportação HTML").setPositiveButton("OK",null).show();}

    private void chooseDestination(int requestCode){if(code.getText().toString().trim().isEmpty()){Toast.makeText(this,"Digite ou cole um HTML primeiro.",Toast.LENGTH_SHORT).show();return;}saveCurrentProject(false);Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("text/html");intent.putExtra(Intent.EXTRA_TITLE,normalizedName());startActivityForResult(intent,requestCode);}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if((requestCode!=REQ_SAVE&&requestCode!=REQ_SAVE_OPEN)||resultCode!=RESULT_OK||data==null)return;Uri uri=data.getData();if(uri==null)return;try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new Exception("Não foi possível criar o arquivo.");out.write(code.getText().toString().getBytes(StandardCharsets.UTF_8));out.flush();recordExport(normalizedName());Toast.makeText(this,"HTML salvo com sucesso.",Toast.LENGTH_SHORT).show();if(requestCode==REQ_SAVE_OPEN)openInBrowser(uri);}catch(Exception e){Toast.makeText(this,"Erro ao salvar: "+e.getMessage(),Toast.LENGTH_LONG).show();}}

    private void openInBrowser(Uri uri){Intent view=new Intent(Intent.ACTION_VIEW);view.setDataAndType(uri,"text/html");view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(Intent.createChooser(view,"Abrir HTML com"));}catch(ActivityNotFoundException e){Toast.makeText(this,"Arquivo salvo. Abra-o pelo app Arquivos usando seu navegador.",Toast.LENGTH_LONG).show();}}

    private void recordExport(String name){try{JSONArray ex=getArray(KEY_EXPORTS);JSONObject x=new JSONObject();x.put("id",UUID.randomUUID().toString());x.put("name",name);x.put("ts",System.currentTimeMillis());ex.put(x);JSONArray trimmed=new JSONArray();for(int i=Math.max(0,ex.length()-50);i<ex.length();i++)trimmed.put(ex.get(i));saveArray(KEY_EXPORTS,trimmed);}catch(Exception ignored){}}

    private void ensureDefaultLists(){JSONArray lists=getArray(KEY_LISTS);if(lists.length()>0)return;try{String[] ids={"geral","sites","estudos","templates"};String[] names={"Geral","Sites","Estudos","Templates"};for(int i=0;i<ids.length;i++){JSONObject l=new JSONObject();l.put("id",ids[i]);l.put("name",names[i]);lists.put(l);}saveArray(KEY_LISTS,lists);}catch(Exception ignored){}}

    private void updateListPicker(){if(listPicker!=null)listPicker.setText(listName(currentListId)+"  ▾");}
    private String listName(String id){JSONArray lists=getArray(KEY_LISTS);for(int i=0;i<lists.length();i++){JSONObject l=lists.optJSONObject(i);if(l!=null&&id.equals(l.optString("id")))return l.optString("name","Geral");}return "Geral";}
    private int countProjectsInList(String id){int c=0;JSONArray p=getArray(KEY_PROJECTS);for(int i=0;i<p.length();i++){JSONObject x=p.optJSONObject(i);if(x!=null&&id.equals(x.optString("listId","geral")))c++;}return c;}
    private JSONObject findProject(JSONArray projects,String id){if(id==null)return null;for(int i=0;i<projects.length();i++){JSONObject p=projects.optJSONObject(i);if(p!=null&&id.equals(p.optString("id")))return p;}return null;}
    private JSONArray getArray(String key){try{return new JSONArray(prefs.getString(key,"[]"));}catch(Exception e){return new JSONArray();}}
    private void saveArray(String key,JSONArray array){prefs.edit().putString(key,array.toString()).apply();}
    private String formatDate(long ts){if(ts<=0)return "agora";return DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,new Locale("pt","BR")).format(new Date(ts));}

    private String normalizedName(){String name=fileName.getText().toString().trim();if(name.isEmpty())name="pagina.html";String low=name.toLowerCase(Locale.ROOT);if(!low.endsWith(".html")&&!low.endsWith(".htm"))name+=".html";return name.replaceAll("[<>:\"/\\\\|?*]","_");}

    private void loadSettings(){autoSave=prefs.getBoolean("autoSave",true);autoPreview=prefs.getBoolean("autoPreview",true);wrapLines=prefs.getBoolean("wrapLines",false);confirmDelete=prefs.getBoolean("confirmDelete",true);autoVersions=prefs.getBoolean("autoVersions",true);fontSize=prefs.getInt("fontSize",13);previewZoom=prefs.getInt("previewZoom",100);previewDevice=prefs.getString("previewDevice","auto");}
    private void saveSettings(){prefs.edit().putBoolean("autoSave",autoSave).putBoolean("autoPreview",autoPreview).putBoolean("wrapLines",wrapLines).putBoolean("confirmDelete",confirmDelete).putBoolean("autoVersions",autoVersions).putInt("fontSize",fontSize).putInt("previewZoom",previewZoom).putString("previewDevice",previewDevice).apply();}
    private void applyEditorSettings(){code.setTextSize(fontSize);code.setHorizontallyScrolling(!wrapLines);}

    private String defaultHtml(){return "<!doctype html>\n<html lang=\"pt-BR\">\n<head>\n  <meta charset=\"utf-8\">\n  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n  <title>Meu Site</title>\n  <style>\n    body{margin:0;font-family:Arial,sans-serif;background:linear-gradient(135deg,#0d1117,#1a1f2e);color:white;min-height:100vh;display:grid;place-items:center}\n    .box{text-align:center;padding:36px}\n    h1{font-size:42px;margin:0 0 12px}\n    p{color:#c8d2e1}\n    button{padding:12px 20px;border:0;border-radius:10px;background:#1479ff;color:white;font-weight:bold}\n  </style>\n</head>\n<body>\n  <div class=\"box\">\n    <h1>Meu Site</h1>\n    <p>Bem-vindo ao meu site criado com CoderBuilder!</p>\n    <button>Vamos codar! 🚀</button>\n  </div>\n</body>\n</html>";}
    private String emptyHtml(){return "<!doctype html>\n<html lang=\"pt-BR\">\n<head>\n  <meta charset=\"utf-8\">\n  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n  <title>Novo projeto</title>\n</head>\n<body>\n\n</body>\n</html>";}

    @Override protected void onPause(){if(autoSave&&code!=null&&code.getText().length()>0)saveCurrentProject(false);saveSettings();super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(preview!=null){preview.loadUrl("about:blank");preview.destroy();}super.onDestroy();}
}
