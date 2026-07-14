package org.servalproject.satnet.maps;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.servalproject.ServalBatPhoneApplication;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SecureMapBookmarkStoreTest {

    private Context context;
    private SecureMapBookmarkStore store;

    @Before
    public void setUp() {
        ServalBatPhoneApplication app = ApplicationProvider.getApplicationContext();
        ServalBatPhoneApplication.context = app;
        context = resetAppData(app);
        context.getSharedPreferences(SecureMapBookmarkStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("bitcoin_wallet_device_binding", Context.MODE_PRIVATE).edit().clear().commit();
        store = new SecureMapBookmarkStore(context);
    }

    private Context resetAppData(ServalBatPhoneApplication app) {
        app.getSharedPreferences("satnet_roles", Context.MODE_PRIVATE).edit().clear().commit();
        app.getSharedPreferences("bitcoin_wallet", Context.MODE_PRIVATE).edit().clear().commit();
        app.deleteDatabase("satnet_vouchers.db");
        return app;
    }

    @Test
    public void bookmarkRoundTripsAndRawStorageDoesNotExposePlaintext() {
        SatnetMapBookmark bookmark = SatnetMapBookmark.create("Clinic Alpha", "Primary field cache", 1.23456d, 32.98765d);
        store.upsertBookmark(bookmark);

        List<SatnetMapBookmark> bookmarks = store.loadBookmarks();
        assertEquals(1, bookmarks.size());
        assertEquals("Clinic Alpha", bookmarks.get(0).getDisplayLabel());
        assertEquals("Primary field cache", bookmarks.get(0).getDisplayNote());

        SharedPreferences prefs = context.getSharedPreferences(SecureMapBookmarkStore.PREFS_NAME, Context.MODE_PRIVATE);
        String rawBlob = prefs.getString(SecureMapBookmarkStore.KEY_ENCRYPTED_BOOKMARKS, null);
        assertNotNull(rawBlob);
        assertFalse(rawBlob.contains("Clinic Alpha"));
        assertFalse(rawBlob.contains("Primary field cache"));
        assertFalse(rawBlob.contains("1.23456"));
    }

    @Test
    public void corruptedBlobIsClearedSafely() {
        context.getSharedPreferences(SecureMapBookmarkStore.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(SecureMapBookmarkStore.KEY_ENCRYPTED_BOOKMARKS, "not-valid")
                .commit();

        List<SatnetMapBookmark> bookmarks = store.loadBookmarks();
        assertTrue(bookmarks.isEmpty());

        String rawBlob = context.getSharedPreferences(SecureMapBookmarkStore.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(SecureMapBookmarkStore.KEY_ENCRYPTED_BOOKMARKS, null);
        assertTrue(rawBlob == null || rawBlob.isEmpty());
    }

    @Test
    public void deleteBookmarkRemovesSavedEntry() {
        SatnetMapBookmark left = SatnetMapBookmark.create("North", "", 1.0d, 2.0d);
        SatnetMapBookmark right = SatnetMapBookmark.create("South", "", -1.0d, -2.0d);
        store.upsertBookmark(left);
        store.upsertBookmark(right);

        store.deleteBookmark(left.id);

        List<SatnetMapBookmark> bookmarks = store.loadBookmarks();
        assertEquals(1, bookmarks.size());
        assertEquals("South", bookmarks.get(0).getDisplayLabel());
    }
}

