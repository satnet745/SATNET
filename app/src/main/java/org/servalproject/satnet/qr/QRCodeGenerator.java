/**
 * Copyright (C) 2025 SATNET AFRICA
 *
 * This file is part of SATNET AFRICA (http://satnetafrica.org)
 *
 * SATNET AFRICA is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.servalproject.satnet.qr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.appcompat.content.res.AppCompatResources;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * QR Code Generator for SATNET AFRICA
 *
 * Features:
 * - Generate Bitcoin address QR codes
 * - Generate Lightning invoice QR codes
 * - Generate voucher QR codes (bidirectional)
 * - Generate agent reputation QR codes
 * - Customizable size and error correction
 */
public class QRCodeGenerator {
    private static final String TAG = "QRCodeGenerator";
    private static final int DEFAULT_SIZE = 512;
    private static final ErrorCorrectionLevel[] ERROR_CORRECTION_FALLBACKS = new ErrorCorrectionLevel[] {
            ErrorCorrectionLevel.H,
            ErrorCorrectionLevel.Q,
            ErrorCorrectionLevel.M,
            ErrorCorrectionLevel.L
    };
    private static final int[] MARGIN_FALLBACKS = new int[] {1, 0};
    private static final float CENTER_LOGO_SCALE = 0.16f;
    private static final float CENTER_LOGO_FRAME_SCALE = 1.28f;

    /**
     * Generate QR code for Bitcoin address (BIP21 format)
     */
    public static Bitmap generateBitcoinAddressQR(String bitcoinAddress) throws WriterException {
        return generateQRCode(bitcoinAddress, DEFAULT_SIZE);
    }

    /**
     * Generate QR code for Lightning invoice
     */
    public static Bitmap generateLightningInvoiceQR(String lightningInvoice) throws WriterException {
        return generateQRCode(lightningInvoice, DEFAULT_SIZE);
    }

    /**
     * Generate QR code for SATNET AFRICA voucher (bidirectional)
     * Format: satnet_voucher|<id>|<secret>|<amount>|<agent>|<direction>|<rate>|<currency>
     * direction: 1=BUY, 2=SELL
     */
    public static Bitmap generateVoucherQR(String voucherId, String secret, long amount,
                                          String agentId, int direction, double exchangeRate,
                                          String currencyCode) throws WriterException {
        String payload = String.format(Locale.ROOT, "satnet_voucher|%s|%s|%d|%s|%d|%.2f|%s",
            voucherId, secret, amount, agentId, direction, exchangeRate, currencyCode);
        return generateQRCode(payload, DEFAULT_SIZE);
    }

    /**
     * Generate QR code for agent reputation
     * Format: satnet_agent|<sid>|<name>|<tier>|<reputation>|<cashReserve>
     */
    public static Bitmap generateAgentReputationQR(String agentSID, String agentName, int tier,
                                                  int reputation, long cashReserve) throws WriterException {
        String payload = String.format(Locale.ROOT, "satnet_agent|%s|%s|%d|%d|%d",
            agentSID, agentName, tier, reputation, cashReserve);
        return generateQRCode(payload, DEFAULT_SIZE);
    }

    /**
     * Generic QR code generator with error correction
     */
    public static Bitmap generateQRCode(String data, int size) throws WriterException {
        if (data == null || data.trim().isEmpty()) {
            throw new IllegalArgumentException("QR data is required");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("QR size must be greater than zero");
        }

        Log.d(TAG, "Generating QR code, size=" + size + ", payloadLength=" + data.length());

        QRCodeWriter writer = new QRCodeWriter();
        WriterException lastFailure = null;
        for (ErrorCorrectionLevel level : ERROR_CORRECTION_FALLBACKS) {
            for (int margin : MARGIN_FALLBACKS) {
                try {
                    BitMatrix bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size,
                            buildEncodeHints(level, margin));
                    Log.d(TAG, "QR code generated with level=" + level + ", margin=" + margin);
                    return overlayCenterLogo(bitMatrixToBitmap(bitMatrix));
                } catch (WriterException e) {
                    lastFailure = e;
                    Log.w(TAG, "QR encode attempt failed with level=" + level
                            + ", margin=" + margin
                            + ", payloadLength=" + data.length(), e);
                }
            }
        }

        Log.e(TAG, "Error generating QR code after retries, payloadLength=" + data.length(), lastFailure);
        throw lastFailure;
    }

    private static Map<EncodeHintType, Object> buildEncodeHints(ErrorCorrectionLevel errorCorrectionLevel, int margin) {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, errorCorrectionLevel);
        hints.put(EncodeHintType.MARGIN, margin);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        return hints;
    }

    /**
     * Convert BitMatrix to Android Bitmap
     */
    private static Bitmap bitMatrixToBitmap(BitMatrix bitMatrix) {
        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        int[] pixels = new int[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);

        return bitmap;
    }

    private static Bitmap overlayCenterLogo(Bitmap qrBitmap) {
        if (qrBitmap == null) {
            return null;
        }
        Bitmap mutableBitmap = qrBitmap.copy(Bitmap.Config.ARGB_8888, true);
        if (mutableBitmap == null) {
            return qrBitmap;
        }

        int width = mutableBitmap.getWidth();
        int height = mutableBitmap.getHeight();
        int qrSize = Math.min(width, height);
        int logoSize = Math.max(1, Math.round(qrSize * CENTER_LOGO_SCALE));
        int frameSize = Math.max(logoSize + 4, Math.round(logoSize * CENTER_LOGO_FRAME_SCALE));
        float left = (width - frameSize) / 2f;
        float top = (height - frameSize) / 2f;
        float right = left + frameSize;
        float bottom = top + frameSize;

        Canvas canvas = new Canvas(mutableBitmap);
        Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        framePaint.setColor(0xFFFFFFFF);
        float cornerRadius = Math.max(6f, frameSize * 0.18f);
        RectF frameRect = new RectF(left, top, right, bottom);
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, framePaint);

        int logoLeft = (width - logoSize) / 2;
        int logoTop = (height - logoSize) / 2;
        int logoRight = logoLeft + logoSize;
        int logoBottom = logoTop + logoSize;
        Drawable logoDrawable = loadSatnetLogoDrawable();
        if (logoDrawable != null) {
            logoDrawable.setBounds(logoLeft, logoTop, logoRight, logoBottom);
            logoDrawable.draw(canvas);
        } else {
            drawFallbackSatnetLogo(canvas, new RectF(logoLeft, logoTop, logoRight, logoBottom));
        }
        return mutableBitmap;
    }

    private static Drawable loadSatnetLogoDrawable() {
        try {
            Context context = ServalBatPhoneApplication.context;
            if (context == null) {
                return null;
            }
            return AppCompatResources.getDrawable(context, R.drawable.ic_satnet_brand);
        } catch (Exception e) {
            Log.w(TAG, "Unable to load SATNET logo drawable for QR overlay", e);
            return null;
        }
    }

    private static void drawFallbackSatnetLogo(Canvas canvas, RectF bounds) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);

        float radius = Math.min(bounds.width(), bounds.height()) * 0.14f;
        paint.setColor(0xFF041B15);
        canvas.drawRoundRect(bounds, radius, radius, paint);

        float w = bounds.width();
        float h = bounds.height();

        paint.setColor(0xFFFFFFFF);
        drawBlock(canvas, paint, bounds, 0.14f * w, 0.38f * h, 0.22f * w, 0.22f * h);
        drawBlock(canvas, paint, bounds, 0.38f * w, 0.14f * h, 0.22f * w, 0.22f * h);
        drawBlock(canvas, paint, bounds, 0.38f * w, 0.38f * h, 0.22f * w, 0.22f * h);
        drawBlock(canvas, paint, bounds, 0.62f * w, 0.38f * h, 0.22f * w, 0.22f * h);
        drawBlock(canvas, paint, bounds, 0.38f * w, 0.62f * h, 0.22f * w, 0.22f * h);
    }

    private static void drawBlock(Canvas canvas, Paint paint, RectF bounds,
                                  float x, float y, float width, float height) {
        RectF rect = new RectF(
                bounds.left + x,
                bounds.top + y,
                bounds.left + x + width,
                bounds.top + y + height
        );
        if (bounds.top + y >= bounds.top + (0.62f * bounds.height())) {
            paint.setColor(0xFF22C55E);
        } else {
            paint.setColor(0xFFFFFFFF);
        }
        canvas.drawRect(rect, paint);
    }

    /**
     * Decode QR code data (placeholder - uses ZXing decoding)
     */
    public static String decodeQRCode(Bitmap qrBitmap) throws Exception {
        Log.d(TAG, "Decoding QR code from bitmap");
        if (qrBitmap == null) {
            throw new IllegalArgumentException("QR bitmap is required");
        }

        int width = qrBitmap.getWidth();
        int height = qrBitmap.getHeight();
        int[] pixels = new int[width * height];
        qrBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
        MultiFormatReader reader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.POSSIBLE_FORMATS, java.util.Collections.singletonList(BarcodeFormat.QR_CODE));
        try {
            Result result = reader.decode(binaryBitmap, hints);
            return result.getText();
        } catch (NotFoundException e) {
            Log.e(TAG, "QR code not found in bitmap", e);
            throw e;
        } finally {
            reader.reset();
        }
    }
}

