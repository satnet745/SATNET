package org.servalproject;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Minimal smoke tests that run on an emulator or physical device.
 *
 * These tests verify:
 *  1. The application package is correct.
 *  2. The Application class attaches without crashing.
 *  3. The Main launcher Activity can be created without crashing.
 *
 * Run via:
 *   ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class AppLaunchSmokeTest {

    /** Expected package name – guards against accidental rename regressions. */
    private static final String EXPECTED_PACKAGE = "org.servalproject";

    @Rule
    public ActivityScenarioRule<Main> activityRule =
            new ActivityScenarioRule<>(Main.class);

    /** Confirm the test process is running inside the correct application package. */
    @Test
    public void instrumentationTargetsCorrectPackage() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals(EXPECTED_PACKAGE, ctx.getPackageName());
    }

    /** Confirm the Application context is available and is the right class. */
    @Test
    public void applicationContextIsServalApp() {
        Context appCtx = ApplicationProvider.getApplicationContext();
        assertNotNull("Application context must not be null", appCtx);
        // The Application must be (or extend) ServalBatPhoneApplication.
        assertTrue("Application is not ServalBatPhoneApplication",
                appCtx instanceof ServalBatPhoneApplication);
    }

    /** Confirm the Main launcher Activity starts without throwing. */
    @Test
    public void mainActivityLaunchesWithoutCrash() {
        activityRule.getScenario().onActivity(activity -> {
            assertNotNull("Activity instance must not be null", activity);
        });
    }
}



