package com.geni.htmlbuilder;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQ_SAVE = 1001;
    private static final int REQ_SAVE_OPEN = 1002;

    private EditText fileName;
    private EditText code;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, float sizeSp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sizeSp);
        t.setTextColor(Color.rgb(28, 35, 50));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setMinHeight(dp(48));
        return b;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(14));
        root.setBackgroundColor(Color.rgb(247, 249, 252));

        TextView title = text("HTML Builder", 27, true);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = text("Cole seu código HTML, salve o arquivo e abra no navegador.", 14, false);
        subtitle.setTextColor(Color.rgb(93, 103, 120));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(4);
        subParams.bottomMargin = dp(14);
        root.addView(subtitle, subParams);

        TextView nameLabel = text("Nome do arquivo", 13, true);
        root.addView(nameLabel, new LinearLayout.LayoutParams(-1, -2));

        fileName = new EditText(this);
        fileName.setSingleLine(true);
        fileName.setHint("pagina.html");
        fileName.setText("pagina.html");
        fileName.setTextSize(16);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, dp(52));
        nameParams.bottomMargin = dp(10);
        root.addView(fileName, nameParams);

        TextView codeLabel = text("Código HTML", 13, true);
        root.addView(codeLabel, new LinearLayout.LayoutParams(-1, -2));

        code = new EditText(this);
        code.setGravity(Gravity.TOP | Gravity.START);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextSize(13);
        code.setPadding(dp(12), dp(12), dp(12), dp(12));
        code.setHorizontallyScrolling(true);
        code.setHorizontalScrollBarEnabled(true);
        code.setVerticalScrollBarEnabled(true);
        code.setText("<!doctype html>\n<html lang=\"pt-BR\">\n<head>\n  <meta charset=\"utf-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n  <title>Minha página</title>\n</head>\n<body>\n  <h1>Olá!</h1>\n  <p>Seu HTML está funcionando.</p>\n</body>\n</html>");
        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        codeParams.topMargin = dp(4);
        codeParams.bottomMargin = dp(10);
        root.addView(code, codeParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);

        Button save = button("Salvar .HTML");
        Button saveOpen = button("Salvar e abrir");
        Button clear = button("Limpar");

        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(52), 1f);
        p1.rightMargin = dp(6);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(52), 1f);
        p2.leftMargin = dp(3);
        p2.rightMargin = dp(3);
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(0, dp(52), 0.72f);
        p3.leftMargin = dp(6);

        actions.addView(save, p1);
        actions.addView(saveOpen, p2);
        actions.addView(clear, p3);
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        TextView tip = text("Dica: para páginas completas, deixe CSS e JavaScript dentro do próprio HTML ou use links web.", 11, false);
        tip.setTextColor(Color.rgb(105, 115, 130));
        LinearLayout.LayoutParams tipParams = new LinearLayout.LayoutParams(-1, -2);
        tipParams.topMargin = dp(8);
        root.addView(tip, tipParams);

        save.setOnClickListener(v -> chooseDestination(REQ_SAVE));
        saveOpen.setOnClickListener(v -> chooseDestination(REQ_SAVE_OPEN));
        clear.setOnClickListener(v -> code.setText(""));

        setContentView(root);
    }

    private String normalizedName() {
        String name = fileName.getText().toString().trim();
        if (name.isEmpty()) name = "pagina.html";
        if (!name.toLowerCase().endsWith(".html") && !name.toLowerCase().endsWith(".htm")) {
            name += ".html";
        }
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
        if ((requestCode != REQ_SAVE && requestCode != REQ_SAVE_OPEN) || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) return;

        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new Exception("Não foi possível criar o arquivo.");
            out.write(code.getText().toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            Toast.makeText(this, "HTML salvo com sucesso.", Toast.LENGTH_SHORT).show();

            if (requestCode == REQ_SAVE_OPEN) {
                openInBrowser(uri);
            }
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
}
