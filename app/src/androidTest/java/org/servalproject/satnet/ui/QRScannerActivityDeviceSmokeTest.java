package org.servalproject.satnet.ui;

import android.Manifest;

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
import static androidx.test.espresso.matcher.ViewMatchers.withId;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class QRScannerActivityDeviceSmokeTest {

    @Rule
    public final GrantPermissionRule cameraPermissionRule =
            GrantPermissionRule.grant(Manifest.permission.CAMERA);

    @Before
    public void requireCameraHardware() {
        Assume.assumeTrue("Camera-backed scanner smoke requires camera hardware",
                SatnetDeviceTestHelper.hasAnyCamera());
    }

    @Test
    public void scannerLaunchesAndCancelClosesActivity() {
        try (ActivityScenario<QRScannerActivity> scenario = ActivityScenario.launch(QRScannerActivity.class)) {
            onView(withId(R.id.barcode_scanner_view)).check(matches(isDisplayed()));
            onView(withId(R.id.cancel_scan_button)).check(matches(isDisplayed())).perform(click());

            SatnetDeviceTestHelper.waitForCondition("QR scanner to finish",
                    () -> scenario.getState() == Lifecycle.State.DESTROYED);
        }
    }
}

