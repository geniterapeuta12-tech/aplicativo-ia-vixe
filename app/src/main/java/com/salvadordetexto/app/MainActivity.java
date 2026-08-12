package com.salvadordetexto.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
    private static final int CREATE_BACKUP = 1001;
    private static final int OPEN_BACKUP = 1002;
    private NoteDbHelper db;
    private EditText search;
    private CheckBox favorites;
    private ListView list;
    private Button listFilterButton;
    private boolean trashMode = false;
    private String selectedList = null;
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
        title.setText("Script Guard");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(32,33,36));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Seus textos ficam salvos neste aparelho");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setPadding(0, dp(2), 0, dp(10));
        root.addView(subtitle);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        Button trash = smallButton("Lixeira");
        Button backup = smallButton("Backup");
        Button restore = smallButton("Restaurar");
        tools.addView(trash, new LinearLayout.LayoutParams(0, dp(48), 1f));
        tools.addView(backup, new LinearLayout.LayoutParams(0, dp(48), 1f));
        tools.addView(restore, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(tools);

        listFilterButton = smallButton("Listas: Todas");
        root.addView(listFilterButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        search = new EditText(this);
        search.setHint("Pesquisar por título, texto ou lista");
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
        listFilterButton.setOnClickListener(v -> showListsDialog());
        add.setOnClickListener(v -> {
            if (trashMode) {
                trashMode = false;
                favorites.setEnabled(true);
                listFilterButton.setEnabled(true);
                add.setText("+ Novo texto");
                reload();
            } else openEditor(new Note(0,"","", selectedList == null ? "Pessoal" : selectedList,false,0,0));
        });
        trash.setOnClickListener(v -> {
            trashMode = !trashMode;
            favorites.setEnabled(!trashMode);
            listFilterButton.setEnabled(!trashMode);
            favorites.setChecked(false);
            add.setText(trashMode ? "← Voltar aos textos" : "+ Novo texto");
            trash.setText(trashMode ? "Na lixeira" : "Lixeira");
            reload();
        });
        backup.setOnClickListener(v -> createBackup());
        restore.setOnClickListener(v -> chooseBackup());
        list.setOnItemClickListener((p,v,pos,id) -> {
            Note n = adapter.items.get(pos);
            if (trashMode) showTrashActions(n); else openEditor(n);
        });
        list.setOnItemLongClickListener((p,v,pos,id) -> {
            Note n = adapter.items.get(pos);
            if (trashMode) showTrashActions(n); else showActions(n);
            return true;
        });
        return root;
    }

    private void showListsDialog() {
        List<String> names = new ArrayList<>();
        names.add("Todas as listas");
        names.addAll(db.getLists());
        names.add("+ Criar nova lista");
        new AlertDialog.Builder(this).setTitle("Listas").setItems(names.toArray(new String[0]), (d, which) -> {
            if (which == 0) {
                selectedList = null;
                listFilterButton.setText("Listas: Todas");
                reload();
            } else if (which == names.size() - 1) {
                createListDialog();
            } else {
                selectedList = names.get(which);
                listFilterButton.setText("Lista: " + selectedList);
                reload();
            }
        }).show();
    }

    private void createListDialog() {
        EditText input = new EditText(this);
        input.setHint("Nome da lista");
        input.setSingleLine(true);
        int p = dp(20);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(p, dp(6), p, 0);
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this).setTitle("Nova lista").setView(box).setNegativeButton("Cancelar", null).setPositiveButton("Criar", (d,w) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) { Toast.makeText(this,"Digite um nome para a lista.",Toast.LENGTH_SHORT).show(); return; }
            db.addList(name);
            selectedList = name;
            listFilterButton.setText("Lista: " + name);
            reload();
            Toast.makeText(this,"Lista criada.",Toast.LENGTH_SHORT).show();
        }).show();
    }

    private void reload() {
        if (db == null || search == null || favorites == null) return;
        adapter.items.clear();
        if (trashMode) adapter.items.addAll(db.searchTrash(search.getText().toString()));
        else adapter.items.addAll(db.search(search.getText().toString(), favorites.isChecked(), selectedList));
        adapter.notifyDataSetChanged();
    }

    private void openEditor(Note note) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), 0);
        EditText title = new EditText(this); title.setHint("Título"); title.setText(note.title); box.addView(title);

        TextView listLabel = new TextView(this); listLabel.setText("Lista"); listLabel.setTextSize(13); listLabel.setTextColor(Color.DKGRAY); listLabel.setPadding(0,dp(6),0,0); box.addView(listLabel);
        List<String> lists = new ArrayList<>(db.getLists());
        if (note.category != null && !note.category.trim().isEmpty() && !containsIgnoreCase(lists,note.category.trim())) lists.add(note.category.trim());
        if (lists.isEmpty()) lists.add("Pessoal");
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, lists);
        spinner.setAdapter(spinnerAdapter);
        int selected = 0;
        for (int i=0;i<lists.size();i++) if (note.category != null && lists.get(i).equalsIgnoreCase(note.category.trim())) { selected=i; break; }
        spinner.setSelection(selected);
        box.addView(spinner);

        Button newList = smallButton("+ Nova lista");
        box.addView(newList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        CheckBox favorite = new CheckBox(this); favorite.setText("Favorito"); favorite.setChecked(note.favorite); box.addView(favorite);
        EditText content = new EditText(this); content.setHint("Digite ou cole seu texto aqui"); content.setGravity(Gravity.TOP); content.setMinLines(10); content.setText(note.content);
        box.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(280)));

        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(note.id == 0 ? "Novo texto" : "Editar texto").setView(box).setPositiveButton("Salvar", null).setNegativeButton("Fechar", null).setNeutralButton("Compartilhar", null).create();
        dialog.setOnShowListener(x -> {
            newList.setOnClickListener(v -> {
                EditText input = new EditText(this); input.setHint("Nome da nova lista");
                new AlertDialog.Builder(this).setTitle("Criar lista").setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Criar",(a,b)->{
                    String name=input.getText().toString().trim();
                    if(!name.isEmpty()){
                        db.addList(name); lists.add(name); spinnerAdapter.notifyDataSetChanged(); spinner.setSelection(lists.size()-1);
                    }
                }).show();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                note.title=title.getText().toString(); note.category=(String)spinner.getSelectedItem(); note.content=content.getText().toString(); note.favorite=favorite.isChecked();
                if(note.title.trim().isEmpty()&&note.content.trim().isEmpty()){Toast.makeText(this,"Digite um título ou texto.",Toast.LENGTH_SHORT).show();return;}
                db.save(note); reload(); Toast.makeText(this,"Texto salvo.",Toast.LENGTH_SHORT).show(); dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> dialog.dismiss());
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> share(title.getText().toString(),content.getText().toString()));
        }); dialog.show();
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        for (String value : values) if (value.equalsIgnoreCase(target)) return true;
        return false;
    }

    private void showActions(Note note) {
        String fav=note.favorite?"Remover dos favoritos":"Adicionar aos favoritos";
        String[] actions={"Abrir",fav,"Copiar","Compartilhar","Duplicar","Mover para lixeira"};
        new AlertDialog.Builder(this).setTitle(note.title.isEmpty()?"Texto sem título":note.title).setItems(actions,(d,which)->{
            if(which==0)openEditor(note); else if(which==1){note.favorite=!note.favorite;db.save(note);reload();}
            else if(which==2)copy(note.content); else if(which==3)share(note.title,note.content);
            else if(which==4){db.duplicate(note);reload();Toast.makeText(this,"Cópia criada.",Toast.LENGTH_SHORT).show();}
            else confirmTrash(note);
        }).show();
    }

    private void confirmTrash(Note note) {
        new AlertDialog.Builder(this).setTitle("Mover para a lixeira?").setMessage("Você poderá restaurar este texto depois.").setNegativeButton("Cancelar",null).setPositiveButton("Mover",(d,w)->{db.moveToTrash(note.id);reload();Toast.makeText(this,"Texto movido para a lixeira.",Toast.LENGTH_SHORT).show();}).show();
    }

    private void showTrashActions(Note note) {
        new AlertDialog.Builder(this).setTitle(note.title.isEmpty()?"Texto sem título":note.title).setItems(new String[]{"Restaurar texto","Excluir definitivamente"},(d,w)->{
            if(w==0){db.restore(note.id);reload();Toast.makeText(this,"Texto restaurado.",Toast.LENGTH_SHORT).show();}
            else new AlertDialog.Builder(this).setTitle("Excluir definitivamente?").setMessage("Depois disso, o texto não poderá ser recuperado pela lixeira.").setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(x,y)->{db.deletePermanently(note.id);reload();Toast.makeText(this,"Texto excluído definitivamente.",Toast.LENGTH_SHORT).show();}).show();
        }).show();
    }

    private void createBackup() {
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json"); i.putExtra(Intent.EXTRA_TITLE,"script-guard-backup.json"); startActivityForResult(i,CREATE_BACKUP);
    }

    private void chooseBackup() {
        new AlertDialog.Builder(this).setTitle("Restaurar backup?").setMessage("A restauração substituirá os textos atuais pelos textos do arquivo de backup. Faça um backup atual antes, se necessário.").setNegativeButton("Cancelar",null).setPositiveButton("Escolher arquivo",(d,w)->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json"); startActivityForResult(i,OPEN_BACKUP);
        }).show();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{
            if(requestCode==CREATE_BACKUP){
                try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){out.write(db.exportJson().getBytes(StandardCharsets.UTF_8));}
                Toast.makeText(this,"Backup salvo com sucesso.",Toast.LENGTH_LONG).show();
            }else if(requestCode==OPEN_BACKUP){
                StringBuilder s=new StringBuilder();
                try(BufferedReader r=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)s.append(line).append('\n');}
                int count=db.importJson(s.toString()); trashMode=false; selectedList=null; favorites.setEnabled(true); listFilterButton.setEnabled(true); listFilterButton.setText("Listas: Todas"); reload();
                Toast.makeText(this,count+" textos restaurados.",Toast.LENGTH_LONG).show();
            }
        }catch(Exception e){Toast.makeText(this,"Não foi possível concluir: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void copy(String text){ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("Texto",text));Toast.makeText(this,"Texto copiado.",Toast.LENGTH_SHORT).show();}
    private void share(String title,String text){Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_SUBJECT,title);i.putExtra(Intent.EXTRA_TEXT,text);startActivity(Intent.createChooser(i,"Compartilhar texto"));}
    private Button button(String text){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(16);b.setBackgroundColor(PRIMARY);return b;}
    private Button smallButton(String text){Button b=button(text);b.setTextSize(13);return b;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private class NoteAdapter extends BaseAdapter {
        final List<Note> items=new ArrayList<>(); public int getCount(){return items.size();} public Object getItem(int p){return items.get(p);} public long getItemId(int p){return items.get(p).id;}
        public View getView(int p,View cv,ViewGroup parent){
            Note n=items.get(p); LinearLayout row=new LinearLayout(MainActivity.this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(dp(14),dp(12),dp(14),dp(12)); row.setBackgroundColor(Color.WHITE);
            TextView t=new TextView(MainActivity.this); String name=n.title.trim().isEmpty()?"Texto sem título":n.title.trim(); t.setText((!trashMode&&n.favorite?"★  ":"")+name); t.setTextSize(18);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setTextColor(Color.rgb(32,33,36));row.addView(t);
            if(!n.content.trim().isEmpty()){TextView preview=new TextView(MainActivity.this);String s=n.content.replace('\n',' ').trim();if(s.length()>110)s=s.substring(0,110)+"…";preview.setText(s);preview.setTextSize(14);preview.setTextColor(Color.DKGRAY);preview.setPadding(0,dp(4),0,dp(4));row.addView(preview);}
            TextView meta=new TextView(MainActivity.this);String cat=n.category.trim().isEmpty()?"Sem lista":n.category;meta.setText((trashMode?"Lixeira • ":"")+cat+" • "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(n.updatedAt)));meta.setTextSize(12);meta.setTextColor(Color.GRAY);row.addView(meta);return row;
        }
    }
}
