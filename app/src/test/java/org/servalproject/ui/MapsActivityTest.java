package org.servalproject.ui;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.satnet.ui.SatnetMapsActivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class MapsActivityTest {

    private Context context;

    @Before
    public void setUp() {
        ServalBatPhoneApplication app = ApplicationProvider.getApplicationContext();
        ServalBatPhoneApplication.context = app;
        context = resetAppData(app);
        setRuntimeReady(app);
    }

    private Context resetAppData(ServalBatPhoneApplication app) {
        app.getSharedPreferences("satnet_roles", Context.MODE_PRIVATE).edit().clear().commit();
        app.getSharedPreferences("bitcoin_wallet", Context.MODE_PRIVATE).edit().clear().commit();
        app.deleteDatabase("satnet_vouchers.db");
        return app;
    }

    private void setRuntimeReady(ServalBatPhoneApplication app) {
        try {
            java.lang.reflect.Field stateField = ServalBatPhoneApplication.class.getDeclaredField("state");
            stateField.setAccessible(true);
            stateField.set(app, ServalBatPhoneApplication.State.Running);

            java.lang.reflect.Field startupTasksField = ServalBatPhoneApplication.class.getDeclaredField("startupTasksComplete");
            startupTasksField.setAccessible(true);
            startupTasksField.setBoolean(app, true);

            java.lang.reflect.Field rhizomeReadyField = ServalBatPhoneApplication.class.getDeclaredField("rhizomeRuntimeReady");
            rhizomeReadyField.setAccessible(true);
            rhizomeReadyField.setBoolean(app, true);
        } catch (Exception e) {
            throw new AssertionError("Unable to prepare SATNET runtime state for test", e);
        }
    }

    @Test
    public void legacyMapsActivityRedirectsToSatnetMaps() {
        try (ActivityController<MapsActivity> controller = Robolectric.buildActivity(MapsActivity.class, new Intent(context, MapsActivity.class))) {
            MapsActivity activity = controller.create().start().resume().get();

            Intent nextIntent = shadowOf(activity).getNextStartedActivity();
            assertNotNull(nextIntent);
            assertNotNull(nextIntent.getComponent());
            assertEquals(SatnetMapsActivity.class.getName(), nextIntent.getComponent().getClassName());
            assertTrue(activity.isFinishing());
        }
    }
}

