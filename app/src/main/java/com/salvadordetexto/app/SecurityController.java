package com.salvadordetexto.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SecurityController {
    private final Activity activity;
    private final SharedPreferences prefs;
    private final AppSecurity security;

    public SecurityController(Activity activity, SharedPreferences prefs, AppSecurity security) {
        this.activity = activity;
        this.prefs = prefs;
        this.security = security;
    }

    public boolean isAppLockEnabled() {
        return prefs.getBoolean("app_lock_enabled", false) && security.hasMainPassword();
    }

    public boolean isHiddenLockEnabled() {
        boolean same = prefs.getBoolean("hidden_use_main", true);
        boolean hasPassword = same ? security.hasMainPassword() : security.hasHiddenPassword();
        return prefs.getBoolean("hidden_lock_enabled", false) && hasPassword;
    }

    public void maybeOfferInitialSetup(Runnable onCreated) {
        if (prefs.getBoolean("security_intro_seen", false) || security.hasMainPassword()) return;
        new AlertDialog.Builder(activity)
                .setTitle("Proteja o Script Guard")
                .setMessage("Crie uma senha ou PIN para bloquear o aplicativo e a área Ocultos. Guarde essa senha: nesta versão não há recuperação automática.")
                .setNegativeButton("Agora não", (d, w) -> prefs.edit().putBoolean("security_intro_seen", true).apply())
                .setPositiveButton("Criar senha", (d, w) -> createMainPassword(true, onCreated))
                .show();
    }

    public void requestAppUnlock(Runnable onSuccess, Runnable onCancel) {
        if (!isAppLockEnabled()) {
            onSuccess.run();
            return;
        }
        verifyDialog("Desbloquear Script Guard", "Digite sua senha ou PIN", security::verifyMainPassword, onSuccess, onCancel, false);
    }

    public void requestHiddenUnlock(Runnable onSuccess) {
        if (!isHiddenLockEnabled()) {
            onSuccess.run();
            return;
        }
        boolean same = prefs.getBoolean("hidden_use_main", true);
        Verifier verifier = same ? security::verifyMainPassword : security::verifyHiddenPassword;
        verifyDialog("Abrir Ocultos", "Digite a senha dos Ocultos", verifier, onSuccess, null, true);
    }

    public void showSecuritySettings(Runnable onChanged) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));

        TextView info = new TextView(activity);
        info.setText("Senhas ficam armazenadas de forma derivada no aparelho. O bloqueio protege a interface; ele não criptografa todo o banco de textos.");
        info.setTextSize(13);
        info.setPadding(0, 0, 0, dp(8));
        box.addView(info);

        CheckBox appLock = new CheckBox(activity);
        appLock.setText("Pedir senha ao abrir o aplicativo");
        appLock.setChecked(isAppLockEnabled());
        box.addView(appLock);

        CheckBox hiddenLock = new CheckBox(activity);
        hiddenLock.setText("Pedir senha para abrir Ocultos");
        hiddenLock.setChecked(isHiddenLockEnabled());
        box.addView(hiddenLock);

        CheckBox samePassword = new CheckBox(activity);
        samePassword.setText("Usar a mesma senha do aplicativo nos Ocultos");
        samePassword.setChecked(prefs.getBoolean("hidden_use_main", true));
        box.addView(samePassword);

        Button mainPassword = new Button(activity);
        mainPassword.setAllCaps(false);
        mainPassword.setText(security.hasMainPassword() ? "Trocar senha do aplicativo" : "Criar senha do aplicativo");
        box.addView(mainPassword, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        Button hiddenPassword = new Button(activity);
        hiddenPassword.setAllCaps(false);
        hiddenPassword.setText(security.hasHiddenPassword() ? "Trocar senha separada dos Ocultos" : "Criar senha separada dos Ocultos");
        hiddenPassword.setEnabled(!samePassword.isChecked());
        hiddenPassword.setAlpha(hiddenPassword.isEnabled() ? 1f : .45f);
        box.addView(hiddenPassword, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        TextView warning = new TextView(activity);
        warning.setText("Importante: se esquecer a senha, não há recuperação automática nesta versão.");
        warning.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        warning.setGravity(Gravity.CENTER);
        warning.setPadding(0, dp(10), 0, 0);
        box.addView(warning);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Segurança")
                .setView(box)
                .setNegativeButton("Fechar", null)
                .setPositiveButton("Salvar", null)
                .create();

        samePassword.setOnCheckedChangeListener((b, checked) -> {
            hiddenPassword.setEnabled(!checked);
            hiddenPassword.setAlpha(!checked ? 1f : .45f);
        });

        mainPassword.setOnClickListener(v -> changeMainPassword(() -> {
            mainPassword.setText("Trocar senha do aplicativo");
            appLock.setChecked(true);
            if (samePassword.isChecked()) hiddenLock.setChecked(true);
            onChanged.run();
        }));

        hiddenPassword.setOnClickListener(v -> changeHiddenPassword(() -> {
            hiddenPassword.setText("Trocar senha separada dos Ocultos");
            hiddenLock.setChecked(true);
            samePassword.setChecked(false);
            onChanged.run();
        }));

        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            boolean same = samePassword.isChecked();
            boolean enableApp = appLock.isChecked();
            boolean enableHidden = hiddenLock.isChecked();

            if (enableApp && !security.hasMainPassword()) {
                Toast.makeText(activity, "Crie uma senha do aplicativo primeiro.", Toast.LENGTH_LONG).show();
                return;
            }
            if (enableHidden && same && !security.hasMainPassword()) {
                Toast.makeText(activity, "Crie a senha do aplicativo primeiro.", Toast.LENGTH_LONG).show();
                return;
            }
            if (enableHidden && !same && !security.hasHiddenPassword()) {
                Toast.makeText(activity, "Crie a senha separada dos Ocultos primeiro.", Toast.LENGTH_LONG).show();
                return;
            }

            prefs.edit()
                    .putBoolean("app_lock_enabled", enableApp)
                    .putBoolean("hidden_lock_enabled", enableHidden)
                    .putBoolean("hidden_use_main", same)
                    .putBoolean("security_intro_seen", true)
                    .apply();
            onChanged.run();
            Toast.makeText(activity, "Segurança atualizada.", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void changeMainPassword(Runnable done) {
        if (security.hasMainPassword()) {
            verifyDialog("Confirmar senha atual", "Digite a senha atual do aplicativo", security::verifyMainPassword,
                    () -> createMainPassword(false, done), null, true);
        } else createMainPassword(false, done);
    }

    private void changeHiddenPassword(Runnable done) {
        if (security.hasHiddenPassword()) {
            verifyDialog("Confirmar senha atual", "Digite a senha atual dos Ocultos", security::verifyHiddenPassword,
                    () -> createHiddenPassword(done), null, true);
        } else createHiddenPassword(done);
    }

    private void createMainPassword(boolean firstSetup, Runnable done) {
        createPasswordDialog("Criar senha do aplicativo", security::setMainPassword, () -> {
            prefs.edit()
                    .putBoolean("app_lock_enabled", true)
                    .putBoolean("hidden_lock_enabled", true)
                    .putBoolean("hidden_use_main", true)
                    .putBoolean("security_intro_seen", true)
                    .apply();
            Toast.makeText(activity, firstSetup ? "Proteção ativada." : "Senha alterada.", Toast.LENGTH_SHORT).show();
            if (done != null) done.run();
        });
    }

    private void createHiddenPassword(Runnable done) {
        createPasswordDialog("Senha separada dos Ocultos", security::setHiddenPassword, () -> {
            prefs.edit().putBoolean("hidden_use_main", false).putBoolean("hidden_lock_enabled", true).apply();
            Toast.makeText(activity, "Senha dos Ocultos salva.", Toast.LENGTH_SHORT).show();
            if (done != null) done.run();
        });
    }

    private void createPasswordDialog(String title, Saver saver, Runnable done) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), 0);

        EditText first = passwordField("Senha ou PIN (mínimo 4 caracteres)");
        EditText second = passwordField("Repita a senha ou PIN");
        box.addView(first);
        box.addView(second);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage("Guarde sua senha. Não há recuperação automática nesta versão.")
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", null)
                .create();

        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String a = first.getText().toString();
            String b = second.getText().toString();
            if (a.length() < 4) {
                first.setError("Use pelo menos 4 caracteres.");
                return;
            }
            if (!a.equals(b)) {
                second.setError("As senhas não são iguais.");
                return;
            }
            if (!saver.save(a)) {
                Toast.makeText(activity, "Não foi possível salvar a senha.", Toast.LENGTH_LONG).show();
                return;
            }
            dialog.dismiss();
            done.run();
        }));
        dialog.show();
    }

    private void verifyDialog(String title, String hint, Verifier verifier, Runnable success, Runnable cancel, boolean cancelable) {
        EditText input = passwordField(hint);
        LinearLayout box = new LinearLayout(activity);
        box.setPadding(dp(20), dp(4), dp(20), 0);
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("Entrar", null);
        if (cancelable) builder.setNegativeButton("Cancelar", (d, w) -> { if (cancel != null) cancel.run(); });
        else builder.setNegativeButton("Fechar app", (d, w) -> { if (cancel != null) cancel.run(); });

        AlertDialog dialog = builder.create();
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = input.getText().toString();
            if (!verifier.verify(password)) {
                input.setError("Senha incorreta.");
                input.setText("");
                return;
            }
            dialog.dismiss();
            success.run();
        }));
        dialog.show();
    }

    private EditText passwordField(String hint) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return input;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private interface Verifier { boolean verify(String password); }
    private interface Saver { boolean save(String password); }
}
