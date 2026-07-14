package org.servalproject.satnet.maps;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.servalproject.bitcoin.security.WalletDeviceSecretStore;
import org.servalproject.util.Base64Compat;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class SecureMapBookmarkStore {
    public static final String PREFS_NAME = "satnet_maps_secure";
    public static final String KEY_ENCRYPTED_BOOKMARKS = "bookmarks_blob_v1";

    private static final String TAG = "SecureMapBookmarks";
    private static final String DEVICE_SECRET_ID = "satnet_maps_bookmarks";
    private static final String ENCRYPTION_AAD = "satnet.maps.bookmarks.v1";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final Type BOOKMARK_LIST_TYPE = new TypeToken<List<SatnetMapBookmark>>() { }
            .getType();

    private final Context appContext;
    private final SharedPreferences preferences;
    private final Gson gson;

    public SecureMapBookmarkStore(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }
        this.appContext = context.getApplicationContext();
        this.preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    public synchronized List<SatnetMapBookmark> loadBookmarks() {
        String encryptedBlob = preferences.getString(KEY_ENCRYPTED_BOOKMARKS, null);
        if (encryptedBlob == null || encryptedBlob.trim().isEmpty()) {
            return new ArrayList<SatnetMapBookmark>();
        }

        try {
            String json = decrypt(encryptedBlob.trim());
            List<SatnetMapBookmark> parsed = gson.fromJson(json, BOOKMARK_LIST_TYPE);
            if (parsed == null || parsed.isEmpty()) {
                return new ArrayList<SatnetMapBookmark>();
            }

            List<SatnetMapBookmark> sanitized = new ArrayList<SatnetMapBookmark>();
            for (SatnetMapBookmark bookmark : parsed) {
                if (bookmark == null) {
                    continue;
                }
                SatnetMapBookmark copy = bookmark.sanitizedCopy();
                if (copy.isValid()) {
                    sanitized.add(copy);
                }
            }
            sortNewestFirst(sanitized);
            return sanitized;
        } catch (Exception e) {
            Log.w(TAG, "Unable to decrypt stored SATNET map bookmarks; clearing corrupted state", e);
            preferences.edit().remove(KEY_ENCRYPTED_BOOKMARKS).commit();
            return new ArrayList<SatnetMapBookmark>();
        }
    }

    public synchronized void upsertBookmark(SatnetMapBookmark bookmark) {
        if (bookmark == null) {
            throw new IllegalArgumentException("Bookmark is required");
        }
        SatnetMapBookmark sanitized = bookmark.sanitizedCopy();
        if (!sanitized.isValid()) {
            throw new IllegalArgumentException("Bookmark coordinates are invalid");
        }

        List<SatnetMapBookmark> bookmarks = loadBookmarks();
        boolean replaced = false;
        for (int i = 0; i < bookmarks.size(); i++) {
            if (bookmarks.get(i).id.equals(sanitized.id)) {
                bookmarks.set(i, sanitized);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            bookmarks.add(sanitized);
        }
        persistBookmarks(bookmarks);
    }

    public synchronized void deleteBookmark(String bookmarkId) {
        if (bookmarkId == null || bookmarkId.trim().isEmpty()) {
            return;
        }
        List<SatnetMapBookmark> bookmarks = loadBookmarks();
        for (int i = bookmarks.size() - 1; i >= 0; i--) {
            if (bookmarkId.equals(bookmarks.get(i).id)) {
                bookmarks.remove(i);
            }
        }
        persistBookmarks(bookmarks);
    }

    public synchronized void clearAll() {
        preferences.edit().remove(KEY_ENCRYPTED_BOOKMARKS).commit();
    }

    private void persistBookmarks(List<SatnetMapBookmark> bookmarks) {
        List<SatnetMapBookmark> sanitized = new ArrayList<SatnetMapBookmark>();
        if (bookmarks != null) {
            for (SatnetMapBookmark bookmark : bookmarks) {
                if (bookmark == null) {
                    continue;
                }
                SatnetMapBookmark copy = bookmark.sanitizedCopy();
                if (copy.isValid()) {
                    sanitized.add(copy);
                }
            }
        }
        sortNewestFirst(sanitized);

        if (sanitized.isEmpty()) {
            preferences.edit().remove(KEY_ENCRYPTED_BOOKMARKS).commit();
            return;
        }

        try {
            String json = gson.toJson(sanitized, BOOKMARK_LIST_TYPE);
            String encryptedBlob = encrypt(json);
            preferences.edit().putString(KEY_ENCRYPTED_BOOKMARKS, encryptedBlob).commit();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to securely store SATNET map bookmarks", e);
        }
    }

    private String encrypt(String plaintext) throws Exception {
        byte[] secret = WalletDeviceSecretStore.getOrCreateBindingSecret(appContext, DEVICE_SECRET_ID);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secret, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        cipher.updateAAD(ENCRYPTION_AAD.getBytes(StandardCharsets.UTF_8));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64Compat.encode(iv) + ":" + Base64Compat.encode(ciphertext);
    }

    private String decrypt(String encryptedBlob) throws Exception {
        String[] parts = encryptedBlob.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Malformed encrypted bookmark blob");
        }
        byte[] iv = Base64Compat.decode(parts[0]);
        byte[] ciphertext = Base64Compat.decode(parts[1]);
        if (iv.length != GCM_IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("Malformed encrypted bookmark IV");
        }

        byte[] secret = WalletDeviceSecretStore.getOrCreateBindingSecret(appContext, DEVICE_SECRET_ID);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secret, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        cipher.updateAAD(ENCRYPTION_AAD.getBytes(StandardCharsets.UTF_8));
        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private static void sortNewestFirst(List<SatnetMapBookmark> bookmarks) {
        Collections.sort(bookmarks, new Comparator<SatnetMapBookmark>() {
            @Override
            public int compare(SatnetMapBookmark left, SatnetMapBookmark right) {
                return Long.compare(right.createdAtMs, left.createdAtMs);
            }
        });
    }
}

