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
    private static final String PREFS = "coderbuilder_v21";
    private static final String KEY_PROJECTS = "projects";
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

    private EditText fileName;
    private EditText code;
    private WebView preview;
    private TextView status;
    private TextView autosaveLabel;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable autosaveRunnable;
    private Runnable previewRunnable;
    private String currentProjectId;
    private boolean suppressChanges = false;
    private boolean autoPreview = true;
    private boolean wrapLines = false;
    private boolean confirmDelete = true;
    private int fontSize = 13;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        autoPreview = prefs.getBoolean("autoPreview", true);
        wrapLines = prefs.getBoolean("wrapLines", false);
        confirmDelete = prefs.getBoolean("confirmDelete", true);
        fontSize = prefs.getInt("fontSize", 13);
        currentProjectId = prefs.getString("currentProjectId", null);

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

        TextView version = text("CoderBuilder 2.1.0-alpha.1  •  Cole. Crie. Execute.", 11, false, MUTED);
        version.setGravity(Gravity.CENTER);
        content.addView(version, new LinearLayout.LayoutParams(-1, dp(36)));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        attachWatchers();
        restoreCurrentProject();
        applyEditorSettings();
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
        header.setPadding(dp(16), dp(10), dp(12), dp(10));
        header.setBackgroundColor(Color.rgb(5, 12, 23));

        TextView logo = text("</>", 17, true, Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable logoBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{BRIGHT, BLUE, CYAN});
        logoBg.setCornerRadius(dp(13));
        logo.setBackground(logoBg);
        header.addView(logo, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout titleWrap = new LinearLayout(this);
        titleWrap.setOrientation(LinearLayout.VERTICAL);
        titleWrap.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tw = new LinearLayout.LayoutParams(0, -1, 1f);
        tw.leftMargin = dp(12);
        titleWrap.addView(text("CoderBuilder", 22, true, TEXT));
        titleWrap.addView(text("Cole. Crie. Execute.", 11, false, BRIGHT));
        header.addView(titleWrap, tw);

        Button more = button("⋮", false);
        more.setTextSize(24);
        more.setOnClickListener(this::showMoreMenu);
        header.addView(more, new LinearLayout.LayoutParams(dp(48), dp(44)));
        return header;
    }

    private View buildFileCard() {
        LinearLayout box = card();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("▣  ARQUIVO ATUAL", 12, true, BRIGHT), new LinearLayout.LayoutParams(0, dp(28), 1f));
        autosaveLabel = text("● Autosave", 10, false, SUCCESS);
        head.addView(autosaveLabel, new LinearLayout.LayoutParams(-2, dp(28)));
        box.addView(head, marginBottom(8));

        fileName = new EditText(this);
        fileName.setSingleLine(true);
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
        head.addView(text("</>  EDITOR", 12, true, CYAN), new LinearLayout.LayoutParams(0, dp(32), 1f));
        status = text("● Pronto", 11, false, SUCCESS);
        head.addView(status, new LinearLayout.LayoutParams(-2, dp(32)));
        box.addView(head, marginBottom(8));

        code = new EditText(this);
        code.setGravity(Gravity.TOP | Gravity.START);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextColor(Color.rgb(219, 232, 248));
        code.setHintTextColor(MUTED);
        code.setPadding(dp(14), dp(14), dp(14), dp(14));
        code.setVerticalScrollBarEnabled(true);
        code.setBackground(rounded(Color.rgb(6, 16, 29), BORDER, 12));
        box.addView(code, new LinearLayout.LayoutParams(-1, dp(390)));
        return box;
    }

    private View buildActions() {
        LinearLayout box = card();
        box.addView(text("⚡  AÇÕES", 12, true, BRIGHT), marginBottom(4));
        Button saveProject = button("✓  Salvar projeto", true);
        Button previewBtn = button("◉  Atualizar prévia", false);
        Button fullBtn = button("⛶  Prévia em tela cheia", false);
        Button exportBtn = button("↓  Exportar HTML", false);
        Button saveOpenBtn = button("↗  Exportar e abrir", false);
        Button newBtn = button("＋  Novo arquivo", false);
        box.addView(saveProject, actionParams());
        box.addView(previewBtn, actionParams());
        box.addView(fullBtn, actionParams());
        box.addView(exportBtn, actionParams());
        box.addView(saveOpenBtn, actionParams());
        box.addView(newBtn, actionParams());

        saveProject.setOnClickListener(v -> saveCurrentProject(false));
        previewBtn.setOnClickListener(v -> updatePreview());
        fullBtn.setOnClickListener(v -> showFullscreenPreview());
        exportBtn.setOnClickListener(v -> chooseDestination(REQ_SAVE));
        saveOpenBtn.setOnClickListener(v -> chooseDestination(REQ_SAVE_OPEN));
        newBtn.setOnClickListener(v -> createNewProject());
        return box;
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
        p.topMargin = dp(8);
        return p;
    }

    private View buildPreviewCard() {
        LinearLayout box = card();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("◉  PRÉ-VISUALIZAÇÃO", 12, true, CYAN), new LinearLayout.LayoutParams(0, dp(36), 1f));
        Button full = button("⛶", false);
        full.setOnClickListener(v -> showFullscreenPreview());
        head.addView(full, new LinearLayout.LayoutParams(dp(48), dp(36)));
        box.addView(head, marginBottom(8));

        preview = createWebView();
        preview.setBackground(rounded(Color.WHITE, BORDER, 12));
        box.addView(preview, new LinearLayout.LayoutParams(-1, dp(320)));
        return box;
    }

    private WebView createWebView() {
        WebView web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        web.setWebViewClient(new WebViewClient());
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        return web;
    }

    private void attachWatchers() {
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) {
                if (suppressChanges) return;
                if (status != null) status.setText("● Alterações pendentes");
                if (autosaveLabel != null) autosaveLabel.setText("● Salvando...");
                scheduleAutosave();
                if (autoPreview) schedulePreview();
            }
        };
        code.addTextChangedListener(watcher);
        fileName.addTextChangedListener(watcher);
    }

    private void scheduleAutosave() {
        if (autosaveRunnable != null) handler.removeCallbacks(autosaveRunnable);
        autosaveRunnable = () -> saveCurrentProject(true);
        handler.postDelayed(autosaveRunnable, 700);
    }

    private void schedulePreview() {
        if (previewRunnable != null) handler.removeCallbacks(previewRunnable);
        previewRunnable = this::updatePreview;
        handler.postDelayed(previewRunnable, 260);
    }

    private JSONArray loadProjects() {
        try { return new JSONArray(prefs.getString(KEY_PROJECTS, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void saveProjects(JSONArray array) {
        prefs.edit().putString(KEY_PROJECTS, array.toString()).apply();
    }

    private JSONObject findProject(String id) {
        if (id == null) return null;
        JSONArray projects = loadProjects();
        for (int i = 0; i < projects.length(); i++) {
            JSONObject p = projects.optJSONObject(i);
            if (p != null && id.equals(p.optString("id"))) return p;
        }
        return null;
    }

    private void restoreCurrentProject() {
        JSONObject project = findProject(currentProjectId);
        if (project == null) {
            JSONArray projects = loadProjects();
            project = projects.optJSONObject(0);
        }
        if (project != null) loadProjectObject(project);
        else {
            suppressChanges = true;
            currentProjectId = null;
            fileName.setText("meu_site.html");
            code.setText(defaultHtml());
            suppressChanges = false;
            saveCurrentProject(true);
        }
    }

    private void loadProjectObject(JSONObject project) {
        suppressChanges = true;
        currentProjectId = project.optString("id", null);
        fileName.setText(project.optString("name", "pagina.html"));
        code.setText(project.optString("html", defaultHtml()));
        prefs.edit().putString("currentProjectId", currentProjectId).apply();
        suppressChanges = false;
        if (status != null) status.setText("● Projeto aberto");
        if (autosaveLabel != null) autosaveLabel.setText("● Salvo");
        updatePreview();
    }

    private void saveCurrentProject(boolean silent) {
        try {
            JSONArray old = loadProjects();
            JSONArray next = new JSONArray();
            if (currentProjectId == null || currentProjectId.isEmpty()) currentProjectId = UUID.randomUUID().toString();
            JSONObject current = new JSONObject();
            current.put("id", currentProjectId);
            current.put("name", normalizedName());
            current.put("html", code.getText().toString());
            current.put("updatedAt", System.currentTimeMillis());
            next.put(current);
            for (int i = 0; i < old.length() && next.length() < 100; i++) {
                JSONObject p = old.optJSONObject(i);
                if (p != null && !currentProjectId.equals(p.optString("id"))) next.put(p);
            }
            saveProjects(next);
            prefs.edit().putString("currentProjectId", currentProjectId).apply();
            if (status != null) status.setText("● Projeto salvo");
            if (autosaveLabel != null) autosaveLabel.setText("● Autosave ativo");
            if (!silent) Toast.makeText(this, "Projeto salvo no CoderBuilder.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            if (!silent) Toast.makeText(this, "Não foi possível salvar o projeto.", Toast.LENGTH_SHORT).show();
        }
    }

    private void createNewProject() {
        saveCurrentProject(true);
        suppressChanges = true;
        currentProjectId = UUID.randomUUID().toString();
        fileName.setText("novo_arquivo.html");
        code.setText("<!doctype html>\n<html lang=\"pt-BR\">\n<head>\n  <meta charset=\"utf-8\">\n  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n  <title>Novo projeto</title>\n</head>\n<body>\n  <h1>Olá!</h1>\n</body>\n</html>");
        suppressChanges = false;
        saveCurrentProject(true);
        updatePreview();
        Toast.makeText(this, "Novo projeto criado.", Toast.LENGTH_SHORT).show();
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Arquivos salvos");
        menu.getMenu().add("Baixados / Exportados");
        menu.getMenu().add("Configurações");
        menu.getMenu().add("Novo arquivo");
        menu.getMenu().add("Sobre o CoderBuilder");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.startsWith("Arquivos")) showProjectsDialog();
            else if (title.startsWith("Baixados")) showExportsDialog();
            else if (title.startsWith("Config")) showSettingsDialog();
            else if (title.startsWith("Novo")) createNewProject();
            else showAboutDialog();
            return true;
        });
        menu.show();
    }

    private void showProjectsDialog() {
        JSONArray projects = loadProjects();
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(6), dp(10), dp(10));
        for (int i = 0; i < projects.length(); i++) {
            JSONObject p = projects.optJSONObject(i);
            if (p == null) continue;
            final String id = p.optString("id");
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(8), dp(4), dp(8));
            row.setBackground(rounded(ELEV, BORDER, 10));
            TextView name = text(p.optString("name", "pagina.html"), 14, true, TEXT);
            name.setOnClickListener(v -> { loadProjectObject(findProject(id)); });
            row.addView(name, new LinearLayout.LayoutParams(0, dp(48), 1f));
            Button more = button("⋮", false);
            more.setOnClickListener(v -> showProjectItemMenu(v, id));
            row.addView(more, new LinearLayout.LayoutParams(dp(48), dp(42)));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.bottomMargin = dp(8);
            list.addView(row, rp);
        }
        if (projects.length() == 0) list.addView(text("Nenhum projeto salvo.", 13, false, MUTED));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        new AlertDialog.Builder(this).setTitle("Arquivos salvos").setView(scroll).setNegativeButton("Fechar", null).show();
    }

    private void showProjectItemMenu(View anchor, String id) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Abrir");
        menu.getMenu().add("Renomear");
        menu.getMenu().add("Duplicar");
        menu.getMenu().add("Excluir");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.equals("Abrir")) loadProjectObject(findProject(id));
            else if (title.equals("Renomear")) renameProject(id);
            else if (title.equals("Duplicar")) duplicateProject(id);
            else deleteProject(id);
            return true;
        });
        menu.show();
    }

    private void renameProject(String id) {
        JSONObject p = findProject(id);
        if (p == null) return;
        EditText input = new EditText(this);
        input.setText(p.optString("name", "pagina.html"));
        new AlertDialog.Builder(this).setTitle("Renomear projeto").setView(input).setPositiveButton("Salvar", (d, w) -> {
            try {
                JSONArray projects = loadProjects();
                for (int i = 0; i < projects.length(); i++) {
                    JSONObject item = projects.optJSONObject(i);
                    if (item != null && id.equals(item.optString("id"))) {
                        item.put("name", normalizeName(input.getText().toString()));
                        item.put("updatedAt", System.currentTimeMillis());
                    }
                }
                saveProjects(projects);
                if (id.equals(currentProjectId)) {
                    suppressChanges = true;
                    fileName.setText(normalizeName(input.getText().toString()));
                    suppressChanges = false;
                }
            } catch (Exception ignored) {}
        }).setNegativeButton("Cancelar", null).show();
    }

    private void duplicateProject(String id) {
        JSONObject source = findProject(id);
        if (source == null) return;
        try {
            JSONArray old = loadProjects();
            JSONArray next = new JSONArray();
            JSONObject copy = new JSONObject();
            copy.put("id", UUID.randomUUID().toString());
            String base = source.optString("name", "pagina.html").replaceAll("(?i)\\.html?$", "");
            copy.put("name", normalizeName(base + "_copia.html"));
            copy.put("html", source.optString("html", ""));
            copy.put("updatedAt", System.currentTimeMillis());
            next.put(copy);
            for (int i = 0; i < old.length() && next.length() < 100; i++) next.put(old.optJSONObject(i));
            saveProjects(next);
            Toast.makeText(this, "Projeto duplicado.", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
    }

    private void deleteProject(String id) {
        Runnable remove = () -> {
            JSONArray old = loadProjects();
            JSONArray next = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject p = old.optJSONObject(i);
                if (p != null && !id.equals(p.optString("id"))) next.put(p);
            }
            saveProjects(next);
            if (id.equals(currentProjectId)) {
                currentProjectId = null;
                createNewProject();
            }
            Toast.makeText(this, "Projeto excluído.", Toast.LENGTH_SHORT).show();
        };
        if (!confirmDelete) { remove.run(); return; }
        new AlertDialog.Builder(this).setTitle("Excluir projeto?").setMessage("Essa ação remove o projeto salvo do CoderBuilder.").setPositiveButton("Excluir", (d, w) -> remove.run()).setNegativeButton("Cancelar", null).show();
    }

    private void showSettingsDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(10), dp(20), dp(6));
        CheckBox auto = new CheckBox(this);
        auto.setText("Prévia automática enquanto digita");
        auto.setChecked(autoPreview);
        CheckBox wrap = new CheckBox(this);
        wrap.setText("Quebra automática de linha no editor");
        wrap.setChecked(wrapLines);
        CheckBox confirm = new CheckBox(this);
        confirm.setText("Confirmar antes de excluir projeto");
        confirm.setChecked(confirmDelete);
        TextView font = text("Tamanho da fonte: " + fontSize + " px", 14, true, Color.DKGRAY);
        SeekBar seek = new SeekBar(this);
        seek.setMax(11);
        seek.setProgress(Math.max(0, Math.min(11, fontSize - 11)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) { font.setText("Tamanho da fonte: " + (p + 11) + " px"); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        box.addView(auto); box.addView(wrap); box.addView(confirm); box.addView(font); box.addView(seek);
        new AlertDialog.Builder(this).setTitle("Configurações").setView(box).setPositiveButton("Aplicar", (d, w) -> {
            autoPreview = auto.isChecked();
            wrapLines = wrap.isChecked();
            confirmDelete = confirm.isChecked();
            fontSize = seek.getProgress() + 11;
            prefs.edit().putBoolean("autoPreview", autoPreview).putBoolean("wrapLines", wrapLines).putBoolean("confirmDelete", confirmDelete).putInt("fontSize", fontSize).apply();
            applyEditorSettings();
            if (autoPreview) updatePreview();
        }).setNegativeButton("Cancelar", null).show();
    }

    private void applyEditorSettings() {
        if (code == null) return;
        code.setTextSize(fontSize);
        code.setHorizontallyScrolling(!wrapLines);
        code.setHorizontalScrollBarEnabled(!wrapLines);
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this).setTitle("CoderBuilder 2.1").setMessage("Cole. Crie. Execute.\n\nVersão 2.1.0-alpha.1\n\nProjetos salvos, autosave, histórico de exportações, configurações e prévia em tela cheia.").setPositiveButton("OK", null).show();
    }

    private void showFullscreenPreview() {
        final Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(8), dp(10), dp(8));
        bar.setBackgroundColor(Color.rgb(5, 12, 23));
        Button back = button("← Voltar", false);
        TextView title = text("Prévia em tela cheia", 14, true, TEXT);
        Button reload = button("↻ Atualizar", false);
        bar.addView(back, new LinearLayout.LayoutParams(dp(100), dp(44)));
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1f));
        bar.addView(reload, new LinearLayout.LayoutParams(dp(120), dp(44)));
        WebView full = createWebView();
        full.loadDataWithBaseURL("https://coderbuilder.local/", code.getText().toString(), "text/html", "UTF-8", null);
        reload.setOnClickListener(v -> full.loadDataWithBaseURL("https://coderbuilder.local/", code.getText().toString(), "text/html", "UTF-8", null));
        back.setOnClickListener(v -> dialog.dismiss());
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(60)));
        root.addView(full, new LinearLayout.LayoutParams(-1, 0, 1f));
        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        dialog.setOnDismissListener(d -> { full.loadUrl("about:blank"); full.destroy(); });
        dialog.show();
        if (w != null) w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
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
        preview.stopLoading();
        preview.loadDataWithBaseURL("https://coderbuilder.local/", code.getText().toString(), "text/html", "UTF-8", null);
        if (status != null) status.setText("● Prévia atualizada");
    }

    private String normalizeName(String name) {
        name = name == null ? "" : name.trim();
        if (name.isEmpty()) name = "pagina.html";
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".html") && !lower.endsWith(".htm")) name += ".html";
        return name;
    }

    private String normalizedName() { return normalizeName(fileName.getText().toString()); }

    private void chooseDestination(int requestCode) {
        if (code.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Cole um código HTML primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        saveCurrentProject(true);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/html");
        intent.putExtra(Intent.EXTRA_TITLE, normalizedName());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode != REQ_SAVE && requestCode != REQ_SAVE_OPEN) || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (Exception ignored) {}
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new Exception("Não foi possível criar o arquivo.");
                out.write(code.getText().toString().getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            recordExport(normalizedName(), uri.toString());
            Toast.makeText(this, "HTML exportado com sucesso.", Toast.LENGTH_SHORT).show();
            if (status != null) status.setText("● HTML exportado");
            if (requestCode == REQ_SAVE_OPEN) openInBrowser(uri);
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao salvar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void recordExport(String name, String uri) {
        try {
            JSONArray old = new JSONArray(prefs.getString(KEY_EXPORTS, "[]"));
            JSONArray next = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("name", name);
            item.put("uri", uri);
            item.put("exportedAt", System.currentTimeMillis());
            next.put(item);
            for (int i = 0; i < old.length() && next.length() < 50; i++) {
                JSONObject p = old.optJSONObject(i);
                if (p != null && !uri.equals(p.optString("uri"))) next.put(p);
            }
            prefs.edit().putString(KEY_EXPORTS, next.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void showExportsDialog() {
        JSONArray exports;
        try { exports = new JSONArray(prefs.getString(KEY_EXPORTS, "[]")); } catch (Exception e) { exports = new JSONArray(); }
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(6), dp(10), dp(10));
        DateFormat fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        for (int i = 0; i < exports.length(); i++) {
            JSONObject item = exports.optJSONObject(i);
            if (item == null) continue;
            String name = item.optString("name", "arquivo.html");
            String uriText = item.optString("uri", "");
            long time = item.optLong("exportedAt", 0);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(8), dp(4), dp(8));
            row.setBackground(rounded(ELEV, BORDER, 10));
            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(text(name, 14, true, TEXT));
            labels.addView(text(time > 0 ? fmt.format(new Date(time)) : "Exportado", 10, false, MUTED));
            row.addView(labels, new LinearLayout.LayoutParams(0, dp(54), 1f));
            Button open = button("Abrir", false);
            open.setOnClickListener(v -> openInBrowser(Uri.parse(uriText)));
            row.addView(open, new LinearLayout.LayoutParams(dp(90), dp(42)));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.bottomMargin = dp(8);
            list.addView(row, rp);
        }
        if (exports.length() == 0) list.addView(text("Nenhum HTML exportado ainda.", 13, false, MUTED));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        new AlertDialog.Builder(this).setTitle("Baixados / Exportados").setView(scroll).setNegativeButton("Fechar", null).show();
    }

    private void openInBrowser(Uri uri) {
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(uri, "text/html");
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(view, "Abrir HTML com"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Não encontrei um aplicativo para abrir este HTML.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        saveCurrentProject(true);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (autosaveRunnable != null) handler.removeCallbacks(autosaveRunnable);
        if (previewRunnable != null) handler.removeCallbacks(previewRunnable);
        if (preview != null) {
            preview.loadUrl("about:blank");
            preview.destroy();
        }
        super.onDestroy();
    }
}
