package org.servalproject.satnet.ui;

import android.content.Context;
import android.content.Intent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.bitcoin.BitcoinWallet;
import org.servalproject.satnet.SatnetRoleManager;
import org.servalproject.satnet.WalletSessionStore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class BitcoinWalletActivityTest {

    private static final String TEST_MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon about";

    private Context context;
    @Before
    public void setUp() {
        ServalBatPhoneApplication app = SatnetRuntimeTestHelper.prepareApp();
        context = SatnetRuntimeTestHelper.resetAppData(app);
        SatnetRuntimeTestHelper.setRuntimeReady(app);
        new SatnetRoleManager(context).registerAsUser();
    }

    @Test
    public void missingWalletSessionRoutesToUnlockFlow() throws Exception {
        BitcoinWallet wallet = new BitcoinWallet(context, BitcoinWalletActivity.DEFAULT_WALLET_ID, true);
        wallet.importFromMnemonic(TEST_MNEMONIC, "1234");

        Intent intent = new Intent(context, BitcoinWalletActivity.class);
        intent.putExtra(BitcoinWalletActivity.EXTRA_WALLET_ID, BitcoinWalletActivity.DEFAULT_WALLET_ID);

        try (ActivityController<BitcoinWalletActivity> controller = Robolectric.buildActivity(BitcoinWalletActivity.class, intent)) {
            BitcoinWalletActivity activity = controller.create().start().resume().get();

            SatnetRuntimeTestHelper.waitForCondition(activity::isFinishing);

            Intent nextIntent = shadowOf(activity).getNextStartedActivity();
            assertNotNull(nextIntent);
            assertNotNull(nextIntent.getComponent());
            assertEquals(BitcoinWalletSetupActivity.class.getName(), nextIntent.getComponent().getClassName());
            assertEquals(BitcoinWalletActivity.DEFAULT_WALLET_ID,
                    nextIntent.getStringExtra(BitcoinWalletActivity.EXTRA_WALLET_ID));
            assertTrue(nextIntent.getBooleanExtra(BitcoinWalletSetupActivity.EXTRA_UNLOCK_ONLY, false));
        }
    }

    @Test
    public void validWalletSessionLoadsDeterministicAddress() throws Exception {
        BitcoinWallet wallet = new BitcoinWallet(context, BitcoinWalletActivity.DEFAULT_WALLET_ID, true);
        wallet.importFromMnemonic(TEST_MNEMONIC, "1234");
        String expectedAddress = wallet.getDerivedAddress(0);
        String sessionToken = WalletSessionStore.createSession("1234");

        Intent intent = new Intent(context, BitcoinWalletActivity.class);
        intent.putExtra(BitcoinWalletActivity.EXTRA_WALLET_ID, BitcoinWalletActivity.DEFAULT_WALLET_ID);
        intent.putExtra(WalletSessionStore.EXTRA_SESSION_TOKEN, sessionToken);

        try (ActivityController<BitcoinWalletActivity> controller = Robolectric.buildActivity(BitcoinWalletActivity.class, intent)) {
            BitcoinWalletActivity activity = controller.create().start().resume().get();
            TextView addressText = activity.findViewById(R.id.address_text);

            SatnetRuntimeTestHelper.waitForCondition(() -> expectedAddress.equals(addressText.getText().toString()));

            assertFalse(activity.isFinishing());
            assertTrue((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
            assertEquals(expectedAddress, addressText.getText().toString());
            assertNull(shadowOf(activity).getNextStartedActivity());
        }
    }

    @Test
    public void merchantRoleShowsLightningActionAndRoutesToMerchantScreen() throws Exception {
        SatnetRoleManager roleManager = new SatnetRoleManager(context);
        roleManager.registerAsMerchant("Field Shop", "Retail");

        BitcoinWallet wallet = new BitcoinWallet(context, BitcoinWalletActivity.DEFAULT_WALLET_ID, true);
        wallet.importFromMnemonic(TEST_MNEMONIC, "1234");
        String expectedAddress = wallet.getDerivedAddress(0);
        String sessionToken = WalletSessionStore.createSession("1234");

        Intent intent = new Intent(context, BitcoinWalletActivity.class);
        intent.putExtra(BitcoinWalletActivity.EXTRA_WALLET_ID, BitcoinWalletActivity.DEFAULT_WALLET_ID);
        intent.putExtra(WalletSessionStore.EXTRA_SESSION_TOKEN, sessionToken);

        try (ActivityController<BitcoinWalletActivity> controller = Robolectric.buildActivity(BitcoinWalletActivity.class, intent)) {
            BitcoinWalletActivity activity = controller.create().start().resume().get();
            TextView addressText = activity.findViewById(R.id.address_text);
            Button roleActionButton = activity.findViewById(R.id.role_action_button);

            SatnetRuntimeTestHelper.waitForCondition(() -> expectedAddress.equals(addressText.getText().toString()));

            assertEquals(context.getString(R.string.satnet_wallet_role_action_merchant), roleActionButton.getText().toString());
            assertTrue(roleActionButton.isEnabled());

            roleActionButton.performClick();

            Intent nextIntent = shadowOf(activity).getNextStartedActivity();
            assertNotNull(nextIntent);
            assertNotNull(nextIntent.getComponent());
            assertEquals(MerchantLightningActivity.class.getName(), nextIntent.getComponent().getClassName());
            assertEquals(BitcoinWalletActivity.DEFAULT_WALLET_ID,
                    nextIntent.getStringExtra(BitcoinWalletActivity.EXTRA_WALLET_ID));
        }
    }

    @Test
    public void multiRoleWalletUsesActiveRoleForPrimaryAction() throws Exception {
        SatnetRoleManager roleManager = new SatnetRoleManager(context);
        roleManager.registerAsMerchant("Field Shop", "Retail");
        roleManager.registerAsVerifier();
        roleManager.switchRole(SatnetRoleManager.ROLE_VERIFIER);

        BitcoinWallet wallet = new BitcoinWallet(context, BitcoinWalletActivity.DEFAULT_WALLET_ID, true);
        wallet.importFromMnemonic(TEST_MNEMONIC, "1234");
        String expectedAddress = wallet.getDerivedAddress(0);
        String sessionToken = WalletSessionStore.createSession("1234");

        Intent intent = new Intent(context, BitcoinWalletActivity.class);
        intent.putExtra(BitcoinWalletActivity.EXTRA_WALLET_ID, BitcoinWalletActivity.DEFAULT_WALLET_ID);
        intent.putExtra(WalletSessionStore.EXTRA_SESSION_TOKEN, sessionToken);

        try (ActivityController<BitcoinWalletActivity> controller = Robolectric.buildActivity(BitcoinWalletActivity.class, intent)) {
            BitcoinWalletActivity activity = controller.create().start().resume().get();
            TextView addressText = activity.findViewById(R.id.address_text);
            Button roleActionButton = activity.findViewById(R.id.role_action_button);

            SatnetRuntimeTestHelper.waitForCondition(() -> expectedAddress.equals(addressText.getText().toString()));

            assertEquals(context.getString(R.string.satnet_wallet_role_action_verifier), roleActionButton.getText().toString());

            roleActionButton.performClick();

            Intent nextIntent = shadowOf(activity).getNextStartedActivity();
            assertNotNull(nextIntent);
            assertNotNull(nextIntent.getComponent());
            assertEquals(VerifierDashboardActivity.class.getName(), nextIntent.getComponent().getClassName());
        }
    }

    @Test
    public void suspendedMerchantFallsBackToRedeemAction() throws Exception {
        SatnetRoleManager roleManager = new SatnetRoleManager(context);
        roleManager.registerAsMerchant("Field Shop", "Retail");
        roleManager.updateRoleStatus(SatnetRoleManager.ROLE_MERCHANT,
                SatnetRoleManager.ROLE_STATUS_SUSPENDED,
                "Manual review required");

        BitcoinWallet wallet = new BitcoinWallet(context, BitcoinWalletActivity.DEFAULT_WALLET_ID, true);
        wallet.importFromMnemonic(TEST_MNEMONIC, "1234");
        String expectedAddress = wallet.getDerivedAddress(0);
        String sessionToken = WalletSessionStore.createSession("1234");

        Intent intent = new Intent(context, BitcoinWalletActivity.class);
        intent.putExtra(BitcoinWalletActivity.EXTRA_WALLET_ID, BitcoinWalletActivity.DEFAULT_WALLET_ID);
        intent.putExtra(WalletSessionStore.EXTRA_SESSION_TOKEN, sessionToken);

        try (ActivityController<BitcoinWalletActivity> controller = Robolectric.buildActivity(BitcoinWalletActivity.class, intent)) {
            BitcoinWalletActivity activity = controller.create().start().resume().get();
            TextView addressText = activity.findViewById(R.id.address_text);
            Button roleActionButton = activity.findViewById(R.id.role_action_button);

            SatnetRuntimeTestHelper.waitForCondition(() -> expectedAddress.equals(addressText.getText().toString()));

            assertEquals(context.getString(R.string.satnet_wallet_role_action_redeem), roleActionButton.getText().toString());

            roleActionButton.performClick();

            Intent nextIntent = shadowOf(activity).getNextStartedActivity();
            assertNotNull(nextIntent);
            assertNotNull(nextIntent.getComponent());
            assertEquals(VoucherRedemptionActivity.class.getName(), nextIntent.getComponent().getClassName());
        }
    }

    @Test
    public void panicModeToggleHidesBalanceAndLocksSensitiveActions() throws Exception {
        BitcoinWallet wallet = new BitcoinWallet(context, BitcoinWalletActivity.DEFAULT_WALLET_ID, true);
        wallet.importFromMnemonic(TEST_MNEMONIC, "1234");
        String expectedAddress = wallet.getDerivedAddress(0);
        String sessionToken = WalletSessionStore.createSession("1234");

        Intent intent = new Intent(context, BitcoinWalletActivity.class);
        intent.putExtra(BitcoinWalletActivity.EXTRA_WALLET_ID, BitcoinWalletActivity.DEFAULT_WALLET_ID);
        intent.putExtra(WalletSessionStore.EXTRA_SESSION_TOKEN, sessionToken);

        try (ActivityController<BitcoinWalletActivity> controller = Robolectric.buildActivity(BitcoinWalletActivity.class, intent)) {
            BitcoinWalletActivity activity = controller.create().start().resume().get();
            TextView addressText = activity.findViewById(R.id.address_text);
            TextView balanceText = activity.findViewById(R.id.balance_text);
            Button panicButton = activity.findViewById(R.id.panic_mode_button);
            Button hideBalanceButton = activity.findViewById(R.id.hide_balance_button);
            Button sendButton = activity.findViewById(R.id.send_bitcoin_button);
            Button shareAddressButton = activity.findViewById(R.id.share_address_chat_button);
            Button roleActionButton = activity.findViewById(R.id.role_action_button);
            Button backupButton = activity.findViewById(R.id.backup_button);

            SatnetRuntimeTestHelper.waitForCondition(() -> expectedAddress.equals(addressText.getText().toString()));

            assertEquals(context.getString(R.string.satnet_wallet_panic_enable), panicButton.getText().toString());
            assertTrue(hideBalanceButton.isEnabled());
            assertTrue(sendButton.isEnabled());

            invokePrivateVoidMethod(activity, "enablePanicMode");

            SatnetRuntimeTestHelper.waitForCondition(() ->
                    context.getString(R.string.satnet_wallet_zero_balance).equals(balanceText.getText().toString()));

            assertEquals(context.getString(R.string.satnet_wallet_panic_disable), panicButton.getText().toString());
            assertFalse(hideBalanceButton.isEnabled());
            assertFalse(sendButton.isEnabled());
            assertFalse(shareAddressButton.isEnabled());
            assertFalse(roleActionButton.isEnabled());
            assertFalse(backupButton.isEnabled());
            assertFalse(addressText.isEnabled());
            assertEquals(context.getString(R.string.satnet_wallet_panic_address_hidden), addressText.getText().toString());

            invokePrivateVoidMethod(activity, "disablePanicMode");

            SatnetRuntimeTestHelper.waitForCondition(() ->
                    !context.getString(R.string.satnet_wallet_zero_balance).equals(balanceText.getText().toString()));

            assertEquals(context.getString(R.string.satnet_wallet_panic_enable), panicButton.getText().toString());
            assertTrue(hideBalanceButton.isEnabled());
            assertTrue(sendButton.isEnabled());
            assertNotEquals(context.getString(R.string.satnet_wallet_zero_balance), balanceText.getText().toString());
            assertEquals(expectedAddress, addressText.getText().toString());
        }
    }

    @Test
    public void panicModePersistsAcrossRelaunch() throws Exception {
        BitcoinWallet wallet = new BitcoinWallet(context, BitcoinWalletActivity.DEFAULT_WALLET_ID, true);
        wallet.importFromMnemonic(TEST_MNEMONIC, "1234");
        String expectedAddress = wallet.getDerivedAddress(0);

        Intent firstIntent = new Intent(context, BitcoinWalletActivity.class);
        firstIntent.putExtra(BitcoinWalletActivity.EXTRA_WALLET_ID, BitcoinWalletActivity.DEFAULT_WALLET_ID);
        firstIntent.putExtra(WalletSessionStore.EXTRA_SESSION_TOKEN, WalletSessionStore.createSession("1234"));

        try (ActivityController<BitcoinWalletActivity> firstController = Robolectric.buildActivity(BitcoinWalletActivity.class, firstIntent)) {
            BitcoinWalletActivity firstActivity = firstController.create().start().resume().get();
            TextView firstAddressText = firstActivity.findViewById(R.id.address_text);

            SatnetRuntimeTestHelper.waitForCondition(() -> expectedAddress.equals(firstAddressText.getText().toString()));
            invokePrivateVoidMethod(firstActivity, "enablePanicMode");
            SatnetRuntimeTestHelper.waitForCondition(() ->
                    context.getString(R.string.satnet_wallet_panic_address_hidden).equals(firstAddressText.getText().toString()));
        }

        Intent secondIntent = new Intent(context, BitcoinWalletActivity.class);
        secondIntent.putExtra(BitcoinWalletActivity.EXTRA_WALLET_ID, BitcoinWalletActivity.DEFAULT_WALLET_ID);
        secondIntent.putExtra(WalletSessionStore.EXTRA_SESSION_TOKEN, WalletSessionStore.createSession("1234"));

        try (ActivityController<BitcoinWalletActivity> secondController = Robolectric.buildActivity(BitcoinWalletActivity.class, secondIntent)) {
            BitcoinWalletActivity secondActivity = secondController.create().start().resume().get();
            TextView secondAddressText = secondActivity.findViewById(R.id.address_text);
            TextView secondBalanceText = secondActivity.findViewById(R.id.balance_text);
            Button panicButton = secondActivity.findViewById(R.id.panic_mode_button);
            Button sendButton = secondActivity.findViewById(R.id.send_bitcoin_button);

            SatnetRuntimeTestHelper.waitForCondition(() ->
                    context.getString(R.string.satnet_wallet_zero_balance).equals(secondBalanceText.getText().toString()));

            assertEquals(context.getString(R.string.satnet_wallet_panic_disable), panicButton.getText().toString());
            assertEquals(context.getString(R.string.satnet_wallet_panic_address_hidden), secondAddressText.getText().toString());
            assertFalse(sendButton.isEnabled());
        }
    }

    private static void invokePrivateVoidMethod(Object target, String methodName) throws Exception {
        java.lang.reflect.Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }
}

