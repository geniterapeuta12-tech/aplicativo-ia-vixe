package com.salvadordetexto.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class AppSecurity {
    private static final String PREFS = "script_guard_security";
    private static final int ITERATIONS = 120000;
    private static final int KEY_LENGTH = 256;

    private final SharedPreferences prefs;

    public AppSecurity(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean hasMainPassword() {
        return prefs.contains("main_hash") && prefs.contains("main_salt");
    }

    public boolean hasHiddenPassword() {
        return prefs.contains("hidden_hash") && prefs.contains("hidden_salt");
    }

    public boolean setMainPassword(String password) {
        return savePassword("main", password);
    }

    public boolean setHiddenPassword(String password) {
        return savePassword("hidden", password);
    }

    public boolean verifyMainPassword(String password) {
        return verifyPassword("main", password);
    }

    public boolean verifyHiddenPassword(String password) {
        return verifyPassword("hidden", password);
    }

    public void clearHiddenPassword() {
        prefs.edit().remove("hidden_hash").remove("hidden_salt").apply();
    }

    private boolean savePassword(String prefix, String password) {
        if (password == null || password.length() < 4) return false;
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            byte[] hash = derive(password, salt);
            prefs.edit()
                    .putString(prefix + "_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString(prefix + "_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
                    .apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean verifyPassword(String prefix, String password) {
        try {
            String saltText = prefs.getString(prefix + "_salt", null);
            String hashText = prefs.getString(prefix + "_hash", null);
            if (saltText == null || hashText == null || password == null) return false;
            byte[] salt = Base64.decode(saltText, Base64.NO_WRAP);
            byte[] expected = Base64.decode(hashText, Base64.NO_WRAP);
            byte[] actual = derive(password, salt);
            if (expected.length != actual.length) return false;
            int diff = 0;
            for (int i = 0; i < expected.length; i++) diff |= expected[i] ^ actual[i];
            return diff == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] derive(String password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return factory.generateSecret(spec).getEncoded();
    }
}
