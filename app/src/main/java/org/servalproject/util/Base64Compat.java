package org.servalproject.util;

import java.lang.reflect.Method;

public final class Base64Compat {
    private Base64Compat() {
    }

    public static String encode(byte[] value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot encode null value");
        }
        String encoded = tryJavaEncode(value);
        if (encoded == null) {
            encoded = tryAndroidEncode(value);
        }
        if (encoded == null) {
            throw new IllegalStateException("Base64 encoding is unavailable");
        }
        return encoded.trim();
    }

    public static byte[] decode(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Cannot decode empty value");
        }
        byte[] decoded = tryJavaDecode(value.trim());
        if (decoded == null) {
            decoded = tryAndroidDecode(value.trim());
        }
        if (decoded == null) {
            throw new IllegalStateException("Base64 decoding is unavailable");
        }
        return decoded;
    }

    private static String tryJavaEncode(byte[] value) {
        try {
            Class<?> base64Class = Class.forName("java.util.Base64");
            Method getEncoder = base64Class.getMethod("getEncoder");
            Object encoder = getEncoder.invoke(null);
            Method encodeToString = encoder.getClass().getMethod("encodeToString", byte[].class);
            return (String) encodeToString.invoke(encoder, value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static byte[] tryJavaDecode(String value) {
        try {
            Class<?> base64Class = Class.forName("java.util.Base64");
            Method getDecoder = base64Class.getMethod("getDecoder");
            Object decoder = getDecoder.invoke(null);
            Method decode = decoder.getClass().getMethod("decode", String.class);
            return (byte[]) decode.invoke(decoder, value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String tryAndroidEncode(byte[] value) {
        try {
            Class<?> base64Class = Class.forName("android.util.Base64");
            int noWrap = base64Class.getField("NO_WRAP").getInt(null);
            Method encodeToString = base64Class.getMethod("encodeToString", byte[].class, int.class);
            return (String) encodeToString.invoke(null, value, noWrap);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static byte[] tryAndroidDecode(String value) {
        try {
            Class<?> base64Class = Class.forName("android.util.Base64");
            int defaultFlags = base64Class.getField("DEFAULT").getInt(null);
            Method decode = base64Class.getMethod("decode", String.class, int.class);
            return (byte[]) decode.invoke(null, value, defaultFlags);
        } catch (Exception ignored) {
            return null;
        }
    }
}

