package com.wanderly.ui.common;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import com.wanderly.R;
import com.wanderly.data.local.TokenManager;
import com.wanderly.ui.auth.LoginActivity;

/** Sends the traveler back to login when the server rejects the saved token (401). */
public final class Session {

    private Session() {
    }

    public static void expire(Activity activity) {
        Toast.makeText(activity, R.string.error_session_expired, Toast.LENGTH_LONG).show();
        new TokenManager(activity).clear();

        Intent intent = new Intent(activity, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
