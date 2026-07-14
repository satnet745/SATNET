package org.servalproject.satnet.ui;

import android.content.Context;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import org.servalproject.ServalBatPhoneApplication;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.robolectric.Shadows.shadowOf;

public final class SatnetRuntimeTestHelper {
    private SatnetRuntimeTestHelper() {
    }

      public static ServalBatPhoneApplication prepareApp() {
        ServalBatPhoneApplication app = ApplicationProvider.getApplicationContext();
        ServalBatPhoneApplication.context = app;
        return app;
    }

      public static Context resetAppData(ServalBatPhoneApplication app) {
        app.getSharedPreferences("satnet_roles", Context.MODE_PRIVATE).edit().clear().commit();
        app.getSharedPreferences("bitcoin_wallet", Context.MODE_PRIVATE).edit().clear().commit();
        app.deleteDatabase("satnet_vouchers.db");
        return app;
    }

      public static void setRuntimeReady(ServalBatPhoneApplication app) {
        setRuntimeState(app, true, true);
    }

      public static void setRuntimeWarming(ServalBatPhoneApplication app) {
        setRuntimeState(app, false, true);
    }

      public static void setRhizomePending(ServalBatPhoneApplication app) {
        setRuntimeState(app, true, false);
    }

      public static void setRuntimeState(ServalBatPhoneApplication app, boolean startupTasksComplete, boolean rhizomeReady) {
        if (app == null) {
            throw new AssertionError("Expected ServalBatPhoneApplication test context");
        }
        try {
            java.lang.reflect.Field stateField = ServalBatPhoneApplication.class.getDeclaredField("state");
            stateField.setAccessible(true);
            stateField.set(app, ServalBatPhoneApplication.State.Running);

            java.lang.reflect.Field startupTasksField = ServalBatPhoneApplication.class.getDeclaredField("startupTasksComplete");
            startupTasksField.setAccessible(true);
            startupTasksField.setBoolean(app, startupTasksComplete);

            java.lang.reflect.Field rhizomeReadyField = ServalBatPhoneApplication.class.getDeclaredField("rhizomeRuntimeReady");
            rhizomeReadyField.setAccessible(true);
            rhizomeReadyField.setBoolean(app, rhizomeReady);
        } catch (Exception e) {
            throw new AssertionError("Unable to prepare SATNET runtime state for test", e);
        }
    }

      public static void waitForCondition(Check condition) {
        long deadline = System.currentTimeMillis() + 4000L;
        AssertionError lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            try {
                if (condition.isSatisfied()) {
                    return;
                }
            } catch (AssertionError error) {
                lastFailure = error;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25L));
            if (Thread.currentThread().isInterrupted()) {
                throw new AssertionError("Interrupted while waiting for SATNET UI update");
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new AssertionError("Timed out waiting for SATNET UI update");
    }

      public interface Check {
        boolean isSatisfied();
    }
}

