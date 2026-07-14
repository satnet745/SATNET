package org.servalproject.satnet.ui;

import android.content.Context;
import android.content.Intent;
import android.widget.RadioButton;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class SatnetRoleRoutingTest {

    private Context context;

    @Before
    public void setUp() {
        ServalBatPhoneApplication app = SatnetRuntimeTestHelper.prepareApp();
        context = SatnetRuntimeTestHelper.resetAppData(app);
        SatnetRuntimeTestHelper.setRuntimeReady(app);
    }

    @Test
    public void existingRoleAndWalletRouteToUnlockFlow() throws Exception {
        SatnetRoleManager roleManager = new SatnetRoleManager(context);
        roleManager.registerAsUser();

        BitcoinWallet wallet = new BitcoinWallet(context, BitcoinWalletActivity.DEFAULT_WALLET_ID, true);
        wallet.importFromMnemonic(
                "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
                "1234");

        try (ActivityController<SatnetRoleSetupActivity> controller = Robolectric.buildActivity(SatnetRoleSetupActivity.class)) {
            SatnetRoleSetupActivity activity = controller.create()
                    .start()
                    .resume()
                    .get();

            Intent nextIntent = shadowOf(activity).getNextStartedActivity();
            assertNotNull(nextIntent);
            assertNotNull(nextIntent.getComponent());
            assertEquals(BitcoinWalletSetupActivity.class.getName(), nextIntent.getComponent().getClassName());
            assertTrue(nextIntent.getBooleanExtra(BitcoinWalletSetupActivity.EXTRA_UNLOCK_ONLY, false));
            assertEquals(BitcoinWalletActivity.DEFAULT_WALLET_ID,
                    nextIntent.getStringExtra(BitcoinWalletActivity.EXTRA_WALLET_ID));
            assertTrue(activity.isFinishing());
        }
    }

    @Test
    public void existingRoleWithoutWalletRoutesToWalletSetupWithoutUnlockFlag() {
        SatnetRoleManager roleManager = new SatnetRoleManager(context);
        roleManager.registerAsAgent("Agent", "Kampala");

        try (ActivityController<SatnetRoleSetupActivity> controller = Robolectric.buildActivity(SatnetRoleSetupActivity.class)) {
            SatnetRoleSetupActivity activity = controller.create()
                    .start()
                    .resume()
                    .get();

            Intent nextIntent = shadowOf(activity).getNextStartedActivity();
            assertNotNull(nextIntent);
            assertNotNull(nextIntent.getComponent());
            assertEquals(BitcoinWalletSetupActivity.class.getName(), nextIntent.getComponent().getClassName());
            assertFalse(nextIntent.getBooleanExtra(BitcoinWalletSetupActivity.EXTRA_UNLOCK_ONLY, false));
        }
    }

    @Test
    public void merchantRoleOptionIsEnabledOnRoleSetupWhenLightningEnabled() {
        try (ActivityController<SatnetRoleSetupActivity> controller = Robolectric.buildActivity(SatnetRoleSetupActivity.class)) {
            SatnetRoleSetupActivity activity = controller.create()
                    .start()
                    .resume()
                    .get();

            RadioButton merchantRoleButton = activity.findViewById(R.id.role_merchant);

            assertNotNull(merchantRoleButton);
            assertTrue(merchantRoleButton.isEnabled());
            assertTrue(merchantRoleButton.getAlpha() > 0.9f);
        }
    }

    @Test
    public void manageExistingRolesModeDoesNotAutoRouteAway() {
        SatnetRoleManager roleManager = new SatnetRoleManager(context);
        roleManager.registerAsAgent("Agent", "Kampala");

        Intent intent = new Intent(context, SatnetRoleSetupActivity.class);
        intent.putExtra(SatnetRoleSetupActivity.EXTRA_MANAGE_EXISTING_ROLES, true);

        try (ActivityController<SatnetRoleSetupActivity> controller = Robolectric.buildActivity(SatnetRoleSetupActivity.class, intent)) {
            SatnetRoleSetupActivity activity = controller.create()
                    .start()
                    .resume()
                    .get();

            assertFalse(activity.isFinishing());
            assertNull(shadowOf(activity).getNextStartedActivity());
        }
    }
}

