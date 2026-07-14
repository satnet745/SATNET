package org.servalproject.satnet.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.servalproject.R;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class VoucherRedemptionActivityDeviceSmokeTest {

    @Rule
    public final GrantPermissionRule cameraPermissionRule =
            GrantPermissionRule.grant(Manifest.permission.CAMERA);

    @Before
    public void prepareRuntime() {
        SatnetDeviceTestHelper.prepareReadyUserRole();
    }

    @Test
    public void voucherRedemptionLaunchesWithInteractiveControls() {
        Intent intent = new Intent(SatnetDeviceTestHelper.targetContext(), VoucherRedemptionActivity.class);
        try (ActivityScenario<VoucherRedemptionActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.voucher_stage_badge_text)).check(matches(isDisplayed()));
            onView(withId(R.id.voucher_runtime_status_text)).check(matches(isDisplayed()));
            onView(withId(R.id.scan_button)).check(matches(isDisplayed())).check(matches(isEnabled()));
            onView(withId(R.id.manual_entry_button)).check(matches(isDisplayed())).check(matches(isEnabled()));
            onView(withId(R.id.redeem_button)).check(matches(isDisplayed()));

            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
    }

    @Test
    public void scanActionLaunchesQrScannerOnCameraDevice() {
        Assume.assumeTrue("Voucher scanner smoke requires camera hardware",
                SatnetDeviceTestHelper.hasAnyCamera());

        Intent intent = new Intent(SatnetDeviceTestHelper.targetContext(), VoucherRedemptionActivity.class);
        try (ActivityScenario<VoucherRedemptionActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.scan_button)).perform(click());

            SatnetDeviceTestHelper.waitForCondition("QR scanner activity launch", () -> {
                Activity activity = SatnetDeviceTestHelper.getTopResumedActivity();
                return activity instanceof QRScannerActivity;
            });

            Activity topActivity = SatnetDeviceTestHelper.getTopResumedActivity();
            assertNotNull(topActivity);
            assertTrue(topActivity instanceof QRScannerActivity);
            topActivity.runOnUiThread(topActivity::finish);

            SatnetDeviceTestHelper.waitForCondition("Voucher screen resume",
                    () -> scenario.getState() == Lifecycle.State.RESUMED);
        }
    }
}


