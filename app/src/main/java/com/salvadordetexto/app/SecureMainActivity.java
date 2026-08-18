package com.salvadordetexto.app;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class SecureMainActivity extends MainActivity {
    private static final int SECTION_TEXTS = 0;
    private static final int SECTION_FAVORITES = 1;
    private static final int SECTION_HIDDEN = 2;
    private static final int SECTION_TRASH = 3;
    private static final int PRIMARY = Color.rgb(52, 87, 213);

    private SharedPreferences settings;
    private AppSecurity appSecurity;
    private SecurityController securityController;
    private boolean appUnlocked = false;
    private boolean unlockPromptVisible = false;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = getSharedPreferences("script_guard_settings", MODE_PRIVATE);
        appSecurity = new AppSecurity(this);
        securityController = new SecurityController(this, settings, appSecurity);
        installSecureNavigation();
        updateVersionLabel();
        securityController.maybeOfferInitialSetup(() -> {
            appUnlocked = true;
            updateVersionLabel();
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (securityController == null) return;
        if (!securityController.isAppLockEnabled()) {
            appUnlocked = true;
            return;
        }
        if (!appUnlocked && !unlockPromptVisible) {
            unlockPromptVisible = true;
            securityController.requestAppUnlock(() -> {
                unlockPromptVisible = false;
                appUnlocked = true;
                updateVersionLabel();
            }, () -> {
                unlockPromptVisible = false;
                finish();
            });
        }
    }

    @Override protected void onStop() {
        if (securityController != null && securityController.isAppLockEnabled()) appUnlocked = false;
        if (securityController != null && securityController.isHiddenLockEnabled() && getCurrentSection() == SECTION_HIDDEN) {
            invokeSetSection(SECTION_TEXTS);
        }
        super.onStop();
    }

    private void installSecureNavigation() {
        Button texts = getButtonField("navTexts");
        Button favorites = getButtonField("navFavorites");
        Button hidden = getButtonField("navHidden");
        Button trash = getButtonField("navTrash");
        Button settingsButton = getButtonField("settingsButton");

        if (texts != null) texts.setOnClickListener(v -> { invokeSetSection(SECTION_TEXTS); updateVersionLabel(); });
        if (favorites != null) favorites.setOnClickListener(v -> { invokeSetSection(SECTION_FAVORITES); updateVersionLabel(); });
        if (trash != null) trash.setOnClickListener(v -> { invokeSetSection(SECTION_TRASH); updateVersionLabel(); });
        if (hidden != null) hidden.setOnClickListener(v -> openHiddenSecurely());
        if (settingsButton != null) settingsButton.setOnClickListener(v -> showCombinedSettings());
    }

    private void openHiddenSecurely() {
        if (getCurrentSection() == SECTION_HIDDEN) return;
        securityController.requestHiddenUnlock(() -> {
            invokeSetSection(SECTION_HIDDEN);
            updateVersionLabel();
        });
    }

    private void showCombinedSettings() {
        boolean strongEffects = settings.getBoolean("strong_effects", true);
        boolean confirmDelete = settings.getBoolean("confirm_delete", true);
        boolean showPreview = settings.getBoolean("show_preview", true);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(6));

        TextView intro = new TextView(this);
        intro.setText("Personalize e proteja o Script Guard");
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

        Button security = actionButton("🔒 Segurança e senhas");
        Button backup = actionButton("Fazer backup");
        Button restore = actionButton("Restaurar backup");
        Button empty = actionButton("Esvaziar lixeira");
        box.addView(security, buttonParams());
        box.addView(backup, buttonParams());
        box.addView(restore, buttonParams());
        box.addView(empty, buttonParams());

        TextView version = new TextView(this);
        version.setText("Script Guard 1.7");
        version.setGravity(Gravity.CENTER);
        version.setTextColor(Color.GRAY);
        version.setPadding(0, dp(8), 0, 0);
        box.addView(version);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Configurações")
                .setView(box)
                .setNegativeButton("Fechar", null)
                .setPositiveButton("Salvar", (d, w) -> {
                    settings.edit()
                            .putBoolean("strong_effects", effects.isChecked())
                            .putBoolean("confirm_delete", confirm.isChecked())
                            .putBoolean("show_preview", preview.isChecked())
                            .apply();
                    setBooleanField("strongEffects", effects.isChecked());
                    setBooleanField("confirmDelete", confirm.isChecked());
                    setBooleanField("showPreview", preview.isChecked());
                    invokePrivate("updateNavigation", new Class<?>[0]);
                    invokePrivate("reload", new Class<?>[]{boolean.class}, true);
                    updateVersionLabel();
                    Toast.makeText(this, "Configurações salvas.", Toast.LENGTH_SHORT).show();
                })
                .create();

        security.setOnClickListener(v -> {
            dialog.dismiss();
            securityController.showSecuritySettings(() -> {
                appUnlocked = true;
                updateVersionLabel();
            });
        });
        backup.setOnClickListener(v -> { dialog.dismiss(); invokePrivate("createBackup", new Class<?>[0]); });
        restore.setOnClickListener(v -> { dialog.dismiss(); invokePrivate("chooseBackup", new Class<?>[0]); });
        empty.setOnClickListener(v -> { dialog.dismiss(); invokePrivate("confirmEmptyTrash", new Class<?>[0]); });
        dialog.show();
    }

    private Button actionButton(String text) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(PRIMARY);
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
    }

    private void updateVersionLabel() {
        TextView label = getTextViewField("sectionTitle");
        if (label == null) return;
        String area = "Textos";
        int section = getCurrentSection();
        if (section == SECTION_FAVORITES) area = "Favoritos";
        else if (section == SECTION_HIDDEN) area = "Ocultos protegidos";
        else if (section == SECTION_TRASH) area = "Lixeira";
        String lock = securityController != null && securityController.isAppLockEnabled() ? "  •  🔒" : "";
        label.setText("v1.7  •  " + area + lock);
    }

    private int getCurrentSection() {
        try {
            Field f = MainActivity.class.getDeclaredField("section");
            f.setAccessible(true);
            return f.getInt(this);
        } catch (Exception e) {
            return SECTION_TEXTS;
        }
    }

    private Button getButtonField(String name) {
        try {
            Field f = MainActivity.class.getDeclaredField(name);
            f.setAccessible(true);
            return (Button) f.get(this);
        } catch (Exception e) {
            return null;
        }
    }

    private TextView getTextViewField(String name) {
        try {
            Field f = MainActivity.class.getDeclaredField(name);
            f.setAccessible(true);
            return (TextView) f.get(this);
        } catch (Exception e) {
            return null;
        }
    }

    private void setBooleanField(String name, boolean value) {
        try {
            Field f = MainActivity.class.getDeclaredField(name);
            f.setAccessible(true);
            f.setBoolean(this, value);
        } catch (Exception ignored) {}
    }

    private void invokeSetSection(int section) {
        invokePrivate("setSection", new Class<?>[]{int.class}, section);
    }

    private Object invokePrivate(String name, Class<?>[] types, Object... args) {
        try {
            Method m = MainActivity.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(this, args);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir esta opção.", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
