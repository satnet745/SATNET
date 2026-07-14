package org.servalproject.satnet.ui;

import android.app.Activity;
import android.content.Context;
import android.view.WindowManager;
import android.view.View;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class QRScannerActivityTest {

    private Context context;

    @Before
    public void setUp() {
        ServalBatPhoneApplication app = SatnetRuntimeTestHelper.prepareApp();
        context = SatnetRuntimeTestHelper.resetAppData(app);
    }

    @Test
    public void scannerLaunchShowsHintAndCancelAction() {
        try (ActivityController<QRScannerActivity> controller = Robolectric.buildActivity(QRScannerActivity.class)) {
            QRScannerActivity activity = controller.create().start().resume().get();

            TextView hint = activity.findViewById(R.id.scanner_hint_text);
            Button cancelButton = activity.findViewById(R.id.cancel_scan_button);
            View scannerView = activity.findViewById(R.id.barcode_scanner_view);

            assertFalse(activity.isFinishing());
            assertTrue((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
            assertNotNull(scannerView);
            assertEquals(context.getString(R.string.satnet_qr_scanner_hint), hint.getText().toString());
            assertEquals(context.getString(R.string.satnet_qr_scanner_cancel), cancelButton.getText().toString());
        }
    }

    @Test
    public void cancelButtonReturnsCanceledResult() {
        try (ActivityController<QRScannerActivity> controller = Robolectric.buildActivity(QRScannerActivity.class)) {
            QRScannerActivity activity = controller.create().start().resume().get();

            Button cancelButton = activity.findViewById(R.id.cancel_scan_button);
            cancelButton.performClick();

            assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).getResultCode());
            assertTrue(activity.isFinishing());
        }
    }
}


