package org.servalproject.satnet.ui;

import android.content.Context;
import android.content.Intent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.satnet.maps.SatnetMapBookmark;
import org.servalproject.satnet.maps.SecureMapBookmarkStore;
import org.servalproject.satnet.SatnetRoleManager;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servaldna.SubscriberId;
import org.servalproject.voucher.VoucherLedger;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class SatnetMapsActivityTest {

	private Context context;

	@Before
	public void setUp() {
		ServalBatPhoneApplication app = SatnetRuntimeTestHelper.prepareApp();
		context = SatnetRuntimeTestHelper.resetAppData(app);
		context.getSharedPreferences(SecureMapBookmarkStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
		context.getSharedPreferences("bitcoin_wallet_device_binding", Context.MODE_PRIVATE).edit().clear().commit();
		PeerListService.peers.clear();
		setLastPeerCount(0);
		SatnetRuntimeTestHelper.setRuntimeReady(app);
	}

	@Test
	public void activityUsesSecureWindowAndSavesManualBookmark() {
		try (ActivityController<SatnetMapsActivity> controller = Robolectric.buildActivity(SatnetMapsActivity.class)) {
			SatnetMapsActivity activity = controller.create().start().resume().get();

			assertTrue((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);

			EditText labelInput = activity.findViewById(R.id.satnet_maps_label_input);
			EditText noteInput = activity.findViewById(R.id.satnet_maps_note_input);
			EditText latitudeInput = activity.findViewById(R.id.satnet_maps_latitude_input);
			EditText longitudeInput = activity.findViewById(R.id.satnet_maps_longitude_input);
			Button applyManualButton = activity.findViewById(R.id.satnet_maps_apply_manual_button);
			Button saveBookmarkButton = activity.findViewById(R.id.satnet_maps_save_bookmark_button);
			TextView currentLocationText = activity.findViewById(R.id.satnet_maps_current_location_text);

			labelInput.setText("Field Depot");
			noteInput.setText("Quiet route marker");
			latitudeInput.setText("1.23456");
			longitudeInput.setText("32.98765");

			applyManualButton.performClick();

			assertTrue(currentLocationText.getText().toString().contains("Field Depot"));
			assertTrue(currentLocationText.getText().toString().contains("1.23456"));

			saveBookmarkButton.performClick();

			List<SatnetMapBookmark> bookmarks = new SecureMapBookmarkStore(activity).loadBookmarks();
			assertEquals(1, bookmarks.size());
			assertEquals("Field Depot", bookmarks.get(0).getDisplayLabel());
			assertEquals("Quiet route marker", bookmarks.get(0).getDisplayNote());
		}
	}

	@Test
	public void leavingActivityClearsTransientLocationSummary() {
		try (ActivityController<SatnetMapsActivity> controller = Robolectric.buildActivity(SatnetMapsActivity.class)) {
			SatnetMapsActivity activity = controller.create().start().resume().get();

			EditText latitudeInput = activity.findViewById(R.id.satnet_maps_latitude_input);
			EditText longitudeInput = activity.findViewById(R.id.satnet_maps_longitude_input);
			Button applyManualButton = activity.findViewById(R.id.satnet_maps_apply_manual_button);
			TextView currentLocationText = activity.findViewById(R.id.satnet_maps_current_location_text);

			latitudeInput.setText("0.5");
			longitudeInput.setText("36.5");
			applyManualButton.performClick();
			assertFalse(currentLocationText.getText().toString().contains("Temporary location is empty"));

			controller.pause();

			assertEquals(activity.getString(R.string.satnet_maps_current_location_placeholder), currentLocationText.getText().toString());
		}
	}

	@Test
	public void roleOverlayReflectsRegisteredAgentContext() {
		new SatnetRoleManager(context).registerAsAgent("Field Agent", "Kampala");

		try (ActivityController<SatnetMapsActivity> controller = Robolectric.buildActivity(SatnetMapsActivity.class)) {
			SatnetMapsActivity activity = controller.create().start().resume().get();
			TextView roleOverlayText = activity.findViewById(R.id.satnet_maps_role_overlay_text);

			assertTrue(roleOverlayText.getText().toString().contains("Agent service corridor"));
			assertTrue(roleOverlayText.getText().toString().contains("Kampala"));
		}
	}

	@Test
	public void meshOverlayShowsLocalOnlyPeerCoverageSummary() throws Exception {
		SubscriberId directSid = new SubscriberId(repeat('a'));
		SubscriberId relaySid = new SubscriberId(repeat('b'));
		SubscriberId transmitterSid = new SubscriberId(repeat('c'));

		Peer directPeer = PeerListService.getPeer(directSid);
		directPeer.linkChanged(transmitterSid, 1);
		directPeer.cacheUntil = Long.MAX_VALUE;
		directPeer.cacheContactUntil = Long.MAX_VALUE;
		Peer relayPeer = PeerListService.getPeer(relaySid);
		relayPeer.linkChanged(transmitterSid, 2);
		relayPeer.cacheUntil = Long.MAX_VALUE;
		relayPeer.cacheContactUntil = Long.MAX_VALUE;
		setLastPeerCount(2);

		try (ActivityController<SatnetMapsActivity> controller = Robolectric.buildActivity(SatnetMapsActivity.class)) {
			SatnetMapsActivity activity = controller.create().start().resume().get();
			TextView meshOverlayText = activity.findViewById(R.id.satnet_maps_mesh_overlay_text);

			assertTrue(meshOverlayText.getText().toString().contains("Reachable peers: 2"));
			assertTrue(meshOverlayText.getText().toString().contains("Relayed peers: 1"));
		}
	}

	@Test
	public void verifierTrustOverlayShowsRotationAndEvidenceBreakdown() {
		new SatnetRoleManager(context).registerAsVerifier();
		try (VoucherLedger ledger = new VoucherLedger(context)) {
			ledger.recordVerifierAudit("voucher-a", "audit-local", true, true, false,
					"Local verification", "manual", 1_000L, VoucherLedger.AUDIT_ORIGIN_LOCAL, null, false, 0L);
			ledger.recordVerifierAudit("voucher-b", "audit-mesh", true, false, true,
					"Mesh caution", "mesh relay", 2_000L, VoucherLedger.AUDIT_ORIGIN_MESH, "node-7", true, 2_100L);
		}

		try (ActivityController<SatnetMapsActivity> controller = Robolectric.buildActivity(SatnetMapsActivity.class)) {
			SatnetMapsActivity activity = controller.create().start().resume().get();
			TextView roleOverlayText = activity.findViewById(R.id.satnet_maps_role_overlay_text);
			TextView trustOverlayText = activity.findViewById(R.id.satnet_maps_trust_overlay_text);

			assertTrue(roleOverlayText.getText().toString().contains("mesh evidence: 1"));
			assertTrue(trustOverlayText.getText().toString().contains("Rotation alerts: 1"));
			assertTrue(trustOverlayText.getText().toString().contains("Mesh evidence: 1"));
			assertTrue(trustOverlayText.getText().toString().contains("Local evidence: 1"));
		}
	}

	@Test
	public void selectingSavedBookmarkEnablesSelectiveExportButtons() {
		SecureMapBookmarkStore store = new SecureMapBookmarkStore(context);
		SatnetMapBookmark bookmark = SatnetMapBookmark.create("Clinic Alpha", "Primary cache", 1.2d, 32.8d);
		store.upsertBookmark(bookmark);

		try (ActivityController<SatnetMapsActivity> controller = Robolectric.buildActivity(SatnetMapsActivity.class)) {
			SatnetMapsActivity activity = controller.create().start().resume().get();
			Button exportSelectedButton = activity.findViewById(R.id.satnet_maps_export_selected_button);
			Button exportSignedButton = activity.findViewById(R.id.satnet_maps_export_signed_button);
			TextView exportSelectionText = activity.findViewById(R.id.satnet_maps_export_selection_text);
			CheckBox exportCheckBox = (CheckBox) activity.findViewById(android.R.id.content)
					.findViewWithTag("export-checkbox:" + bookmark.id);

			assertFalse(exportSelectedButton.isEnabled());
			assertFalse(exportSignedButton.isEnabled());
			assertNotNull(exportCheckBox);

			exportCheckBox.performClick();

			assertTrue(exportSelectedButton.isEnabled());
			assertTrue(exportSignedButton.isEnabled());
			assertTrue(exportSelectionText.getText().toString().contains("1 bookmark"));
		}
	}

	@Test
	public void openInboxButtonLaunchesDedicatedInboxActivity() {
		try (ActivityController<SatnetMapsActivity> controller = Robolectric.buildActivity(SatnetMapsActivity.class)) {
			SatnetMapsActivity activity = controller.create().start().resume().get();
			Button openInboxButton = activity.findViewById(R.id.satnet_maps_open_inbox_button);

			openInboxButton.performClick();

			Intent nextIntent = shadowOf(activity).getNextStartedActivity();
			assertNotNull(nextIntent);
			assertNotNull(nextIntent.getComponent());
			assertEquals(SatnetMapsInboxActivity.class.getName(), nextIntent.getComponent().getClassName());
		}
	}

	private void setLastPeerCount(int count) {
		try {
			java.lang.reflect.Field field = PeerListService.class.getDeclaredField("lastPeerCount");
			field.setAccessible(true);
			field.setInt(null, count);
		} catch (Exception e) {
			throw new AssertionError("Unable to set peer count for test", e);
		}
	}

	private String repeat(char value) {
		char[] chars = new char[64];
		java.util.Arrays.fill(chars, value);
		return new String(chars);
	}
}

