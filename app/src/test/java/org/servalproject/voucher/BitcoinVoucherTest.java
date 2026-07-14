/**
 * Copyright (C) 2025 SATNET AFRICA
 *
 * This file is part of SATNET AFRICA (http://satnetafrica.org)
 *
 * SATNET AFRICA is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 */

package org.servalproject.voucher;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.junit.Assert.*;

/**
 * Unit tests for BitcoinVoucher
 *
 * Tests voucher generation, validation, QR encoding, and expiry enforcement
 */
public class BitcoinVoucherTest {

    private static final String TEST_AGENT_ID = "agent_test_001";

    @Before
    public void setUp() {
        // Reset state before each test
    }

    @Test
    public void testVoucherGeneration() throws Exception {
        // Generate voucher for 1000 sats, expires in 24 hours
        BitcoinVoucher voucher = BitcoinVoucher.generateNew(
            TEST_AGENT_ID,
            1000,
            24
        );

        // Verify basic properties
        assertNotNull("Voucher ID should not be null", voucher.getVoucherId());
        assertEquals("Denomination should match", 1000, voucher.getDenomination());
        assertEquals("Agent ID should match", TEST_AGENT_ID, voucher.getAgentId());
        assertEquals("State should be ISSUED", BitcoinVoucher.STATE_ISSUED, voucher.getState());

        // Verify expiry is approximately 24 hours from now
        long now = System.currentTimeMillis();
        long expiryTime = voucher.getExpiryTime();
        long timeDiff = expiryTime - now;
        long hours24 = 24 * 60 * 60 * 1000;

        // Allow 1 minute tolerance
        assertTrue("Expiry should be ~24 hours from now",
                  timeDiff > hours24 - 60000 && timeDiff < hours24 + 60000);

        System.out.println("✓ Generated voucher: " + voucher.getVoucherId());
    }

    @Test
    public void testQRPayloadGeneration() throws Exception {
        BitcoinVoucher voucher = BitcoinVoucher.generateNew(
            TEST_AGENT_ID,
            5000,
            24
        );

        String qrPayload = voucher.getQRPayload();

        // Verify format starts with core fields and now includes metadata.
        assertTrue("QR payload should start with satnet_voucher",
                  qrPayload.startsWith("satnet_voucher|"));

        String[] parts = qrPayload.split("\\|");
        assertEquals("QR payload should use the new four-part v2 envelope", 4, parts.length);

        assertEquals("Part 0 should be satnet_voucher", "satnet_voucher", parts[0]);
        assertEquals("Part 1 should be the payload version marker", "v2", parts[1]);

        System.out.println("✓ QR payload format valid: " + qrPayload.substring(0, Math.min(50, qrPayload.length())) + "...");
    }

    @Test
    public void testVoucherParsingFromQR() throws Exception {
        // Generate and encode
        BitcoinVoucher original = BitcoinVoucher.generateNew(
            TEST_AGENT_ID,
            10000,
            24
        );
        String qrPayload = original.getQRPayload();

        // Parse from QR
        BitcoinVoucher parsed = BitcoinVoucher.parseQRPayload(qrPayload);

        // Verify parsed matches original
        assertEquals("Voucher ID should match", original.getVoucherId(), parsed.getVoucherId());
        assertEquals("Denomination should match", original.getDenomination(), parsed.getDenomination());
        assertEquals("Agent ID should match", original.getAgentId(), parsed.getAgentId());
        assertEquals("Secret should match", original.getSecret(), parsed.getSecret());
        assertEquals("Secret hash should match", original.getSecretHash(), parsed.getSecretHash());

        System.out.println("✓ Successfully parsed voucher from QR payload");
    }

    @Test
    public void testVoucherValidation() throws Exception {
        BitcoinVoucher voucher = BitcoinVoucher.generateNew(
            TEST_AGENT_ID,
            1000,
            24
        );

        // Fresh voucher should be valid
        BitcoinVoucher.ValidationResult result = voucher.validate();
        assertTrue("Fresh voucher should be valid", result.isValid);
        assertEquals("Message should indicate valid", "Voucher valid", result.message);

        System.out.println("✓ Fresh voucher validation passed");
    }

    @Test
    public void testVoucherContainsIssuerSignatureMetadata() throws Exception {
        BitcoinVoucher voucher = BitcoinVoucher.generateNew(
                TEST_AGENT_ID,
                1000,
                24
        );

        assertNotNull("Issuer public key should be present", voucher.getIssuerPublicKey());
        assertFalse("Issuer public key should not be blank", voucher.getIssuerPublicKey().trim().isEmpty());
        assertNotNull("Issuer key id should be present", voucher.getIssuerKeyId());
        assertFalse("Issuer key id should not be blank", voucher.getIssuerKeyId().trim().isEmpty());
        assertNotNull("Signature should be present", voucher.getSignature());
        assertFalse("Signature should not be blank", voucher.getSignature().trim().isEmpty());
        assertEquals("V2 vouchers should record the primary signature algorithm",
                VoucherSignatureAlgorithms.ALG_RSA_SHA256,
                voucher.getPrimarySignatureAlgorithm());
        assertEquals("New vouchers should default to payload v2", 2, voucher.getPayloadVersion());
        assertNotNull("Signature bundle should be present", voucher.getSignatureBundle());
        assertEquals("New vouchers should emit two signature slots", 2, voucher.getSignatureBundle().getSignatures().size());
        assertEquals("Secondary slot should advertise the detached PQ algorithm identifier",
                VoucherSignatureAlgorithms.ALG_ML_DSA_87,
                voucher.getSignatureBundle().getSignatures().get(1).algorithm);
        assertNotNull("Second-signature manifest should be attached", voucher.getSecondSignatureManifest());
        assertEquals(VoucherSecondSignatureManifest.TYPE_SECOND_SIGNATURE,
                voucher.getSecondSignatureManifest().getType());
        assertFalse(voucher.getSecondSignatureManifest().getDetachedPublicKeyReference().trim().isEmpty());
        assertFalse(voucher.getSecondSignatureManifest().getMetadataReference().trim().isEmpty());
    }

    @Test
    public void testVoucherCarriesDetachedSecondSignatureReferences() throws Exception {
        BitcoinVoucher voucher = BitcoinVoucher.generateNew(TEST_AGENT_ID, 1000, 24);

        VoucherSecondSignatureManifest manifest = voucher.getSecondSignatureManifest();


        assertNotNull(manifest);
        assertEquals(VoucherSignatureAlgorithms.ALG_ML_DSA_87, manifest.getAlgorithm());
        assertEquals(voucher.getSignatureBundle().getPayloadHash(), manifest.getPayloadHash());
        assertEquals(VoucherSecondSignatureManifest.BINDING_DETACHED_PUBLIC_KEY_REFERENCE, manifest.getBindingMode());
        assertNotNull(voucher.getSecondSignatureManifestJson());
        assertTrue(voucher.getSecondSignatureManifestJson().contains("detachedPublicKeyReference"));
        assertNotNull(voucher.getSecondSignaturePublicKeyReference());
        assertTrue(voucher.getSecondSignaturePublicKeyReference().startsWith("issuer://")
                || voucher.getSecondSignaturePublicKeyReference().startsWith("keystore://"));
        assertNotNull(voucher.getSecondSignatureReference());
        assertNotNull(voucher.getSecondSignatureMetadataReference());
    }

    @Test
    public void testTamperedSignedVoucherIsRejected() throws Exception {
        BitcoinVoucher original = BitcoinVoucher.generateNew(
                TEST_AGENT_ID,
                1000,
                24
        );

        String[] parts = original.getQRPayload().split("\\|", 4);
        String body = new String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8);
        String[] bodyParts = body.split("\\|");
        bodyParts[3] = "2000";
        String tamperedBody = String.join("|", bodyParts);
        String tamperedPayload = "satnet_voucher|v2|"
                + Base64.getEncoder().encodeToString(tamperedBody.getBytes(StandardCharsets.UTF_8))
                + "|"
                + parts[3];

        BitcoinVoucher tampered = BitcoinVoucher.parseQRPayload(tamperedPayload);
        BitcoinVoucher.ValidationResult result = tampered.validate();

        assertFalse("Tampered signed voucher should be invalid", result.isValid);
        assertTrue("Tamper result should mention payload/signature integrity",
                result.message.toLowerCase().contains("signature")
                        || result.message.toLowerCase().contains("hash"));
    }

    @Test
    public void testLegacySignedVoucherPayloadRemainsValidForInterop() throws Exception {
        BitcoinVoucher original = BitcoinVoucher.generateNew(
                TEST_AGENT_ID,
                1000,
                24
        );

        BitcoinVoucher legacyParsed = BitcoinVoucher.parseQRPayload(original.getLegacyQrPayloadForInterop());
        BitcoinVoucher.ValidationResult result = legacyParsed.validate();

        assertTrue("Legacy signed payload should still validate", result.isValid);
        assertEquals(original.getVoucherId(), legacyParsed.getVoucherId());
        assertEquals(VoucherSignatureAlgorithms.ALG_RSA_SHA256, legacyParsed.getPrimarySignatureAlgorithm());
    }

    @Test
    public void testFutureUnsupportedSignatureCanCoexistWithSupportedPrimarySignature() throws Exception {
        BitcoinVoucher original = BitcoinVoucher.generateNew(TEST_AGENT_ID, 1000, 24);
        java.util.List<VoucherSignatureBundle.SignatureEntry> signatures = new java.util.ArrayList<VoucherSignatureBundle.SignatureEntry>();
        signatures.add(new VoucherSignatureBundle.SignatureEntry(
                VoucherSignatureAlgorithms.ALG_RSA_SHA256,
                original.getIssuerKeyId(),
                original.getIssuerPublicKey(),
                original.getSignature(),
                "primary"));
        signatures.add(new VoucherSignatureBundle.SignatureEntry(
                VoucherSignatureAlgorithms.ALG_ML_DSA_87,
                "PQ-KEY-01",
                "future-public-key-reference",
                "future-signature-placeholder",
                "post-quantum-secondary"));

        VoucherSignatureBundle mixedBundle = VoucherSignatureBundle.forPayload(original.getCanonicalPayload(), signatures);
        VoucherSignatureBundle.VerificationResult result = mixedBundle.verify(original.getCanonicalPayload());

        assertTrue(result.message, result.isValid);
        assertEquals("Mixed bundle should preserve both signature entries", 2, mixedBundle.getSignatures().size());
    }

    @Test
    public void testUnsupportedOnlySignatureBundleIsRejected() throws Exception {
        BitcoinVoucher original = BitcoinVoucher.generateNew(TEST_AGENT_ID, 1000, 24);
        VoucherSignatureBundle unsupportedBundle = VoucherSignatureBundle.forPayload(
                original.getCanonicalPayload(),
                java.util.Collections.singletonList(new VoucherSignatureBundle.SignatureEntry(
                        VoucherSignatureAlgorithms.ALG_ML_DSA_87,
                        "PQ-KEY-01",
                        "future-public-key-reference",
                        "future-signature-placeholder",
                        "post-quantum-primary")));

        VoucherSignatureBundle.VerificationResult result = unsupportedBundle.verify(original.getCanonicalPayload());

        assertFalse("Unsupported-only signature bundles should be rejected", result.isValid);
        assertTrue(result.message.toLowerCase().contains("unsupported"));
    }

    private static String sha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) {
            builder.append(String.format(java.util.Locale.US, "%02x", b));
        }
        return builder.toString();
    }

    @Test
    public void testExpiredVoucherRejected() throws Exception {
        // Generate voucher that expires in 0 hours (immediately)
        BitcoinVoucher voucher = BitcoinVoucher.generateNew(
            TEST_AGENT_ID,
            1000,
            0  // Expires immediately
        );

        // Wait a bit to ensure expiry
        Thread.sleep(100);

        // Should be expired
        BitcoinVoucher.ValidationResult result = voucher.validate();
        assertFalse("Expired voucher should be invalid", result.isValid);
        assertTrue("Message should mention expiry", result.message.contains("expired"));

        System.out.println("✓ Correctly rejected expired voucher");
    }

    @Test
    public void testVoucherRedemption() throws Exception {
        BitcoinVoucher voucher = BitcoinVoucher.generateNew(
            TEST_AGENT_ID,
            5000,
            24
        );

        String walletAddress = "tb1qtest123";

        // Redeem voucher
        voucher.redeem(walletAddress);

        assertEquals("State should be REDEEMED", BitcoinVoucher.STATE_REDEEMED, voucher.getState());
        assertEquals("Redeemed wallet should match", walletAddress, voucher.getRedeemedByWallet());
        assertTrue("Redeemed time should be set", voucher.getRedeemedTime() > 0);

        System.out.println("✓ Successfully redeemed voucher");
    }

    @Test
    public void testAlreadyRedeemedVoucherRejected() throws Exception {
        BitcoinVoucher voucher = BitcoinVoucher.generateNew(
            TEST_AGENT_ID,
            1000,
            24
        );

        // Redeem once
        voucher.redeem("tb1qfirst");

        // Try to redeem again
        BitcoinVoucher.ValidationResult result = voucher.validate();
        assertFalse("Already redeemed voucher should be invalid", result.isValid);
        assertTrue("Message should mention already redeemed", result.message.contains("already redeemed"));

        System.out.println("✓ Correctly rejected already redeemed voucher");
    }

    @Test
    public void testNumericCodeFormat() throws Exception {
        BitcoinVoucher voucher = BitcoinVoucher.generateNew(
            TEST_AGENT_ID,
            1000,
            24
        );

        String numericCode = voucher.getNumericCode();

        // Verify format: XXXX-XXXX-XXXX-XXXX
        assertTrue("Should match format XXXX-XXXX-XXXX-XXXX",
                  numericCode.matches("[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}"));

        System.out.println("✓ Numeric code format valid: " + numericCode);
    }

    @Test
    public void testAllDenominations() throws Exception {
        long[] denominations = {
            BitcoinVoucher.DENOM_1000_SAT,
            BitcoinVoucher.DENOM_5000_SAT,
            BitcoinVoucher.DENOM_10000_SAT,
            BitcoinVoucher.DENOM_50000_SAT
        };

        for (long denom : denominations) {
            BitcoinVoucher voucher = BitcoinVoucher.generateNew(
                TEST_AGENT_ID,
                denom,
                24
            );

            assertEquals("Denomination should match", denom, voucher.getDenomination());

            // Validate QR encoding round-trips the denomination correctly.
            String qrPayload = voucher.getQRPayload();
            BitcoinVoucher parsed = BitcoinVoucher.parseQRPayload(qrPayload);
            assertEquals("QR should preserve denomination after parsing", denom, parsed.getDenomination());
        }

        System.out.println("✓ All denominations work correctly");
    }

    @Test
    public void testUniqueVouchers() throws Exception {
        // Generate multiple vouchers
        BitcoinVoucher v1 = BitcoinVoucher.generateNew(TEST_AGENT_ID, 1000, 24);
        BitcoinVoucher v2 = BitcoinVoucher.generateNew(TEST_AGENT_ID, 1000, 24);
        BitcoinVoucher v3 = BitcoinVoucher.generateNew(TEST_AGENT_ID, 1000, 24);

        // All should have unique IDs and secrets
        assertNotEquals("Voucher IDs should be unique", v1.getVoucherId(), v2.getVoucherId());
        assertNotEquals("Voucher IDs should be unique", v2.getVoucherId(), v3.getVoucherId());

        assertNotEquals("Secrets should be unique", v1.getSecret(), v2.getSecret());
        assertNotEquals("Secrets should be unique", v2.getSecret(), v3.getSecret());

        assertNotEquals("Hashes should be unique", v1.getSecretHash(), v2.getSecretHash());

        System.out.println("✓ Each voucher is unique");
    }

    @Test
    public void testVoucherGenerationHandlesNullBlankAndShortAgentIds() throws Exception {
        BitcoinVoucher nullAgentVoucher = BitcoinVoucher.generateNew(null, 1000, 24);
        BitcoinVoucher blankAgentVoucher = BitcoinVoucher.generateNew("   ", 1000, 24);
        BitcoinVoucher shortAgentVoucher = BitcoinVoucher.generateNew("ab", 1000, 24);

        assertEquals("agent", nullAgentVoucher.getAgentId());
        assertEquals("agent", blankAgentVoucher.getAgentId());
        assertEquals("ab", shortAgentVoucher.getAgentId());

        assertTrue(nullAgentVoucher.getVoucherId().startsWith("agent_"));
        assertTrue(blankAgentVoucher.getVoucherId().startsWith("agent_"));
        assertTrue(shortAgentVoucher.getVoucherId().startsWith("ab_"));
        assertTrue(shortAgentVoucher.getVoucherId().matches("[A-Za-z0-9]+_\\d+_[0-9a-f]{4}"));
    }
}

