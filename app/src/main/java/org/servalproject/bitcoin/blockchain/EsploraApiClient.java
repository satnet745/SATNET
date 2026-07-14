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

package org.servalproject.bitcoin.blockchain;

import android.util.Log;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Esplora API client for Bitcoin blockchain operations
 *
 * Features:
 * - UTXO fetching for addresses
 * - Transaction broadcasting
 * - Fee estimation
 * - Balance querying
 * - Transaction status checking
 */
public class EsploraApiClient {
    private static final String TAG = "EsploraApiClient";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final List<String> baseUrls;
    private int currentUrlIndex = 0;
    private final NetworkParameters networkParams;

    public EsploraApiClient(NetworkParameters networkParams) {
        this.networkParams = networkParams;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        // Initialize with multiple API endpoints for failover
        this.baseUrls = new ArrayList<>();
        boolean isMainnet = networkParams.getId().equals(NetworkParameters.ID_MAINNET);

        // Primary endpoints
        if (isMainnet) {
            baseUrls.add("https://blockstream.info/api");
            baseUrls.add("https://mempool.space/api");
            baseUrls.add("https://esplora.blockstream.com/api");
        } else {
            baseUrls.add("https://blockstream.info/testnet/api");
            baseUrls.add("https://mempool.space/testnet/api");
        }

        Log.d(TAG, "Initialized with " + baseUrls.size() + " API endpoints for " + (isMainnet ? "mainnet" : "testnet"));
    }

    /**
     * UTXO data structure
     */
    public static class Utxo {
        public final String txid;
        public final int vout;
        public final long value; // satoshis
        public final String scriptPubKey;
        public final int confirmations;
        private final NetworkParameters networkParams;

        public Utxo(String txid, int vout, long value, String scriptPubKey, int confirmations, NetworkParameters networkParams) {
            this.txid = txid;
            this.vout = vout;
            this.value = value;
            this.scriptPubKey = scriptPubKey;
            this.confirmations = confirmations;
            this.networkParams = networkParams;
        }

        public TransactionOutPoint getOutPoint() {
            // Convert hex txid to bytes in reverse order (little-endian)
            byte[] txidBytes = hexToBytes(txid);
            // Reverse for Bitcoin byte order
            reverseBytes(txidBytes);
            return new TransactionOutPoint(networkParams, (long)vout, Sha256Hash.wrap(txidBytes));
        }

        private static byte[] hexToBytes(String hex) {
            int len = hex.length();
            byte[] data = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                        + Character.digit(hex.charAt(i + 1), 16));
            }
            return data;
        }

        private static void reverseBytes(byte[] bytes) {
            for (int i = 0; i < bytes.length / 2; i++) {
                byte temp = bytes[i];
                bytes[i] = bytes[bytes.length - 1 - i];
                bytes[bytes.length - 1 - i] = temp;
            }
        }

        public Coin getValue() {
            return Coin.valueOf(value);
        }
    }

    /**
     * Fee estimation data
     */
    public static class FeeEstimate {
        public final int targetBlocks;
        public final long satPerVbyte;

        public FeeEstimate(int targetBlocks, long satPerVbyte) {
            this.targetBlocks = targetBlocks;
            this.satPerVbyte = satPerVbyte;
        }

        public Coin getFeePerKb() {
            return Coin.valueOf(satPerVbyte * 1000);
        }
    }

    /**
     * Fetch UTXOs for an address
     */
    public List<Utxo> getUtxos(String address) throws IOException, JSONException {
        String url = baseUrls.get(currentUrlIndex) + "/address/" + address + "/utxo";
        Request request = new Request.Builder().url(url).build();

        try (Response response = executeWithFailover(request)) {
            String responseBody = response.body().string();
            JSONArray utxoArray = new JSONArray(responseBody);
            List<Utxo> utxos = new ArrayList<>();

            for (int i = 0; i < utxoArray.length(); i++) {
                JSONObject utxoObj = utxoArray.getJSONObject(i);
                Utxo utxo = new Utxo(
                        utxoObj.getString("txid"),
                        utxoObj.getInt("vout"),
                        utxoObj.getLong("value"),
                        utxoObj.optString("scriptpubkey", ""),
                        utxoObj.optInt("status", 0), // confirmations
                        networkParams
                );
                utxos.add(utxo);
            }

            Log.d(TAG, "Fetched " + utxos.size() + " UTXOs for address " + address);
            return utxos;
        }
    }

    /**
     * Get current balance for an address
     */
    public long getBalance(String address) throws IOException, JSONException {
        List<Utxo> utxos = getUtxos(address);
        long total = 0L;
        for (Utxo utxo : utxos) {
            total += utxo.value;
        }
        return total;
    }

    /**
     * Broadcast a signed transaction
     */
    public String broadcastTransaction(String hexTransaction) throws IOException, JSONException {
        String url = baseUrls.get(currentUrlIndex) + "/tx";
        RequestBody body = RequestBody.create(hexTransaction, MediaType.get("text/plain"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = executeWithFailover(request)) {
            String txid = response.body().string().trim();
            Log.d(TAG, "Broadcasted transaction: " + txid);
            return txid;
        }
    }

    /**
     * Get fee estimates for different confirmation targets
     */
    public List<FeeEstimate> getFeeEstimates() throws IOException, JSONException {
        String url = baseUrls.get(currentUrlIndex) + "/fee-estimates";
        Request request = new Request.Builder().url(url).build();

        try (Response response = executeWithFailover(request)) {
            String responseBody = response.body().string();
            JSONObject feeObj = new JSONObject(responseBody);
            List<FeeEstimate> estimates = new ArrayList<>();

            // Common confirmation targets
            int[] targets = {1, 3, 6, 10, 20};
            for (int target : targets) {
                String key = String.valueOf(target);
                if (feeObj.has(key)) {
                    long satPerVbyte = (long) (feeObj.getDouble(key) * 100000); // Convert BTC/kvB to sat/vbyte
                    estimates.add(new FeeEstimate(target, satPerVbyte));
                }
            }

            Log.d(TAG, "Fetched " + estimates.size() + " fee estimates");
            return estimates;
        }
    }

    /**
     * Get recommended fee rate for target confirmations
     */
    public long getRecommendedFeeRate(int targetBlocks) throws IOException, JSONException {
        List<FeeEstimate> estimates = getFeeEstimates();
        for (FeeEstimate estimate : estimates) {
            if (estimate.targetBlocks == targetBlocks) {
                return estimate.satPerVbyte;
            }
        }
        // Fallback to conservative estimate
        return 10; // 10 sat/vbyte minimum
    }

    /**
     * Check transaction confirmation status
     */
    public int getTransactionConfirmations(String txid) throws IOException, JSONException {
        String url = baseUrls.get(currentUrlIndex) + "/tx/" + txid + "/status";
        Request request = new Request.Builder().url(url).build();

        try (Response response = executeWithFailover(request)) {
            String responseBody = response.body().string();
            JSONObject statusObj = new JSONObject(responseBody);
            int confirmed = statusObj.optInt("confirmed", 0);
            return confirmed != 0 ? 1 : 0; // Simplified: just confirmed/unconfirmed
        } catch (IOException e) {
            // For transaction status, 404 means not found (unconfirmed)
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                return 0;
            }
            throw e;
        }
    }

    /**
     * Execute HTTP request with automatic failover to backup endpoints
     */
    private Response executeWithFailover(Request request) throws IOException {
        IOException lastException = null;

        for (int attempt = 0; attempt < baseUrls.size(); attempt++) {
            try {
                // Rotate through available endpoints
                currentUrlIndex = (currentUrlIndex + attempt) % baseUrls.size();

                // Update URL for this endpoint
                String originalUrl = request.url().toString();
                String newUrl = originalUrl.replaceFirst("https://[^/]+", baseUrls.get(currentUrlIndex));
                Request failoverRequest = request.newBuilder().url(newUrl).build();

                Log.d(TAG, "Trying endpoint: " + baseUrls.get(currentUrlIndex));

                Response response = httpClient.newCall(failoverRequest).execute();
                if (response.isSuccessful()) {
                    return response; // Success - return the response
                }

                // Close unsuccessful response
                response.close();

                // If we get here, the response was not successful
                String errorMsg = "HTTP " + response.code() + " from " + baseUrls.get(currentUrlIndex);
                lastException = new IOException(errorMsg);

            } catch (IOException e) {
                lastException = e;
                Log.w(TAG, "Failed endpoint " + baseUrls.get(currentUrlIndex) + ": " + e.getMessage());
            }
        }

        // All endpoints failed
        throw new IOException("All API endpoints failed. Last error: " +
                (lastException != null ? lastException.getMessage() : "Unknown error"));
    }
}
