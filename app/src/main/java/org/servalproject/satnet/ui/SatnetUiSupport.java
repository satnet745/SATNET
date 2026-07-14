package org.servalproject.satnet.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

final class SatnetUiSupport {
    static final long CLIPBOARD_CLEAR_DELAY_SHORT_MS = 30_000L;
    static final long CLIPBOARD_CLEAR_DELAY_MEDIUM_MS = 60_000L;
    static final long CLIPBOARD_CLEAR_DELAY_LONG_MS = 120_000L;

    private SatnetUiSupport() {
    }

    static void applySecureWindow(Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return;
        }
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    static <T extends View> T requireView(Activity activity, int viewId, Class<T> expectedType, String debugName) {
        View view = activity.findViewById(viewId);
        if (!expectedType.isInstance(view)) {
            throw new IllegalStateException("SATNET screen is missing required view: " + debugName);
        }
        return expectedType.cast(view);
    }

    static void failInitialization(Activity activity, String tag, Throwable error, String userMessage) {
        Log.e(tag, userMessage, error);
        Toast.makeText(activity, userMessage, Toast.LENGTH_LONG).show();
        activity.finish();
    }

    static boolean copySensitiveText(Activity activity, CharSequence label, CharSequence value, long clearDelayMs) {
        if (activity == null || value == null || value.length() == 0) {
            return false;
        }
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return false;
        }
        String copiedText = value.toString();
        clipboard.setPrimaryClip(ClipData.newPlainText(label, copiedText));
        scheduleClipboardClear(activity.getApplicationContext(), copiedText, clearDelayMs);
        return true;
    }

    private static void scheduleClipboardClear(Context context, String copiedText, long clearDelayMs) {
        if (context == null || copiedText == null || copiedText.isEmpty() || clearDelayMs <= 0L) {
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> clearClipboardIfUnchanged(context, copiedText),
                clearDelayMs);
    }

    private static void clearClipboardIfUnchanged(Context context, String expectedText) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            return;
        }
        ClipData clipData = clipboard.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() == 0) {
            return;
        }
        CharSequence currentText = clipData.getItemAt(0).coerceToText(context);
        if (!TextUtils.equals(expectedText, currentText)) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
    }
}

