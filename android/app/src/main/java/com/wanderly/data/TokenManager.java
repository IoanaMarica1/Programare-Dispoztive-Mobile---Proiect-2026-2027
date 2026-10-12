package com.wanderly.data;

import android.content.Context;
import android.content.SharedPreferences;

/** Saves the users token so that the session does not dissconect after register/login */
public class TokenManager {

    private static final String PREFS_NAME = "wanderly_prefs";
    private static final String KEY_TOKEN = "jwt_token";

    private final SharedPreferences prefs;

    public TokenManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public boolean isLoggedIn() {
        return getToken() != null;
    }

    public void clear() {
        prefs.edit().remove(KEY_TOKEN).apply();
    }
}