package com.wanderly.ui.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wanderly.R;

/**
 * The app's dialogs: a centred icon above the title, rounded corners, no ALL CAPS buttons.
 * {@link #error} is red for things that went wrong; {@link #info} is teal for things to decide.
 */
public final class Dialogs {

    private Dialogs() {
    }

    public static MaterialAlertDialogBuilder info(Context context, @DrawableRes int icon,
                                                  CharSequence title, CharSequence message) {
        return new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Wanderly_Dialog)
                .setIcon(icon)
                .setTitle(title)
                .setMessage(message);
    }

    public static MaterialAlertDialogBuilder error(Context context, @DrawableRes int icon,
                                                   CharSequence title, CharSequence message) {
        return new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Wanderly_Dialog_Error)
                .setIcon(icon)
                .setTitle(title)
                .setMessage(message);
    }

    /** A plain error with a single "Got it". */
    public static void showError(Context context, CharSequence title, CharSequence message) {
        error(context, R.drawable.ic_error, title, message)
                .setPositiveButton(R.string.action_ok, null)
                .show();
    }

    /** The server could not be reached; {@code retry} may be null when there is nothing to resend. */
    public static void showNetworkError(Context context, Runnable retry) {
        MaterialAlertDialogBuilder builder = error(context, R.drawable.ic_cloud_off,
                context.getString(R.string.dialog_network_title),
                context.getString(R.string.dialog_network_message));
        if (retry == null) {
            builder.setPositiveButton(R.string.action_ok, null);
        } else {
            builder.setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_retry, (d, w) -> retry.run());
        }
        builder.show();
    }

    /** A dialog that can't be dismissed while something long runs; the caller dismisses it. */
    public static AlertDialog showProgress(Context context, CharSequence title, CharSequence body) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_progress, null, false);
        ((TextView) view.findViewById(R.id.progressBody)).setText(body);
        return info(context, R.drawable.ic_group, title, null)
                .setView(view)
                .setCancelable(false)
                .show();
    }
}
