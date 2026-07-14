package org.servalproject.voucher;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class VoucherIssuerRotationPolicyTest {
    private static final String PREFS_NAME = "satnet_voucher_issuer";

    private Context context;
    private VoucherLedger ledger;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("satnet_vouchers.db");
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        ledger = new VoucherLedger(context);
    }

    @Test
    public void resolveRotatesAfterIssuanceThreshold() throws Exception {
        long now = 1_700_000_000_000L;
        VoucherIssuerRotationPolicy.ActiveIssuerState first = VoucherIssuerRotationPolicy.resolve(
                context,
                ledger,
                now,
                Long.MAX_VALUE,
                1);

        BitcoinVoucher voucher = BitcoinVoucher.generateNew(
                "agent_rotation_threshold",
                1000,
                24,
                BitcoinVoucher.DIRECTION_BUY,
                0.0,
                "USD",
                first);
        ledger.recordIssuedVoucher(voucher);

        VoucherIssuerRotationPolicy.ActiveIssuerState rotated = VoucherIssuerRotationPolicy.resolve(
                context,
                ledger,
                now + 1,
                Long.MAX_VALUE,
                1);

        assertTrue(rotated.isRotated());
        assertEquals("issuance-threshold", rotated.getRotationReason());
        assertEquals(1L, rotated.getRotationEpoch());
        assertEquals(first.getIdentity().getKeystoreAlias(), rotated.getPreviousAlias());
        assertNotEquals(first.getIdentity().getKeystoreAlias(), rotated.getIdentity().getKeystoreAlias());

        BitcoinVoucher rotatedVoucher = BitcoinVoucher.generateNew(
                "agent_rotation_threshold",
                2000,
                24,
                BitcoinVoucher.DIRECTION_BUY,
                0.0,
                "USD",
                rotated);

        assertEquals(rotated.getPreviousAlias(), rotatedVoucher.getIssuerPreviousKeystoreAlias());
        assertEquals(rotated.getRotationReason(), rotatedVoucher.getIssuerRotationReason());
        assertEquals(rotated.getRotationEpoch(), rotatedVoucher.getIssuerRotationEpoch());
        assertEquals(rotated.getIdentity().getKeystoreAlias(), rotatedVoucher.getIssuerKeystoreAlias());
        assertEquals(rotated.getPreviousAlias(),
                rotatedVoucher.getSecondSignatureManifest().getPreviousIssuerKeystoreAlias());
    }

    @Test
    public void resolveRotatesAfterAliasAgeThreshold() throws Exception {
        long start = 1_700_000_000_000L;
        VoucherIssuerRotationPolicy.ActiveIssuerState first = VoucherIssuerRotationPolicy.resolve(
                context,
                ledger,
                start,
                10_000L,
                Integer.MAX_VALUE);

        VoucherIssuerRotationPolicy.ActiveIssuerState rotated = VoucherIssuerRotationPolicy.resolve(
                context,
                ledger,
                start + 10_001L,
                10_000L,
                Integer.MAX_VALUE);

        assertTrue(rotated.isRotated());
        assertEquals("age-threshold", rotated.getRotationReason());
        assertEquals(first.getRotationEpoch() + 1L, rotated.getRotationEpoch());
        assertNotEquals(first.getIdentity().getKeystoreAlias(), rotated.getIdentity().getKeystoreAlias());
        assertEquals(first.getIdentity().getKeystoreAlias(), rotated.getPreviousAlias());
    }
}

