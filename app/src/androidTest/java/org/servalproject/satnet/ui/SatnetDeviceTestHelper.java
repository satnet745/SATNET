package org.servalproject.satnet.ui;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.satnet.SatnetRoleManager;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

final class SatnetDeviceTestHelper {
    private SatnetDeviceTestHelper() {
    }

    static Context targetContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    static ServalBatPhoneApplication app() {
        Context applicationContext = targetContext().getApplicationContext();
        if (!(applicationContext instanceof ServalBatPhoneApplication)) {
            throw new AssertionError("Target application is not ServalBatPhoneApplication");
        }
        ServalBatPhoneApplication app = (ServalBatPhoneApplication) applicationContext;
        ServalBatPhoneApplication.context = app;
        return app;
    }

    static void prepareReadyUserRole() {
        ServalBatPhoneApplication app = app();
        resetAppData(app);
        setRuntimeState(app, true, true);
        new SatnetRoleManager(targetContext()).registerAsUser();
    }

    static boolean hasAnyCamera() {
        PackageManager packageManager = targetContext().getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    static Activity getTopResumedActivity() {
        final Activity[] resumedActivity = new Activity[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Collection<Activity> resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED);
            if (!resumed.isEmpty()) {
                resumedActivity[0] = resumed.iterator().next();
            }
        });
        return resumedActivity[0];
    }

    static void waitForCondition(String label, Condition condition) {
        long deadline = System.currentTimeMillis() + 6_000L;
        Throwable lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            try {
                if (condition.isSatisfied()) {
                    return;
                }
            } catch (Throwable failure) {
                lastFailure = failure;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50L));
            if (Thread.currentThread().isInterrupted()) {
                throw new AssertionError("Interrupted while waiting for " + label);
            }
        }
        if (lastFailure instanceof AssertionError) {
            throw (AssertionError) lastFailure;
        }
        throw new AssertionError("Timed out waiting for " + label, lastFailure);
    }

    private static void resetAppData(ServalBatPhoneApplication app) {
        app.getSharedPreferences("satnet_roles", Context.MODE_PRIVATE).edit().clear().commit();
        app.getSharedPreferences("bitcoin_wallet", Context.MODE_PRIVATE).edit().clear().commit();
        app.deleteDatabase("satnet_vouchers.db");
    }

    private static void setRuntimeState(ServalBatPhoneApplication app,
            boolean startupTasksComplete, boolean rhizomeReady) {
        try {
            java.lang.reflect.Field stateField = ServalBatPhoneApplication.class.getDeclaredField("state");
            stateField.setAccessible(true);
            stateField.set(app, ServalBatPhoneApplication.State.Running);

            java.lang.reflect.Field startupTasksField = ServalBatPhoneApplication.class
                    .getDeclaredField("startupTasksComplete");
            startupTasksField.setAccessible(true);
            startupTasksField.setBoolean(app, startupTasksComplete);

            java.lang.reflect.Field rhizomeReadyField = ServalBatPhoneApplication.class
                    .getDeclaredField("rhizomeRuntimeReady");
            rhizomeReadyField.setAccessible(true);
            rhizomeReadyField.setBoolean(app, rhizomeReady);
        } catch (Exception e) {
            throw new AssertionError("Unable to prepare SATNET runtime state for device test", e);
        }
    }

    interface Condition {
        boolean isSatisfied();
    }
}

