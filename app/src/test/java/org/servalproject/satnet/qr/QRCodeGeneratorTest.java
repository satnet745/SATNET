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

package org.servalproject.satnet.qr;

import android.graphics.Bitmap;

import com.google.zxing.WriterException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.servalproject.voucher.BitcoinVoucher;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class QRCodeGeneratorTest {

    private static final String TEST_DATA = "satnet_voucher|agent_001|abc123|50000|agentid";

    @Test
    public void testQRCodeGeneration() throws WriterException {
        Bitmap qr = QRCodeGenerator.generateQRCode(TEST_DATA, 512);
        assertNotNull(qr);
        assertEquals(512, qr.getWidth());
        assertEquals(512, qr.getHeight());
    }

    @Test
    public void testVoucherQRGeneration() throws WriterException {
        Bitmap qr = QRCodeGenerator.generateVoucherQR(
                "id_123",
                "secret_abc",
                5000L,
                "agent_xyz",
                1,
                0.0,
                "USD"
        );

        assertNotNull(qr);
        assertEquals(512, qr.getWidth());
        assertEquals(512, qr.getHeight());
    }

    @Test
    public void testLightningQRGeneration() throws WriterException {
        Bitmap qr = QRCodeGenerator.generateLightningInvoiceQR("lnbc50000n1pexample");
        assertNotNull(qr);
        assertEquals(512, qr.getWidth());
    }

    @Test
    public void testAddressQRGeneration() throws WriterException {
        Bitmap qr = QRCodeGenerator.generateBitcoinAddressQR("tb1qexample123");
        assertNotNull(qr);
        assertEquals(512, qr.getWidth());
    }

    @Test
    public void testSmallAndLargeQRCode() throws WriterException {
        Bitmap small = QRCodeGenerator.generateQRCode("small", 256);
        Bitmap large = QRCodeGenerator.generateQRCode("large", 1024);

        assertEquals(256, small.getWidth());
        assertEquals(1024, large.getWidth());
    }

    @Test
    public void testDecodeGeneratedVoucherQRCodeRoundTrip() throws Exception {
        Bitmap qr = QRCodeGenerator.generateQRCode(TEST_DATA, 512);
        assertEquals(TEST_DATA, QRCodeGenerator.decodeQRCode(qr));
    }

    @Test
    public void testDecodeGeneratedLightningQRCodeRoundTrip() throws Exception {
        String invoice = "lnsatnet1.c29tZS1lbmNvZGVkLWludm9pY2U";
        Bitmap qr = QRCodeGenerator.generateLightningInvoiceQR(invoice);
        assertEquals(invoice, QRCodeGenerator.decodeQRCode(qr));
    }

    @Test
    public void testGenerateSignedVoucherPayloadQRCodeRoundTrip() throws Exception {
        BitcoinVoucher voucher = BitcoinVoucher.generateNew("agent_qr_test_001", 5000L, 24);
        String payload = voucher.getQRPayload();

        Bitmap qr = QRCodeGenerator.generateQRCode(payload, 512);

        assertNotNull(qr);
        assertEquals(payload, QRCodeGenerator.decodeQRCode(qr));
    }

}
