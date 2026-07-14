package org.servalproject.voucher;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.servalproject.util.Base64Compat;

import java.security.KeyPair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class VoucherIssuerIdentityTest {
    private static final String PREFS_NAME = "satnet_voucher_issuer";
    private static final String KEY_PUBLIC = "public_key";
    private static final String KEY_PRIVATE = "private_key";
    private static final String KEY_ALGORITHM = "algorithm";
    private static final String KEY_KEYSTORE_ALIAS = "keystore_alias";

    private Context context;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    @Test
    public void loadOrCreateStoresOnlyMetadataAndNoRawPrivateKey() throws Exception {
        VoucherIssuerIdentity identity = VoucherIssuerIdentity.loadOrCreate(context);

        assertNotNull(identity);
        assertNotNull(identity.getIssuerKeyId());
        assertEquals(VoucherSignatureAlgorithms.ALG_RSA_SHA256, identity.getAlgorithm());
        assertNotNull(identity.getKeystoreAlias());
        assertFalse(identity.getKeystoreAlias().trim().isEmpty());

        assertNotNull(prefs.getString(KEY_PUBLIC, null));
        assertEquals(VoucherSignatureAlgorithms.ALG_RSA_SHA256, prefs.getString(KEY_ALGORITHM, null));
        assertEquals(identity.getKeystoreAlias(), prefs.getString(KEY_KEYSTORE_ALIAS, null));
        assertFalse("Raw private keys should no longer be stored in SharedPreferences", prefs.contains(KEY_PRIVATE));
    }

    @Test
    public void loadOrCreateReturnsStableIdentityAcrossRepeatedLoads() throws Exception {
        VoucherIssuerIdentity first = VoucherIssuerIdentity.loadOrCreate(context);
        VoucherIssuerIdentity second = VoucherIssuerIdentity.loadOrCreate(context);

        assertEquals(first.getIssuerKeyId(), second.getIssuerKeyId());
        assertEquals(first.getEncodedPublicKey(), second.getEncodedPublicKey());
        assertEquals(first.getKeystoreAlias(), second.getKeystoreAlias());
    }

    @Test
    public void legacySharedPreferencePrivateKeyIsMigratedToKeystoreAliasMetadata() throws Exception {
        KeyPair legacyKeyPair = VoucherSignatureAlgorithms.generateKeyPair(VoucherSignatureAlgorithms.ALG_RSA_SHA256);
        prefs.edit()
                .putString(KEY_PUBLIC, Base64Compat.encode(legacyKeyPair.getPublic().getEncoded()))
                .putString(KEY_PRIVATE, Base64Compat.encode(legacyKeyPair.getPrivate().getEncoded()))
                .putString(KEY_ALGORITHM, VoucherSignatureAlgorithms.ALG_RSA_SHA256)
                .commit();

        VoucherIssuerIdentity migrated = VoucherIssuerIdentity.loadOrCreate(context);
        VoucherSignatureBundle.SignatureEntry signatureEntry = migrated.createPrimarySignatureEntry("voucher-payload");

        assertNotNull(migrated.getKeystoreAlias());
        assertFalse(prefs.contains(KEY_PRIVATE));
        assertEquals(migrated.getKeystoreAlias(), prefs.getString(KEY_KEYSTORE_ALIAS, null));
        assertEquals(VoucherSignatureAlgorithms.ALG_RSA_SHA256, signatureEntry.algorithm);
        assertTrue(VoucherSignatureAlgorithms.verify(
                signatureEntry.algorithm,
                "voucher-payload",
                signatureEntry.signature,
                VoucherSignatureAlgorithms.decodePublicKey(signatureEntry.algorithm, signatureEntry.publicKey)));
    }
}

