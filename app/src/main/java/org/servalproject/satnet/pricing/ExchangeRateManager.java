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

package org.servalproject.satnet.pricing;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.servalproject.features.FeatureFlags;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Automatic live exchange-rate manager.
 *
 * Privacy / resilience goals:
 * - HTTPS only
 * - configurable provider mirror list
 * - no device identifiers or country gating
 * - fresh cache plus stale offline fallback
 */
public class ExchangeRateManager {
    private static final String TAG = "ExchangeRateManager";
    private static final String PREFS_NAME = "exchange_rates";
    private static final String KEY_RATE = "rate_";
    private static final String KEY_RATE_TIME = "rate_time_";
    private static final String KEY_RATE_SOURCE = "rate_source_";
    private static final String KEY_MANUAL_OVERRIDE = "override_";

    private static final long CACHE_DURATION_MS = 5 * 60 * 1000L;
    private static final long STALE_FALLBACK_DURATION_MS = 24 * 60 * 60 * 1000L;
    private static final double OVERRIDE_MAX_DEVIATION = 0.02;
    private static final String[] SUPPORTED_CURRENCY_CODES = new String[]{
            "USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "INR", "BRL", "MXN",
            "NGN", "GHS", "ZAR", "KES", "UGX", "TZS", "ETB", "CDF", "SSP"
    };

    private final SharedPreferences prefs;
    private final OkHttpClient httpClient;

    public ExchangeRateManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.httpClient = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public double getExchangeRate(String currencyCode, ExchangeRateCallback callback) {
        String normalizedCurrencyCode = normalizeCurrencyCode(currencyCode);
        RateSnapshot freshRate = getCachedRateSnapshot(normalizedCurrencyCode, CACHE_DURATION_MS);
        if (freshRate != null) {
            if (callback != null) {
                callback.onRateReceived(freshRate.rate, freshRate.source);
            }
            return freshRate.rate;
        }

        RateSnapshot staleFallback = getCachedRateSnapshot(normalizedCurrencyCode, STALE_FALLBACK_DURATION_MS);
        List<String> providerUrls = getProviderUrls();
        if (!FeatureFlags.isSatnetLiveExchangeRatesEnabled() || providerUrls.isEmpty()) {
            if (staleFallback != null) {
                if (callback != null) {
                    callback.onRateReceived(staleFallback.rate, staleFallback.source + ":stale_cache");
                }
                return staleFallback.rate;
            }
            if (callback != null) {
                callback.onRateFailed(false, "no_provider_mirrors_configured");
            }
            return 0.0;
        }

        fetchRateFromProviders(providerUrls, 0, normalizedCurrencyCode, staleFallback, callback);
        return staleFallback != null ? staleFallback.rate : 0.0;
    }

    private void fetchRateFromProviders(final List<String> providerUrls, final int providerIndex,
            final String currencyCode, final RateSnapshot staleFallback,
            final ExchangeRateCallback callback) {
        if (providerIndex >= providerUrls.size()) {
            if (callback != null) {
                callback.onRateFailed(staleFallback != null,
                        staleFallback != null ? "stale_cache" : "all_providers_failed");
            }
            return;
        }

        final String providerUrl = providerUrls.get(providerIndex);
        Request request = new Request.Builder().url(providerUrl).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "Failed to fetch exchange rate from " + providerUrl + ": " + e.getMessage());
                fetchRateFromProviders(providerUrls, providerIndex + 1, currencyCode, staleFallback, callback);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        Log.w(TAG, "Provider " + providerUrl + " returned " + response.code());
                        fetchRateFromProviders(providerUrls, providerIndex + 1, currencyCode, staleFallback, callback);
                        return;
                    }

                    String body = response.body() != null ? response.body().string() : "";
                    double rate = parseProviderRate(providerUrl, body, currencyCode);
                    if (rate <= 0) {
                        throw new IllegalStateException("Provider returned non-positive rate");
                    }

                    cacheRate(currencyCode, rate, providerUrl);
                    if (callback != null) {
                        callback.onRateReceived(rate, providerUrl);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error parsing rate from " + providerUrl + ": " + e.getMessage());
                    fetchRateFromProviders(providerUrls, providerIndex + 1, currencyCode, staleFallback, callback);
                } finally {
                    response.close();
                }
            }
        });
    }

    private double parseProviderRate(String providerUrl, String body, String currencyCode) {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        String currencyLower = currencyCode.toLowerCase(Locale.US);

        if (providerUrl.contains("coinbase.com")) {
            JsonObject data = json.getAsJsonObject("data");
            JsonObject rates = data != null ? data.getAsJsonObject("rates") : null;
            if (rates == null || !rates.has(currencyCode)) {
                throw new IllegalStateException("Coinbase response missing currency " + currencyCode);
            }
            return roundRateForDisplay(Double.parseDouble(rates.get(currencyCode).getAsString()));
        }

        if (providerUrl.contains("coingecko.com")) {
            JsonObject bitcoin = json.getAsJsonObject("bitcoin");
            if (bitcoin == null || !bitcoin.has(currencyLower)) {
                throw new IllegalStateException("CoinGecko response missing currency " + currencyCode);
            }
            return roundRateForDisplay(bitcoin.get(currencyLower).getAsDouble());
        }

        if (json.has(currencyCode)) {
            return roundRateForDisplay(json.get(currencyCode).getAsDouble());
        }
        if (json.has(currencyLower)) {
            return roundRateForDisplay(json.get(currencyLower).getAsDouble());
        }
        throw new IllegalStateException("Unsupported provider response shape for " + providerUrl);
    }

    public List<String> getProviderUrls() {
        String configuredProviders = FeatureFlags.getSatnetExchangeRateProviderUrls();
        if (configuredProviders == null || configuredProviders.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String[] rawProviders = configuredProviders.split(";");
        List<String> urls = new ArrayList<String>();
        String currenciesQuery = buildCurrenciesQuery();
        for (String rawProvider : rawProviders) {
            if (rawProvider == null) {
                continue;
            }
            String trimmed = rawProvider.trim();
            if (trimmed.isEmpty() || trimmed.contains(".invalid")) {
                continue;
            }
            urls.add(trimmed.replace("{CURRENCIES}", currenciesQuery));
        }
        return urls;
    }

    private String buildCurrenciesQuery() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < SUPPORTED_CURRENCY_CODES.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(SUPPORTED_CURRENCY_CODES[i].toLowerCase(Locale.US));
        }
        return builder.toString();
    }

    public String getPreferredCurrencyCode() {
        try {
            Currency currency = Currency.getInstance(Locale.getDefault());
            if (currency != null) {
                return normalizeCurrencyCode(currency.getCurrencyCode());
            }
        } catch (Exception ignored) {
        }
        return "USD";
    }

    public static String normalizeCurrencyCode(String currencyCode) {
        if (currencyCode == null) {
            return "USD";
        }
        String trimmed = currencyCode.trim().toUpperCase(Locale.US);
        return trimmed.length() == 3 ? trimmed : "USD";
    }

    public double getLastKnownRate(String currencyCode) {
        RateSnapshot snapshot = getCachedRateSnapshot(normalizeCurrencyCode(currencyCode), Long.MAX_VALUE);
        return snapshot != null ? snapshot.rate : 0.0;
    }

    public long getLastKnownRateTimestamp(String currencyCode) {
        RateSnapshot snapshot = getCachedRateSnapshot(normalizeCurrencyCode(currencyCode), Long.MAX_VALUE);
        return snapshot != null ? snapshot.timestamp : 0L;
    }

    public RateSnapshot getLastKnownRateSnapshot(String currencyCode) {
        return getCachedRateSnapshot(normalizeCurrencyCode(currencyCode), Long.MAX_VALUE);
    }

    private RateSnapshot getCachedRateSnapshot(String currencyCode, long maxAgeMs) {
        long timestamp = prefs.getLong(KEY_RATE_TIME + currencyCode, 0L);
        if (timestamp <= 0L) {
            return null;
        }
        long ageMs = System.currentTimeMillis() - timestamp;
        if (ageMs < 0L || ageMs > maxAgeMs) {
            return null;
        }
        double cachedRate = Double.longBitsToDouble(
                prefs.getLong(KEY_RATE + currencyCode, Double.doubleToLongBits(0.0))
        );
        if (cachedRate <= 0.0) {
            return null;
        }
        String source = prefs.getString(KEY_RATE_SOURCE + currencyCode, "cache");
        return new RateSnapshot(currencyCode, cachedRate, timestamp, source, ageMs > CACHE_DURATION_MS);
    }

    private double roundRateForDisplay(double rate) {
        if (rate >= 10000) {
            return Math.round(rate / 10.0) * 10.0;
        } else if (rate >= 1000) {
            return Math.round(rate / 5.0) * 5.0;
        } else {
            return Math.round(rate * 100.0) / 100.0;
        }
    }

    private void cacheRate(String currencyCode, double rate, String source) {
        long now = System.currentTimeMillis();
        prefs.edit()
                .putLong(KEY_RATE + currencyCode, Double.doubleToLongBits(rate))
                .putLong(KEY_RATE_TIME + currencyCode, now)
                .putString(KEY_RATE_SOURCE + currencyCode, source)
                .apply();
        Log.d(TAG, "Cached rate for " + currencyCode + " from " + source);
    }

    private double getManualOverride(String currencyCode) {
        return Double.longBitsToDouble(
                prefs.getLong(KEY_MANUAL_OVERRIDE + currencyCode, Double.doubleToLongBits(0.0))
        );
    }

    public boolean setManualOverride(String currencyCode, double baseRate, double overrideRate) {
        double deviation = Math.abs(overrideRate - baseRate) / baseRate;
        if (deviation > OVERRIDE_MAX_DEVIATION) {
            Log.w(TAG, "Override deviation exceeds ±2%: " + (deviation * 100) + "%");
            return false;
        }
        prefs.edit()
                .putLong(KEY_MANUAL_OVERRIDE + currencyCode, Double.doubleToLongBits(overrideRate))
                .apply();
        Log.d(TAG, "Stored manual override for " + currencyCode + ": " + overrideRate);
        return true;
    }

    public void clearManualOverride(String currencyCode) {
        prefs.edit().remove(KEY_MANUAL_OVERRIDE + currencyCode).apply();
        Log.d(TAG, "Cleared manual override for " + currencyCode);
    }

    public String getRateSource(String currencyCode) {
        return prefs.getString(KEY_RATE_SOURCE + normalizeCurrencyCode(currencyCode), "unknown");
    }

    public interface ExchangeRateCallback {
        void onRateReceived(double rate, String source);
        void onRateFailed(boolean hasOfflineFallback, String reason);
    }

    public static final class RateSnapshot {
        public final String currencyCode;
        public final double rate;
        public final long timestamp;
        public final String source;
        public final boolean stale;

        public RateSnapshot(String currencyCode, double rate, long timestamp, String source, boolean stale) {
            this.currencyCode = currencyCode;
            this.rate = rate;
            this.timestamp = timestamp;
            this.source = source;
            this.stale = stale;
        }
    }
}

