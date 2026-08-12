package com.salvadordetexto.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {
    private static final int PRIMARY = Color.rgb(52, 87, 213);
    private static final int ACTIVE_DARK = Color.rgb(36, 58, 156);
    private static final int INACTIVE = Color.rgb(229, 233, 244);
    private static final int TEXT_DARK = Color.rgb(32, 33, 36);
    private static final int CREATE_BACKUP = 1001;
    private static final int OPEN_BACKUP = 1002;
    private static final int SECTION_TEXTS = 0;
    private static final int SECTION_FAVORITES = 1;
    private static final int SECTION_HIDDEN = 2;
    private static final int SECTION_TRASH = 3;

    private NoteDbHelper db;
    private SharedPreferences prefs;
    private EditText search;
    private ListView list;
    private Button listFilterButton, settingsButton, addButton;
    private Button navTexts, navFavorites, navHidden, navTrash;
    private TextView sectionTitle;
    private int section = SECTION_TEXTS;
    private String selectedList = null;
    private boolean strongEffects = true;
    private boolean confirmDelete = true;
    private boolean showPreview = true;
    private final NoteAdapter adapter = new NoteAdapter();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("script_guard_settings", MODE_PRIVATE);
        strongEffects = prefs.getBoolean("strong_effects", true);
        confirmDelete = prefs.getBoolean("confirm_delete", true);
        showPreview = prefs.getBoolean("show_preview", true);
        db = new NoteDbHelper(this);
        View ui = buildUi();
        setContentView(ui);
        ui.setAlpha(0f);
        ui.setTranslationY(dp(strongEffects ? 22 : 8));
        ui.animate().alpha(1f).translationY(0f).setDuration(strongEffects ? 420 : 220).setInterpolator(new DecelerateInterpolator()).start();
        updateNavigation();
        reload(false);
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.setBackgroundColor(Color.rgb(247, 248, 252));

        TextView title = new TextView(this);
        title.setText("Script Guard");
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(TEXT_DARK);
        root.addView(title);

        sectionTitle = new TextView(this);
        sectionTitle.setTextSize(14);
        sectionTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sectionTitle.setTextColor(PRIMARY);
        sectionTitle.setPadding(0, dp(2), 0, dp(10));
        root.addView(sectionTitle);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        navTexts = navButton("Textos");
        navFavorites = navButton("★ Favoritos");
        navHidden = navButton("Ocultos");
        navTrash = navButton("Lixeira");
        nav.addView(navTexts, new LinearLayout.LayoutParams(0, dp(50), 1f));
        nav.addView(navFavorites, new LinearLayout.LayoutParams(0, dp(50), 1f));
        nav.addView(navHidden, new LinearLayout.LayoutParams(0, dp(50), 1f));
        nav.addView(navTrash, new LinearLayout.LayoutParams(0, dp(50), 1f));
        root.addView(nav);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        listFilterButton = smallButton("Listas: Todas");
        settingsButton = smallButton("⚙ Configurações");
        tools.addView(listFilterButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        tools.addView(settingsButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(tools);

        search = new EditText(this);
        search.setHint("Pesquisar textos");
        search.setSingleLine(true);
        search.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        addButton = button("+ Novo texto");
        root.addView(addButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        list = new ListView(this);
        list.setAdapter(adapter);
        list.setDividerHeight(1);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        navTexts.setOnClickListener(v -> setSection(SECTION_TEXTS));
        navFavorites.setOnClickListener(v -> setSection(SECTION_FAVORITES));
        navHidden.setOnClickListener(v -> setSection(SECTION_HIDDEN));
        navTrash.setOnClickListener(v -> setSection(SECTION_TRASH));
        listFilterButton.setOnClickListener(v -> showListsDialog());
        settingsButton.setOnClickListener(v -> showSettings());
        addButton.setOnClickListener(v -> {
            if (section == SECTION_HIDDEN || section == SECTION_TRASH) setSection(SECTION_TEXTS);
            else openEditor(new Note(0, "", "", selectedList == null ? "" : selectedList, false, 0, 0));
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { reload(false); }
            public void afterTextChanged(Editable e) {}
        });
        list.setOnItemClickListener((p, v, pos, id) -> {
            pulse(v);
            Note n = adapter.items.get(pos);
            v.postDelayed(() -> {
                if (section == SECTION_TRASH) showTrashActions(n);
                else if (section == SECTION_HIDDEN) showHiddenActions(n);
                else openEditor(n);
            }, strongEffects ? 120 : 60);
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            Note n = adapter.items.get(pos);
            if (section == SECTION_TRASH) showTrashActions(n);
            else if (section == SECTION_HIDDEN) showHiddenActions(n);
            else showActions(n);
            return true;
        });
        return root;
    }

    private void setSection(int newSection) {
        if (section == newSection) {
            pulse(navForSection(newSection));
            return;
        }
        section = newSection;
        updateNavigation();
        reload(true);
    }

    private Button navForSection(int s) {
        if (s == SECTION_FAVORITES) return navFavorites;
        if (s == SECTION_HIDDEN) return navHidden;
        if (s == SECTION_TRASH) return navTrash;
        return navTexts;
    }

    private void updateNavigation() {
        styleNav(navTexts, section == SECTION_TEXTS);
        styleNav(navFavorites, section == SECTION_FAVORITES);
        styleNav(navHidden, section == SECTION_HIDDEN);
        styleNav(navTrash, section == SECTION_TRASH);

        String name = "Textos";
        if (section == SECTION_FAVORITES) name = "Favoritos";
        else if (section == SECTION_HIDDEN) name = "Ocultos";
        else if (section == SECTION_TRASH) name = "Lixeira";
        sectionTitle.setText("v1.6  •  " + name);

        boolean canFilterLists = section == SECTION_TEXTS || section == SECTION_FAVORITES;
        listFilterButton.setEnabled(canFilterLists);
        listFilterButton.setAlpha(canFilterLists ? 1f : .45f);
        addButton.setText((section == SECTION_HIDDEN || section == SECTION_TRASH) ? "← Voltar aos textos" : "+ Novo texto");
    }

    private void styleNav(Button b, boolean active) {
        b.setBackgroundColor(active ? ACTIVE_DARK : INACTIVE);
        b.setTextColor(active ? Color.WHITE : TEXT_DARK);
        b.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        b.animate().scaleX(active ? 1.03f : 1f).scaleY(active ? 1.03f : 1f).setDuration(strongEffects ? 180 : 90).start();
    }

    private void showListsDialog() {
        if (section == SECTION_HIDDEN || section == SECTION_TRASH) return;
        List<String> names = new ArrayList<>();
        names.add("Todas as listas");
        names.addAll(db.getLists());
        names.add("+ Criar nova lista");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Listas").setItems(names.toArray(new String[0]), (d, which) -> {
            if (which == 0) {
                selectedList = null;
                listFilterButton.setText("Listas: Todas");
                reload(true);
            } else if (which == names.size() - 1) createListDialog();
            else {
                selectedList = names.get(which);
                listFilterButton.setText("Lista: " + selectedList);
                reload(true);
            }
        }).create();
        showAnimated(dialog);
    }

    private void createListDialog() {
        EditText input = new EditText(this);
        input.setHint("Nome da lista");
        input.setSingleLine(true);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(6), dp(20), 0);
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Nova lista").setView(box).setNegativeButton("Cancelar", null).setPositiveButton("Criar", (d, w) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) return;
            db.addList(name);
            selectedList = name;
            listFilterButton.setText("Lista: " + name);
            reload(true);
            Toast.makeText(this, "Lista criada.", Toast.LENGTH_SHORT).show();
        }).create();
        showAnimated(dialog);
    }

    private void reload(boolean sectionChange) {
        if (db == null || search == null) return;
        String q = search.getText().toString();
        adapter.items.clear();
        if (section == SECTION_FAVORITES) adapter.items.addAll(db.search(q, true, selectedList));
        else if (section == SECTION_HIDDEN) adapter.items.addAll(db.searchHidden(q));
        else if (section == SECTION_TRASH) adapter.items.addAll(db.searchTrash(q));
        else adapter.items.addAll(db.search(q, false, selectedList));
        adapter.notifyDataSetChanged();
        if (list != null) {
            list.animate().cancel();
            list.setAlpha(0f);
            list.setTranslationX(dp(sectionChange && strongEffects ? 34 : 12));
            list.animate().alpha(1f).translationX(0f).setDuration(strongEffects ? 260 : 140).setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    private void openEditor(Note note) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), 0);

        EditText title = new EditText(this);
        title.setHint("Título");
        title.setText(note.title);
        box.addView(title);

        TextView listLabel = new TextView(this);
        listLabel.setText("Lista");
        listLabel.setTextSize(13);
        listLabel.setTextColor(Color.DKGRAY);
        listLabel.setPadding(0, dp(6), 0, 0);
        box.addView(listLabel);

        List<String> lists = new ArrayList<>();
        lists.add("Sem lista");
        lists.addAll(db.getLists());
        if (note.category != null && !note.category.trim().isEmpty() && !containsIgnoreCase(lists, note.category.trim())) lists.add(note.category.trim());
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, lists);
        spinner.setAdapter(spinnerAdapter);
        int selected = 0;
        for (int i = 1; i < lists.size(); i++) if (note.category != null && lists.get(i).equalsIgnoreCase(note.category.trim())) { selected = i; break; }
        spinner.setSelection(selected);
        box.addView(spinner);

        Button newList = smallButton("+ Nova lista");
        box.addView(newList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        CheckBox favorite = new CheckBox(this);
        favorite.setText("Favorito");
        favorite.setChecked(note.favorite);
        box.addView(favorite);

        CheckBox hidden = new CheckBox(this);
        hidden.setText("Ocultar este texto");
        hidden.setChecked(note.id > 0 && db.isHidden(note.id));
        box.addView(hidden);

        EditText content = new EditText(this);
        content.setHint("Digite ou cole seu texto aqui");
        content.setGravity(Gravity.TOP);
        content.setMinLines(10);
        content.setText(note.content);
        box.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(270)));

        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(note.id == 0 ? "Novo texto" : "Editar texto").setView(box).setPositiveButton("Salvar", null).setNegativeButton("Fechar", null).setNeutralButton("Compartilhar", null).create();
        dialog.setOnShowListener(x -> {
            animateDialog(dialog);
            newList.setOnClickListener(v -> {
                EditText input = new EditText(this);
                input.setHint("Nome da nova lista");
                AlertDialog child = new AlertDialog.Builder(this).setTitle("Criar lista").setView(input).setNegativeButton("Cancelar", null).setPositiveButton("Criar", (a, b) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty() && !containsIgnoreCase(lists, name)) {
                        db.addList(name);
                        lists.add(name);
                        spinnerAdapter.notifyDataSetChanged();
                        spinner.setSelection(lists.size() - 1);
                    }
                }).create();
                showAnimated(child);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                note.title = title.getText().toString();
                String chosen = (String) spinner.getSelectedItem();
                note.category = "Sem lista".equals(chosen) ? "" : chosen;
                note.content = content.getText().toString();
                note.favorite = favorite.isChecked();
                if (note.title.trim().isEmpty() && note.content.trim().isEmpty()) {
                    Toast.makeText(this, "Digite um título ou texto.", Toast.LENGTH_SHORT).show();
                    return;
                }
                db.save(note);
                if (hidden.isChecked()) db.hide(note.id); else db.unhide(note.id);
                reload(true);
                Toast.makeText(this, hidden.isChecked() ? "Texto salvo em Ocultos." : "Texto salvo.", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> dialog.dismiss());
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> share(title.getText().toString(), content.getText().toString()));
        });
        dialog.show();
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        for (String value : values) if (value.equalsIgnoreCase(target)) return true;
        return false;
    }

    private void showActions(Note note) {
        String fav = note.favorite ? "Remover dos favoritos" : "Adicionar aos favoritos";
        String[] actions = {"Abrir", fav, "Ocultar", "Copiar", "Compartilhar", "Duplicar", "Mover para lixeira"};
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(note.title.isEmpty() ? "Texto sem título" : note.title).setItems(actions, (d, which) -> {
            if (which == 0) openEditor(note);
            else if (which == 1) { note.favorite = !note.favorite; db.save(note); reload(true); }
            else if (which == 2) { db.hide(note.id); reload(true); Toast.makeText(this, "Texto movido para Ocultos.", Toast.LENGTH_SHORT).show(); }
            else if (which == 3) copy(note.content);
            else if (which == 4) share(note.title, note.content);
            else if (which == 5) { db.duplicate(note); reload(true); Toast.makeText(this, "Cópia criada.", Toast.LENGTH_SHORT).show(); }
            else confirmTrash(note);
        }).create();
        showAnimated(dialog);
    }

    private void showHiddenActions(Note note) {
        String[] actions = {"Abrir", "Mostrar novamente", "Copiar", "Compartilhar", "Mover para lixeira"};
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(note.title.isEmpty() ? "Texto oculto" : note.title).setItems(actions, (d, which) -> {
            if (which == 0) openEditor(note);
            else if (which == 1) { db.unhide(note.id); reload(true); Toast.makeText(this, "Texto voltou para Textos.", Toast.LENGTH_SHORT).show(); }
            else if (which == 2) copy(note.content);
            else if (which == 3) share(note.title, note.content);
            else confirmTrash(note);
        }).create();
        showAnimated(dialog);
    }

    private void confirmTrash(Note note) {
        if (!confirmDelete) {
            db.moveToTrash(note.id);
            reload(true);
            Toast.makeText(this, "Texto movido para a lixeira.", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Mover para a lixeira?").setMessage("Você poderá restaurar este texto depois.").setNegativeButton("Cancelar", null).setPositiveButton("Mover", (d, w) -> {
            db.moveToTrash(note.id);
            reload(true);
            Toast.makeText(this, "Texto movido para a lixeira.", Toast.LENGTH_SHORT).show();
        }).create();
        showAnimated(dialog);
    }

    private void showTrashActions(Note note) {
        String[] actions = {"Restaurar texto", "Excluir definitivamente"};
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(note.title.isEmpty() ? "Texto sem título" : note.title).setItems(actions, (d, w) -> {
            if (w == 0) {
                db.restore(note.id);
                reload(true);
                Toast.makeText(this, "Texto restaurado.", Toast.LENGTH_SHORT).show();
            } else if (confirmDelete) confirmPermanentDelete(note);
            else {
                db.deletePermanently(note.id);
                reload(true);
                Toast.makeText(this, "Texto excluído definitivamente.", Toast.LENGTH_SHORT).show();
            }
        }).create();
        showAnimated(dialog);
    }

    private void confirmPermanentDelete(Note note) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Excluir definitivamente?").setMessage("Depois disso, o texto não poderá ser recuperado pela lixeira.").setNegativeButton("Cancelar", null).setPositiveButton("Excluir", (x, y) -> {
            db.deletePermanently(note.id);
            reload(true);
            Toast.makeText(this, "Texto excluído definitivamente.", Toast.LENGTH_SHORT).show();
        }).create();
        showAnimated(dialog);
    }

    private void showSettings() {
        settingsButton.setBackgroundColor(ACTIVE_DARK);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(6));

        TextView intro = new TextView(this);
        intro.setText("Personalize o Script Guard");
        intro.setTextSize(15);
        intro.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        intro.setPadding(0, 0, 0, dp(6));
        box.addView(intro);

        CheckBox effects = new CheckBox(this);
        effects.setText("Efeitos mais marcados");
        effects.setChecked(strongEffects);
        box.addView(effects);

        CheckBox confirm = new CheckBox(this);
        confirm.setText("Confirmar antes de excluir ou mover para lixeira");
        confirm.setChecked(confirmDelete);
        box.addView(confirm);

        CheckBox preview = new CheckBox(this);
        preview.setText("Mostrar prévia do texto na tela inicial");
        preview.setChecked(showPreview);
        box.addView(preview);

        Button backup = smallButton("Fazer backup");
        Button restore = smallButton("Restaurar backup");
        Button empty = smallButton("Esvaziar lixeira");
        box.addView(backup, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        box.addView(restore, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        box.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        TextView version = new TextView(this);
        version.setText("Script Guard 1.6");
        version.setGravity(Gravity.CENTER);
        version.setTextColor(Color.GRAY);
        version.setPadding(0, dp(8), 0, 0);
        box.addView(version);

        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Configurações").setView(box).setNegativeButton("Fechar", null).setPositiveButton("Salvar", (d, w) -> {
            strongEffects = effects.isChecked();
            confirmDelete = confirm.isChecked();
            showPreview = preview.isChecked();
            prefs.edit().putBoolean("strong_effects", strongEffects).putBoolean("confirm_delete", confirmDelete).putBoolean("show_preview", showPreview).apply();
            updateNavigation();
            reload(true);
            Toast.makeText(this, "Configurações salvas.", Toast.LENGTH_SHORT).show();
        }).create();
        dialog.setOnShowListener(x -> animateDialog(dialog));
        dialog.setOnDismissListener(x -> settingsButton.setBackgroundColor(PRIMARY));
        backup.setOnClickListener(v -> { dialog.dismiss(); createBackup(); });
        restore.setOnClickListener(v -> { dialog.dismiss(); chooseBackup(); });
        empty.setOnClickListener(v -> { dialog.dismiss(); confirmEmptyTrash(); });
        dialog.show();
    }

    private void confirmEmptyTrash() {
        if (!confirmDelete) {
            db.emptyTrash();
            reload(true);
            Toast.makeText(this, "Lixeira esvaziada.", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Esvaziar lixeira?").setMessage("Todos os textos da lixeira serão excluídos definitivamente.").setNegativeButton("Cancelar", null).setPositiveButton("Esvaziar", (d, w) -> {
            db.emptyTrash();
            reload(true);
            Toast.makeText(this, "Lixeira esvaziada.", Toast.LENGTH_SHORT).show();
        }).create();
        showAnimated(dialog);
    }

    private void createBackup() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "script-guard-backup.json");
        startActivityForResult(i, CREATE_BACKUP);
    }

    private void chooseBackup() {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Restaurar backup?").setMessage("A restauração substituirá os textos atuais pelos textos do arquivo de backup.").setNegativeButton("Cancelar", null).setPositiveButton("Escolher arquivo", (d, w) -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            startActivityForResult(i, OPEN_BACKUP);
        }).create();
        showAnimated(dialog);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == CREATE_BACKUP) {
                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                    out.write(db.exportJson().getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "Backup salvo com sucesso.", Toast.LENGTH_LONG).show();
            } else if (requestCode == OPEN_BACKUP) {
                StringBuilder s = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) s.append(line).append('\n');
                }
                int count = db.importJson(s.toString());
                section = SECTION_TEXTS;
                selectedList = null;
                listFilterButton.setText("Listas: Todas");
                updateNavigation();
                reload(true);
                Toast.makeText(this, count + " textos restaurados.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível concluir: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void copy(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Texto", text));
        Toast.makeText(this, "Texto copiado.", Toast.LENGTH_SHORT).show();
    }

    private void share(String title, String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, title);
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, "Compartilhar texto"));
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setBackgroundColor(PRIMARY);
        addPressEffect(b);
        return b;
    }

    private Button smallButton(String text) {
        Button b = button(text);
        b.setTextSize(13);
        return b;
    }

    private Button navButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12);
        addPressEffect(b);
        return b;
    }

    private void addPressEffect(View v) {
        v.setOnTouchListener((view, event) -> {
            float pressed = strongEffects ? .91f : .96f;
            if (event.getAction() == MotionEvent.ACTION_DOWN) view.animate().scaleX(pressed).scaleY(pressed).setDuration(80).start();
            else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) view.animate().scaleX(1f).scaleY(1f).setDuration(strongEffects ? 160 : 100).start();
            return false;
        });
    }

    private void pulse(View v) {
        if (v == null) return;
        float scale = strongEffects ? .94f : .98f;
        v.animate().scaleX(scale).scaleY(scale).setDuration(80).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()).start();
    }

    private void showAnimated(AlertDialog dialog) {
        dialog.setOnShowListener(x -> animateDialog(dialog));
        dialog.show();
    }

    private void animateDialog(AlertDialog dialog) {
        if (dialog.getWindow() == null) return;
        View decor = dialog.getWindow().getDecorView();
        decor.setAlpha(0f);
        float start = strongEffects ? .88f : .96f;
        decor.setScaleX(start);
        decor.setScaleY(start);
        decor.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(strongEffects ? 240 : 140).setInterpolator(new DecelerateInterpolator()).start();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private class NoteAdapter extends BaseAdapter {
        final List<Note> items = new ArrayList<>();
        public int getCount() { return items.size(); }
        public Object getItem(int p) { return items.get(p); }
        public long getItemId(int p) { return items.get(p).id; }

        public View getView(int p, View cv, ViewGroup parent) {
            Note n = items.get(p);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(13), dp(14), dp(13));
            row.setBackgroundColor(Color.WHITE);

            TextView t = new TextView(MainActivity.this);
            String name = n.title.trim().isEmpty() ? "Texto sem título" : n.title.trim();
            String prefix = "";
            if (section == SECTION_HIDDEN) prefix = "◉  ";
            else if (section != SECTION_TRASH && n.favorite) prefix = "★  ";
            t.setText(prefix + name);
            t.setTextSize(18);
            t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            t.setTextColor(TEXT_DARK);
            row.addView(t);

            if (showPreview && !n.content.trim().isEmpty()) {
                TextView preview = new TextView(MainActivity.this);
                String s = n.content.replace('\n', ' ').trim();
                if (s.length() > 110) s = s.substring(0, 110) + "…";
                preview.setText(s);
                preview.setTextSize(14);
                preview.setTextColor(Color.DKGRAY);
                preview.setPadding(0, dp(4), 0, dp(4));
                row.addView(preview);
            }

            TextView meta = new TextView(MainActivity.this);
            String cat = n.category.trim().isEmpty() ? "Sem lista" : n.category;
            String area = section == SECTION_HIDDEN ? "Oculto • " : (section == SECTION_TRASH ? "Lixeira • " : "");
            meta.setText(area + cat + " • " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(n.updatedAt)));
            meta.setTextSize(12);
            meta.setTextColor(section == SECTION_HIDDEN ? ACTIVE_DARK : Color.GRAY);
            row.addView(meta);

            row.setAlpha(0f);
            row.setTranslationY(dp(strongEffects ? 16 : 7));
            row.setScaleX(strongEffects ? .97f : .99f);
            row.setScaleY(strongEffects ? .97f : .99f);
            row.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setStartDelay(Math.min(p * (strongEffects ? 35 : 15), 210)).setDuration(strongEffects ? 250 : 150).setInterpolator(new DecelerateInterpolator()).start();
            return row;
        }
    }
}
