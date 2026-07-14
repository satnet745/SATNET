# String Resources for On-Chain Bitcoin Transfer Feature

Add the following strings to `app/src/main/res/values/satnet_strings.xml`:

```xml
<!-- Send Bitcoin Feature -->
<string name="satnet_send_bitcoin_title">Send Bitcoin</string>
<string name="satnet_send_recipient_label">Recipient Bitcoin Address</string>
<string name="satnet_send_recipient_hint">bc1q... or 1... or 3...</string>
<string name="satnet_send_amount_label">Amount to Send</string>
<string name="satnet_send_amount_btc_hint">0.00000000 BTC</string>
<string name="satnet_send_amount_sats_hint">0 sats</string>
<string name="satnet_send_fee_rate_label">Transaction Fee Rate</string>
<string name="satnet_send_slow">Slow</string>
<string name="satnet_send_fast">Fast</string>
<string name="satnet_send_estimate_fee_button">Estimate Fees</string>
<string name="satnet_send_preview_button">Preview</string>
<string name="satnet_send_button">Send Bitcoin</string>
<string name="satnet_send_cancel">Cancel</string>
<string name="satnet_send_confirm">Confirm</string>
<string name="satnet_send_close">Close</string>
<string name="satnet_send_copy">Copy</string>

<!-- Fee Estimation -->
<string name="satnet_send_estimating_fees">Estimating fees...</string>
<string name="satnet_send_fees_updated">Network fees updated</string>
<string name="satnet_send_fee_estimation_failed">Failed to estimate fees: %1$s</string>

<!-- Transaction Preview -->
<string name="satnet_send_preview_title">Transaction Preview</string>
<string name="satnet_send_loading_preview">Loading transaction preview...</string>
<string name="satnet_send_enter_recipient">Please enter a recipient address</string>
<string name="satnet_send_enter_amount">Please enter an amount</string>
<string name="satnet_send_preview_failed">Failed to create preview: %1$s</string>

<!-- Transaction Execution -->
<string name="satnet_send_incomplete_form">Please fill in all fields</string>
<string name="satnet_send_broadcasting">Broadcasting...</string>
<string name="satnet_send_broadcast_failed">Failed to broadcast: %1$s</string>
<string name="satnet_send_enter_password">Enter Your Password</string>
<string name="satnet_send_password_hint">Your password is required to sign this transaction</string>
<string name="satnet_send_password_required">Password is required</string>
<string name="satnet_send_error">Error: %1$s</string>

<!-- Success Messages -->
<string name="satnet_send_success">✓ Bitcoin Sent</string>
<string name="satnet_send_txid_label">Transaction ID</string>
<string name="satnet_send_txid_copied">Transaction ID copied to clipboard</string>
<string name="satnet_send_address_pasted">Address pasted from clipboard</string>
<string name="satnet_send_clipboard_error">Unable to access clipboard</string>
<string name="satnet_send_loading">Loading...</string>

<!-- Menu Items -->
<string name="satnet_send_menu_paste_address">Paste Address</string>
```

## Add to Menu Resource

Create `app/src/main/res/menu/send_bitcoin_menu.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    
    <item
        android:id="@+id/menu_paste_address"
        android:title="@string/satnet_send_menu_paste_address"
        app:showAsAction="ifRoom" />
</menu>
```

## Update AndroidManifest.xml

Add the SendBitcoinActivity to the manifest:

```xml
<activity
    android:name="org.servalproject.satnet.ui.SendBitcoinActivity"
    android:label="@string/satnet_send_bitcoin_title"
    android:exported="false"
    android:windowSoftInputMode="stateHidden" />
```

## Update BitcoinWalletActivity

Add button and menu item to launch SendBitcoinActivity:

```java
// In onCreate():
Button sendBitcoinButton = findViewById(R.id.send_bitcoin_button);
sendBitcoinButton.setOnClickListener(v -> {
    Intent intent = new Intent(this, SendBitcoinActivity.class);
    intent.putExtra(SendBitcoinActivity.EXTRA_WALLET_ID, walletId);
    if (walletSessionToken != null) {
        intent.putExtra(WalletSessionStore.EXTRA_SESSION_TOKEN, walletSessionToken);
    }
    startActivity(intent);
});

// In wallet layout (activity_bitcoin_wallet.xml):
<Button
    android:id="@+id/send_bitcoin_button"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="@string/satnet_send_bitcoin_title" />
```

## Integration Points

### With BitcoinWallet
- ✅ Uses existing wallet for UTXO scanning
- ✅ Uses existing encryption for key protection
- ✅ Uses existing HD path derivation

### With Network Parameters
- ✅ Respects mainnet/testnet configuration
- ✅ Uses existing Bitcoin network setup

### With UI Framework
- ✅ Extends AppCompatActivity (existing pattern)
- ✅ Uses SatnetUiSupport utilities
- ✅ Follows existing security window setup

## Backend Resources Required

All existing:
- ✅ bitcoinj library
- ✅ OkHttp for API calls
- ✅ Wallet encryption system
- ✅ Network parameter configuration

No additional dependencies needed.

