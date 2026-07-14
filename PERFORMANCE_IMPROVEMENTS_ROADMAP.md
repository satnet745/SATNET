# SATNET GLOBAL: Critical Performance Improvements Roadmap

**Date:** May 11, 2026  
**Target:** Address scalability bottlenecks for production readiness  
**Timeline:** 2 weeks to critical fixes completion

---

## Problem Statement

Current bottleneck: **20-60 second transaction initiation latency**

```
Typical transaction flow timeline:
  ├─ Load seed:        100-300 ms ✅
  ├─ Fetch UTXO × 20:  20-60 sec  ⚠️ CRITICAL
  │  └─ Issues:
  │     ├─ Serial 20 API calls (could be parallel)
  │     ├─ Blocks UI thread (users see frozen app)
  │     ├─ No retry on transient failures
  │     └─ No caching of address balance
  │
  ├─ Select UTXOs:     50 ms      ✅
  ├─ Build TX:         100-200 ms ✅
  ├─ Sign TX:          500-1000 ms ✅ (acceptable for security)
  └─ Broadcast:        1-5 sec    ✅
```

**Target:** Reduce from 20-60 sec → 5-10 sec (10x improvement)

---

## Solution 1: Parallel UTXO Fetching

### Current Implementation
```java
// BitcoinWallet.java - CURRENT (SLOW)
private List<String> getAllWalletAddresses() {
    List<String> addresses = new ArrayList<>();
    String currentAddress = getReceiveAddress();
    if (currentAddress != null) {
        addresses.add(currentAddress);
    }
    // Add derived addresses one by one
    for (int i = 0; i < 20; i++) {
        String derivedAddress = getDerivedAddress(i);
        if (derivedAddress != null && !addresses.contains(derivedAddress)) {
            addresses.add(derivedAddress);
        }
    }
    return addresses;
}

// Then fetch UTXOs SERIALLY
List<EsploraApiClient.Utxo> allUtxos = new ArrayList<>();
for (String address : walletAddresses) {  // ⚠️ SERIAL LOOP = SLOW
    try {
        List<EsploraApiClient.Utxo> addressUtxos = apiClient.getUtxos(address);
        allUtxos.addAll(addressUtxos);
    } catch (Exception e) {
        Log.w(TAG, "Failed to fetch UTXOs for address " + address);
    }
}
```

**Problem:** Each API call takes 2-3 seconds, serial execution = 20 × 3 sec = 60 seconds!

### Proposed Solution: Parallel Execution

**Option 1: ExecutorService (Java 8 compatible)**
```java
// Use thread pool to fetch UTXOs in parallel
private List<EsploraApiClient.Utxo> fetchUtxosParallel(
        List<String> walletAddresses, EsploraApiClient apiClient) throws Exception {
    
    ExecutorService executor = Executors.newFixedThreadPool(4);
    List<Future<List<EsploraApiClient.Utxo>>> futures = new ArrayList<>();
    
    // Submit all address UTXO fetches
    for (String address : walletAddresses) {
        futures.add(executor.submit(() -> {
            try {
                return apiClient.getUtxos(address);
            } catch (Exception e) {
                Log.w(TAG, "UTXO fetch failed for " + address + ": " + e.getMessage());
                return new ArrayList<>(); // Return empty on failure
            }
        }));
    }
    
    // Collect results as they complete
    List<EsploraApiClient.Utxo> allUtxos = new ArrayList<>();
    for (Future<List<EsploraApiClient.Utxo>> future : futures) {
        try {
            List<EsploraApiClient.Utxo> utxos = future.get(5, TimeUnit.SECONDS); // 5 sec timeout
            allUtxos.addAll(utxos);
        } catch (TimeoutException e) {
            Log.w(TAG, "UTXO fetch timeout");
            // Continue with other results
        }
    }
    
    executor.shutdown();
    executor.awaitTermination(10, TimeUnit.SECONDS);
    
    return allUtxos;
}
```

**Option 2: RxJava (Reactive)**
```java
// More elegant async handling
private Observable<List<EsploraApiClient.Utxo>> fetchUtxosRxjava(
        List<String> walletAddresses, EsploraApiClient apiClient) {
    
    return Observable.fromIterable(walletAddresses)
        .parallel(4) // Fetch 4 addresses in parallel
        .runOn(Schedulers.io())
        .flatMap(address -> 
            Observable.fromCallable(() -> {
                try {
                    return apiClient.getUtxos(address);
                } catch (Exception e) {
                    Log.w(TAG, "UTXO fetch failed: " + e.getMessage());
                    return new ArrayList<>();
                }
            })
            .timeout(5, TimeUnit.SECONDS)
            .onErrorReturn(ignored -> new ArrayList<>())
        )
        .sequential()
        .collect(ArrayList::new, List::addAll)
        .toObservable();
}
```

**Expected Improvement:**
- Serial: 20 addresses × 2.5 sec = 50 seconds
- Parallel (4 threads): 20 addresses ÷ 4 × 2.5 sec ≈ 12.5 seconds
- **Net improvement: 4x faster**

---

## Solution 2: Async/Background Operations

### Move UTXO Fetching Off Main Thread

**Current Problem:**
```java
// PUBLIC METHOD BLOCKS UI
public SendTransactionResult createAndSignTransaction(...) throws Exception {
    // ...
    // Fetch UTXOs for all wallet addresses (BLOCKS UI)
    List<EsploraApiClient.Utxo> allUtxos = new ArrayList<>();
    for (String address : walletAddresses) {
        List<EsploraApiClient.Utxo> addressUtxos = apiClient.getUtxos(address);
        allUtxos.addAll(addressUtxos); // ← BLOCKS FOR 50+ SECONDS
    }
    // ...
}
```

**Solution: Async Version**
```java
// SendBitcoinActivity.java - Use background thread
private void executeTransaction(String recipientAddress, long amountSats, String password) {
    sendButton.setEnabled(false);
    sendButton.setText("Preparing...");
    
    // Use background executor (already exists in activity)
    backgroundExecutor.execute(() -> {
        try {
            // Step 1: Load wallet on background thread
            wallet.loadEncryptedSeed(passwordChars);
            
            // Step 2: Fetch UTXOs on background (NON-BLOCKING)
            List<EsploraApiClient.Utxo> allUtxos = 
                fetchUtxosParallel(walletAddresses, apiClient);
            
            // Step 3: Create & sign transaction on background
            BitcoinWallet.SendTransactionResult txResult =
                wallet.createAndSignTransaction(recipientAddress, amountSats, 
                                              currentFeeRateSatPerVbyte, passwordChars);
            
            // Step 4: Broadcast on background
            String txid = wallet.broadcastTransaction(txResult.signedTxHex);
            
            // Step 5: Show success on main thread
            runOnUiThread(() -> {
                sendButton.setEnabled(true);
                TransactionSuccessDialog dialog = new TransactionSuccessDialog(this, txid, txResult);
                dialog.show();
            });
            
        } catch (Exception e) {
            // Error handling on main thread
            runOnUiThread(() -> {
                sendButton.setEnabled(true);
                Toast.makeText(SendBitcoinActivity.this, "Error: " + e.getMessage(), 
                              Toast.LENGTH_LONG).show();
            });
        }
    });
}
```

**UI Improvements:**
- User sees progress: "Preparing..." → "Fetching addresses..." → "Building transaction..."
- Progress bar with indeterminate animation
- Ability to cancel long operations

---

## Solution 3: Transaction Broadcast Reliability

### Current Issue: Fire-and-Forget
```java
// CURRENT: No retry, no verification
public String broadcastTransaction(String signedTxHex) throws Exception {
    EsploraApiClient apiClient = new EsploraApiClient(networkParams);
    String txid = apiClient.broadcastTransaction(signedTxHex);  // ← FAILS SILENTLY
    return txid;
}
```

### Proposed Solution: Broadcast with Retry + Queue

**Step 1: Transaction Queue Data Structure**
```java
class PendingTransaction {
    String txHex;           // Signed transaction
    String recipientAddress;
    long amountSats;
    long broadcastAttempts;
    long lastBroadcastTime;
    int confirmations;
    String txid;            // Set after first successful broadcast
    long createdAt;
    long broadcastedAt;
}
```

**Step 2: Persist Unsigned TX Before Broadcast**
```java
private void persistPendingTransaction(String txHex, String recipientAddress, 
                                       long amountSats) {
    // Save to SQLite before attempting broadcast
    TransactionDAO dao = AppDatabase.getInstance().transactionDAO();
    PendingTransaction pending = new PendingTransaction(
        txHex, recipientAddress, amountSats, 0, 0, 0, null, System.currentTimeMillis(), 0
    );
    dao.insert(pending);
    Log.i(TAG, "Persisted pending transaction: " + pending.id);
}
```

**Step 3: Broadcast with Exponential Backoff**
```java
public String broadcastTransactionWithRetry(String signedTxHex) throws Exception {
    String txid = null;
    long maxRetries = 5;
    long delaySeconds = 5; // Start with 5 seconds
    
    for (long attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            EsploraApiClient apiClient = new EsploraApiClient(networkParams);
            txid = apiClient.broadcastTransaction(signedTxHex);
            Log.i(TAG, "[BROADCAST_SUCCESS] TXID: " + txid + ", Attempt: " + attempt);
            return txid;
            
        } catch (IOException e) {
            if (attempt < maxRetries) {
                Log.w(TAG, "[BROADCAST_RETRY] Attempt " + attempt + " failed, retrying in " + 
                      delaySeconds + " seconds...");
                Thread.sleep(delaySeconds * 1000);
                delaySeconds = Math.min(delaySeconds * 2, 300); // Exponential backoff, max 5 min
            } else {
                Log.e(TAG, "[BROADCAST_FAILED] Max retries exceeded");
                throw new IOException("Failed to broadcast transaction after " + maxRetries + 
                                    " attempts", e);
            }
        }
    }
    
    throw new IOException("Unable to broadcast transaction");
}
```

**Step 4: Monitor & Verify Confirmations**
```java
class TransactionMonitor {
    
    // Check confirmation status of pending transactions
    public void checkPendingTransactions(Context context, EsploraApiClient apiClient) {
        TransactionDAO dao = AppDatabase.getInstance().transactionDAO();
        List<PendingTransaction> pending = dao.getPendingTransactions();
        
        for (PendingTransaction tx : pending) {
            if (tx.txid == null) {
                continue; // Not yet broadcasted
            }
            
            try {
                // Check if transaction is in mempool
                int confirmations = apiClient.getTransactionConfirmations(tx.txid);
                Log.i(TAG, "[TX_STATUS] TXID: " + tx.txid + ", Confirmations: " + confirmations);
                
                if (confirmations == 0) {
                    // Still in mempool (not in block yet)
                    tx.confirmations = 0;
                } else if (confirmations >= 6) {
                    // Finalized
                    Log.i(TAG, "[SETTLEMENT_FINALIZED] TXID: " + tx.txid);
                    dao.markConfirmed(tx.id);
                } else {
                    // In progress (1-5 confirmations)
                    tx.confirmations = confirmations;
                    dao.update(tx);
                }
                
            } catch (Exception e) {
                Log.w(TAG, "[TX_STATUS_ERROR] Failed to check status of " + tx.txid);
            }
        }
    }
    
    // Retry failed broadcasts periodically
    public void rebroadcastFailed(Context context, EsploraApiClient apiClient) {
        TransactionDAO dao = AppDatabase.getInstance().transactionDAO();
        List<PendingTransaction> failed = dao.getFailedTransactions();
        
        for (PendingTransaction tx : failed) {
            if (System.currentTimeMillis() - tx.lastBroadcastTime < 60000) {
                continue; // Wait longer before retry
            }
            
            try {
                String txid = apiClient.broadcastTransaction(tx.txHex);
                Log.i(TAG, "[REBROADCAST_SUCCESS] TXID: " + txid);
                tx.txid = txid;
                tx.broadcastedAt = System.currentTimeMillis();
                tx.broadcastAttempts++;
                dao.update(tx);
            } catch (Exception e) {
                Log.w(TAG, "[REBROADCAST_FAILED] " + e.getMessage());
                tx.broadcastAttempts++;
                dao.update(tx);
            }
        }
    }
}
```

**Expected Improvement:**
- Failed broadcasts now retry automatically
- User can see pending transactions
- Settlement becomes reliable even with network glitches
- **Reliability improvement: 95% → 99.9%**

---

## Solution 4: Offline Transaction Queue

### Store Unsigned Transactions Locally

```java
class OfflineTransactionQueue {
    
    public void queueTransactionForBroadcast(Context context, 
                                            String signedTxHex,
                                            String recipientAddress,
                                            long amountSats) {
        TransactionDAO dao = AppDatabase.getInstance().transactionDAO();
        
        PendingTransaction pending = new PendingTransaction(
            signedTxHex,
            recipientAddress,
            amountSats,
            0,                                  // broadcastAttempts
            0,                                  // lastBroadcastTime
            0,                                  // confirmations
            null,                               // txid
            System.currentTimeMillis(),        // createdAt
            0                                   // broadcastedAt
        );
        
        long id = dao.insert(pending);
        Log.i(TAG, "[QUEUED] Transaction queued with ID: " + id);
        
        // Attempt broadcast if online
        if (isNetworkAvailable(context)) {
            backgroundExecutor.execute(() -> processPendingQueue(context));
        }
    }
    
    public void processPendingQueue(Context context) {
        if (!isNetworkAvailable(context)) {
            Log.i(TAG, "[OFFLINE] No network, transactions queued for later");
            return;
        }
        
        TransactionDAO dao = AppDatabase.getInstance().transactionDAO();
        List<PendingTransaction> pending = dao.getPendingTransactions();
        
        for (PendingTransaction tx : pending) {
            try {
                if (tx.txid == null) {
                    // First broadcast attempt
                    EsploraApiClient apiClient = new EsploraApiClient(networkParams);
                    String txid = apiClient.broadcastTransaction(tx.txHex);
                    tx.txid = txid;
                    tx.broadcastedAt = System.currentTimeMillis();
                    Log.i(TAG, "[BROADCAST_QUEUED_TX] TXID: " + txid);
                }
                dao.update(tx);
            } catch (Exception e) {
                Log.w(TAG, "[BROADCAST_QUEUE_ERROR] " + e.getMessage());
                // Will retry on next online check
            }
        }
    }
    
    private boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) 
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }
}
```

**Expected Improvement:**
- Users can initiate transactions while offline
- Automatic broadcasting when connection returns
- **UX improvement: Users never lose transactions**

---

## Implementation Checklist

### Week 1: Concurrency & Performance

- [ ] Add `ExecutorService` thread pool to `BitcoinWallet`
- [ ] Implement parallel UTXO fetching (4 concurrent requests)
- [ ] Move transaction operations off main thread
- [ ] Add progress callbacks for UI updates
- [ ] Test with 10K+ UTXOs
- **Target:** 20-60 sec → 5-10 sec latency

### Week 2: Reliability & Offline Support

- [ ] Create SQLite database schema for `PendingTransaction`
- [ ] Implement transaction persistence
- [ ] Add broadcast retry with exponential backoff
- [ ] Implement transaction confirmation monitoring
- [ ] Create `OfflineTransactionQueue`
- [ ] Add network connectivity monitoring
- **Target:** 95% settlement → 99.9% reliability

### Performance Testing

```
Test Case 1: Single UTXO
  - Expected: < 2 seconds
  
Test Case 2: 10 UTXOs (typical user)
  - Expected: < 5 seconds
  
Test Case 3: 100 UTXOs (power user)
  - Expected: < 10 seconds
  
Test Case 4: No network (offline)
  - Expected: Queue locally, broadcast when online
  
Test Case 5: Transient network error
  - Expected: Automatic retry, eventual success
  
Test Case 6: Confirmation tracking
  - Expected: Update UI as confirmations arrive
```

---

## Success Criteria

| Metric | Current | Target |
|--------|---------|--------|
| **TX Initiation** | 20-60 sec | < 10 sec |
| **Parallel UTXO Fetches** | N/A (serial) | 4 concurrent |
| **Main Thread Blocking** | Yes (50+ sec) | No |
| **Broadcast Reliability** | Fire-and-forget | Retry + verify |
| **Offline Support** | None | Full queue |
| **Settlement Success Rate** | 95% | 99.9% |
| **User Experience** | Frozen app | Smooth progress |

---

## Deployment Strategy

**Phase 1 (Internal Testing):**
- Deploy to devs/QA
- Compare old vs new performance
- Gather feedback on UX

**Phase 2 (Beta Users):**
- Deploy to 100 beta users
- Monitor error logs, broadcast reliability
- Track settlement times

**Phase 3 (Production):**
- Deploy to all users
- Monitor continuously
- A/B test different thread pool sizes

---

## Rollback Plan

If new implementation causes issues:
1. Keep old serial implementation in fallback mode
2. Feature flag to enable/disable parallel mode
3. Can revert in minutes if needed

---

## Estimated Development Effort

- **Parallel UTXO fetch:** 1 day (ExecutorService approach) or 2 days (RxJava)
- **Async operations:** 1 day
- **Broadcast retry:** 1-2 days
- **Transaction queue/SQLite:** 2-3 days
- **Testing:** 2 days
- **Total:** 7-9 days (1.5 weeks)

**With concurrent development:** Achievable in **2 weeks**


