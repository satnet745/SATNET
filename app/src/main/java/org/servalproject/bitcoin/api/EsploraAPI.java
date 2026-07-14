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

package org.servalproject.bitcoin.api;

import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

/**
 * Esplora API integration for SATNET AFRICA
 *
 * Features:
 * - Query Bitcoin balance (no authentication needed)
 * - Broadcast transactions
 * - Offline fallback support
 * - Works with both mainnet and testnet
 */
public class EsploraAPI {
    private static final String TAG = "EsploraAPI";

    // Esplora API endpoints
    private static final String MAINNET_API = "https://blockstream.info/api";
    private static final String TESTNET_API = "https://blockstream.info/testnet/api";

    private OkHttpClient httpClient;
    private String apiEndpoint;
    private boolean testnet;

    /**
     * Initialize Esplora API client
     * @param testnet If true, use testnet; if false, use mainnet
     */
    public EsploraAPI(boolean testnet) {
        this.testnet = testnet;
        this.apiEndpoint = testnet ? TESTNET_API : MAINNET_API;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        Log.d(TAG, "Initialized Esplora API (" + (testnet ? "testnet" : "mainnet") + ")");
    }

    /**
     * Query balance for Bitcoin address
     * @param address Bitcoin address
     * @param callback Callback with balance in satoshis
     */
    public void getBalance(String address, BalanceCallback callback) {
        String url = apiEndpoint + "/address/" + address;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Error querying balance: " + e.getMessage());
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        callback.onError("HTTP " + response.code());
                        return;
                    }

                    String responseBody = response.body().string();
                    JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

                    // Sum confirmed balance from all address stats
                    long balanceSatoshis = 0;

                    if (json.has("chain_stats")) {
                        JsonObject chainStats = json.getAsJsonObject("chain_stats");
                        balanceSatoshis += chainStats.get("funded_txo_sum").getAsLong();
                        balanceSatoshis -= chainStats.get("spent_txo_sum").getAsLong();
                    }

                    Log.d(TAG, "Balance for " + address + ": " + balanceSatoshis + " sats");
                    callback.onBalanceReceived(balanceSatoshis);

                } catch (Exception e) {
                    Log.e(TAG, "Error parsing balance response", e);
                    callback.onError("Parse error: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Broadcast signed transaction to network
     * @param signedTxHex Signed transaction in hex format
     * @param callback Callback with transaction hash
     */
    public void broadcastTransaction(String signedTxHex, BroadcastCallback callback) {
        String url = apiEndpoint + "/tx";

        RequestBody body = RequestBody.create(signedTxHex.getBytes());

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Error broadcasting transaction: " + e.getMessage());
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        String error = response.body().string();
                        Log.e(TAG, "Broadcast failed: " + error);
                        callback.onError("HTTP " + response.code() + ": " + error);
                        return;
                    }

                    String txHash = response.body().string();
                    Log.d(TAG, "Transaction broadcast successfully: " + txHash);
                    callback.onBroadcastSuccess(txHash);

                } catch (Exception e) {
                    Log.e(TAG, "Error parsing broadcast response", e);
                    callback.onError("Parse error: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Get transaction details
     * @param txHash Transaction hash
     * @param callback Callback with transaction details
     */
    public void getTransaction(String txHash, TransactionCallback callback) {
        String url = apiEndpoint + "/tx/" + txHash;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Error querying transaction: " + e.getMessage());
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        callback.onError("HTTP " + response.code());
                        return;
                    }

                    String responseBody = response.body().string();
                    JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

                    // Extract transaction details
                    long fee = json.has("fee") ? json.get("fee").getAsLong() : 0;
                    boolean confirmed = json.has("status") &&
                                      json.getAsJsonObject("status").has("confirmed") &&
                                      json.getAsJsonObject("status").get("confirmed").getAsBoolean();

                    callback.onTransactionReceived(fee, confirmed);

                } catch (Exception e) {
                    Log.e(TAG, "Error parsing transaction response", e);
                    callback.onError("Parse error: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Callback for balance queries
     */
    public interface BalanceCallback {
        void onBalanceReceived(long satoshis);
        void onError(String error);
    }

    /**
     * Callback for transaction broadcasts
     */
    public interface BroadcastCallback {
        void onBroadcastSuccess(String txHash);
        void onError(String error);
    }

    /**
     * Callback for transaction details
     */
    public interface TransactionCallback {
        void onTransactionReceived(long fee, boolean confirmed);
        void onError(String error);
    }
}

