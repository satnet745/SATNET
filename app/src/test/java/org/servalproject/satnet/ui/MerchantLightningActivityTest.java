package org.servalproject.satnet.ui;

import android.content.Context;
import android.content.Intent;
import android.view.WindowManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.features.FeatureFlags;
import org.servalproject.satnet.SatnetRoleManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class MerchantLightningActivityTest {

    private Context context;
    private ServalBatPhoneApplication app;

    @Before
    public void setUp() {
        app = SatnetRuntimeTestHelper.prepareApp();
        context = SatnetRuntimeTestHelper.resetAppData(app);
    }

    @Test
    public void merchantRegistrationSucceedsWhenLightningEnabledByDefault() {
        SatnetRuntimeTestHelper.setRuntimeReady(app);
        assertTrue(FeatureFlags.isLightningEnabled());

        SatnetRoleManager roleManager = new SatnetRoleManager(context);
        roleManager.registerAsMerchant("Field Shop", "Retail");

        assertTrue(roleManager.canActAsMerchant());
        assertTrue(roleManager.hasFeature("accept_lightning"));
        assertEquals("Field Shop", roleManager.getMerchantName());
        assertEquals("Retail", roleManager.getMerchantType());
    }

    @Test
    public void merchantActivityStaysOpenForRegisteredMerchantWhenLightningEnabled() {
        SatnetRuntimeTestHelper.setRuntimeWarming(app);
        assertTrue(FeatureFlags.isLightningEnabled());
        new SatnetRoleManager(context).registerAsMerchant("Field Shop", "Retail");

        try (ActivityController<MerchantLightningActivity> controller = Robolectric.buildActivity(
                MerchantLightningActivity.class,
                new Intent(context, MerchantLightningActivity.class))) {
            MerchantLightningActivity activity = controller.create().start().resume().get();

            assertFalse(activity.isFinishing());
            assertTrue((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
            assertNotNull(activity.findViewById(R.id.generate_invoice_button));
            assertNotNull(activity.findViewById(R.id.amount_sats_input));
            assertNull(org.robolectric.shadows.ShadowToast.getLatestToast());
        }
    }

    @Test
    public void suspendedMerchantCannotEnterMerchantActivity() {
        SatnetRuntimeTestHelper.setRuntimeReady(app);
        assertTrue(FeatureFlags.isLightningEnabled());
        SatnetRoleManager roleManager = new SatnetRoleManager(context);
        roleManager.registerAsMerchant("Field Shop", "Retail");
        roleManager.updateRoleStatus(SatnetRoleManager.ROLE_MERCHANT,
                SatnetRoleManager.ROLE_STATUS_SUSPENDED,
                "Manual review required");

        try (ActivityController<MerchantLightningActivity> controller = Robolectric.buildActivity(
                MerchantLightningActivity.class,
                new Intent(context, MerchantLightningActivity.class))) {
            MerchantLightningActivity activity = controller.create().start().resume().get();

            assertTrue(activity.isFinishing());
        }
    }

}


