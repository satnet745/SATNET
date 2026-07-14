/*
 * SATNET maintenance note:
 * This file is maintained as part of SATNET and builds on historical upstream work.
 * Copyright (C) 2012 The Serval Project.
 * Licensed under GPL-3.0-or-later; see LICENSE-SOFTWARE.md.
 */

package org.servalproject.ui;

/* Settings screen for MeshMS and relay fallback preferences. */

import org.servalproject.R;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class SettingsMeshMSScreenActivity extends Activity implements
		OnClickListener {
	private final int RINGTONE_PICKER_ACTIVITY = 1;
	private static final String PREF_RELAY_HOST = "relay_host";
	private static final String PREF_SMS_RELAY_NUMBER = "sms_relay_number";
	private static final String PREF_ANON_ROUTE = "relay_anonymous_preference";
	private SharedPreferences mSharedPreferences = null;
	private SharedPreferences.Editor mPreferenceEditor = null;
	private EditText relayHostEdit;
	private EditText smsRelayNumberEdit;
	private Spinner relayPreferenceSpinner;

	@Override
	public void onClick(View v) {
		if (v.getId() == R.id.btnNotifSound) {
			Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
			intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
					RingtoneManager.TYPE_NOTIFICATION);
			intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,
					"Select MeshMS Tone");
			intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
			intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
			String current = mSharedPreferences.getString(
					"meshms_notification_sound", null);
			if (current != null) {
				intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
						current);
			}
			Uri def = RingtoneManager.getActualDefaultRingtoneUri(this,
					RingtoneManager.TYPE_NOTIFICATION);
			if (def != null) {
				intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, def);
			}
			startActivityForResult(intent, RINGTONE_PICKER_ACTIVITY);
		} else if (v.getId() == R.id.btnSaveRelaySettings) {
			saveRelaySettings();
		}
	}
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settingsmeshmsscreen);

		mSharedPreferences = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
		mPreferenceEditor = mSharedPreferences.edit();
		relayHostEdit = findViewById(R.id.relayHostEdit);
		smsRelayNumberEdit = findViewById(R.id.smsRelayNumberEdit);
		relayPreferenceSpinner = findViewById(R.id.relayPreferenceSpinner);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.relay_route_preference_entries,
				android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		relayPreferenceSpinner.setAdapter(adapter);
		loadRelaySettings();
		// Notification Sound Settings
		this.findViewById(R.id.btnNotifSound).setOnClickListener(this);
		this.findViewById(R.id.btnSaveRelaySettings).setOnClickListener(this);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == RINGTONE_PICKER_ACTIVITY && resultCode == RESULT_OK) {
			Uri uri = data == null ? null
					: data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
			mPreferenceEditor.putString("meshms_notification_sound",
					uri == null ? null : uri.toString());
			mPreferenceEditor.apply();
		}
	}

	private void loadRelaySettings() {
		relayHostEdit.setText(mSharedPreferences.getString(PREF_RELAY_HOST, ""));
		smsRelayNumberEdit.setText(mSharedPreferences.getString(PREF_SMS_RELAY_NUMBER, ""));
		String[] values = getResources().getStringArray(R.array.relay_route_preference_values);
		String current = mSharedPreferences.getString(PREF_ANON_ROUTE, "AUTO");
		int index = 0;
		for (int i = 0; i < values.length; i++) {
			if (values[i].equalsIgnoreCase(current)) {
				index = i;
				break;
			}
		}
		relayPreferenceSpinner.setSelection(index);
	}

	private void saveRelaySettings() {
		String[] values = getResources().getStringArray(R.array.relay_route_preference_values);
		int selectedPosition = relayPreferenceSpinner.getSelectedItemPosition();
		String routePreference = values[Math.max(0, Math.min(values.length - 1, selectedPosition))];
		mPreferenceEditor.putString(PREF_RELAY_HOST, relayHostEdit.getText().toString().trim());
		mPreferenceEditor.putString(PREF_SMS_RELAY_NUMBER, smsRelayNumberEdit.getText().toString().trim());
		mPreferenceEditor.putString(PREF_ANON_ROUTE, routePreference);
		mPreferenceEditor.apply();
		Toast.makeText(this, R.string.relay_settings_saved, Toast.LENGTH_SHORT).show();
	}
}
