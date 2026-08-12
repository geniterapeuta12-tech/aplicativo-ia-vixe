package com.salvadordetexto.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {
    private static final int PRIMARY = Color.rgb(52, 87, 213);
    private NoteDbHelper db;
    private EditText search;
    private CheckBox favorites;
    private ListView list;
    private final NoteAdapter adapter = new NoteAdapter();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = new NoteDbHelper(this);
        setContentView(buildUi());
        reload();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.setBackgroundColor(Color.rgb(247,248,252));

        TextView title = new TextView(this);
        title.setText("Salvador de Texto");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(32,33,36));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Seus textos ficam salvos neste aparelho");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setPadding(0, dp(2), 0, dp(12));
        root.addView(subtitle);

        search = new EditText(this);
        search.setHint("Pesquisar por título, texto ou categoria");
        search.setSingleLine(true);
        search.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        favorites = new CheckBox(this);
        favorites.setText("Mostrar somente favoritos");
        favorites.setPadding(0, dp(4), 0, dp(4));
        root.addView(favorites);

        Button add = button("+ Novo texto");
        root.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        list = new ListView(this);
        list.setAdapter(adapter);
        list.setDividerHeight(1);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { reload(); }
            public void afterTextChanged(Editable e) {}
        });
        favorites.setOnCheckedChangeListener((b, checked) -> reload());
        add.setOnClickListener(v -> openEditor(new Note(0,"","","Pessoal",false,0,0)));
        list.setOnItemClickListener((p,v,pos,id) -> openEditor(adapter.items.get(pos)));
        list.setOnItemLongClickListener((p,v,pos,id) -> { showActions(adapter.items.get(pos)); return true; });
        return root;
    }

    private void reload() {
        if (db == null || search == null || favorites == null) return;
        adapter.items.clear();
        adapter.items.addAll(db.search(search.getText().toString(), favorites.isChecked()));
        adapter.notifyDataSetChanged();
    }

    private void openEditor(Note note) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), 0);

        EditText title = new EditText(this);
        title.setHint("Título");
        title.setText(note.title);
        box.addView(title);

        EditText category = new EditText(this);
        category.setHint("Categoria");
        category.setText(note.category);
        box.addView(category);

        CheckBox favorite = new CheckBox(this);
        favorite.setText("Favorito");
        favorite.setChecked(note.favorite);
        box.addView(favorite);

        EditText content = new EditText(this);
        content.setHint("Digite ou cole seu texto aqui");
        content.setGravity(Gravity.TOP);
        content.setMinLines(10);
        content.setText(note.content);
        box.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(note.id == 0 ? "Novo texto" : "Editar texto")
                .setView(box)
                .setPositiveButton("Salvar", null)
                .setNegativeButton("Fechar", null)
                .setNeutralButton("Compartilhar", null)
                .create();
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                note.title = title.getText().toString();
                note.category = category.getText().toString();
                note.content = content.getText().toString();
                note.favorite = favorite.isChecked();
                if (note.title.trim().isEmpty() && note.content.trim().isEmpty()) {
                    Toast.makeText(this,"Digite um título ou texto.",Toast.LENGTH_SHORT).show();
                    return;
                }
                db.save(note);
                reload();
                Toast.makeText(this,"Texto salvo.",Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> dialog.dismiss());
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> share(title.getText().toString(), content.getText().toString()));
        });
        dialog.show();
    }

    private void showActions(Note note) {
        String fav = note.favorite ? "Remover dos favoritos" : "Adicionar aos favoritos";
        String[] actions = {"Abrir", fav, "Copiar", "Compartilhar", "Duplicar", "Excluir"};
        new AlertDialog.Builder(this).setTitle(note.title.isEmpty()?"Texto sem título":note.title)
                .setItems(actions,(d,which)-> {
                    if (which == 0) openEditor(note);
                    else if (which == 1) { note.favorite=!note.favorite; db.save(note); reload(); }
                    else if (which == 2) copy(note.content);
                    else if (which == 3) share(note.title,note.content);
                    else if (which == 4) { db.duplicate(note); reload(); Toast.makeText(this,"Cópia criada.",Toast.LENGTH_SHORT).show(); }
                    else confirmDelete(note);
                }).show();
    }

    private void confirmDelete(Note note) {
        new AlertDialog.Builder(this).setTitle("Excluir texto?")
                .setMessage("Esta ação removerá o texto do aparelho.")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Excluir",(d,w)-> { db.delete(note.id); reload(); Toast.makeText(this,"Texto excluído.",Toast.LENGTH_SHORT).show(); })
                .show();
    }

    private void copy(String text) {
        ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Texto",text));
        Toast.makeText(this,"Texto copiado.",Toast.LENGTH_SHORT).show();
    }

    private void share(String title,String text) {
        Intent i=new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT,title);
        i.putExtra(Intent.EXTRA_TEXT,text);
        startActivity(Intent.createChooser(i,"Compartilhar texto"));
    }

    private Button button(String text) {
        Button b=new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setBackgroundColor(PRIMARY);
        return b;
    }

    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }

    private class NoteAdapter extends BaseAdapter {
        final List<Note> items=new ArrayList<>();
        public int getCount(){return items.size();}
        public Object getItem(int p){return items.get(p);}
        public long getItemId(int p){return items.get(p).id;}
        public View getView(int p,View cv,ViewGroup parent){
            Note n=items.get(p);
            LinearLayout row=new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14),dp(12),dp(14),dp(12));
            row.setBackgroundColor(Color.WHITE);
            TextView t=new TextView(MainActivity.this);
            String name=n.title.trim().isEmpty()?"Texto sem título":n.title.trim();
            t.setText((n.favorite?"★  ":"")+name);
            t.setTextSize(18); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setTextColor(Color.rgb(32,33,36));
            row.addView(t);
            if(!n.content.trim().isEmpty()){
                TextView preview=new TextView(MainActivity.this);
                String s=n.content.replace('\n',' ').trim(); if(s.length()>110)s=s.substring(0,110)+"…";
                preview.setText(s); preview.setTextSize(14); preview.setTextColor(Color.DKGRAY); preview.setPadding(0,dp(4),0,dp(4));
                row.addView(preview);
            }
            TextView meta=new TextView(MainActivity.this);
            String cat=n.category.trim().isEmpty()?"Sem categoria":n.category;
            meta.setText(cat+" • "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(n.updatedAt)));
            meta.setTextSize(12); meta.setTextColor(Color.GRAY);
            row.addView(meta);
            return row;
        }
    }
}
