/*
 * SATNET maintenance note:
 * This file is maintained as part of SATNET and builds on historical upstream work.
 * Copyright (C) 2011 The Serval Project.
 * Additional copyright (c) 2009 Harald Mueller and Seth Lemons.
 * Licensed under GPL-3.0-or-later; see LICENSE-SOFTWARE.md.
 */


package org.servalproject.ui;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.provider.Settings;
import android.util.Log;

import org.servalproject.R;

public class SetupActivity extends PreferenceActivity implements
		OnSharedPreferenceChangeListener {

	public static final String MSG_TAG = "ADHOC -> SetupActivity";
	public static final String AIRPLANE_MODE_TOGGLEABLE_RADIOS = "airplane_mode_toggleable_radios";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.layout.setupview);

		final ContentResolver resolver = getContentResolver();
		final String toggleableRadios = Settings.System.getString(resolver,
				AIRPLANE_MODE_TOGGLEABLE_RADIOS);

		setFlightModeCheckBoxes("bluetooth", toggleableRadios);
		setFlightModeCheckBoxes("wifi", toggleableRadios);

	}

	private void setFlightModeCheckBoxes(String name, String airplaneToggleable) {
		CheckBoxPreference pref = (CheckBoxPreference) findPreference(name
				+ "_toggleable");
		pref.setChecked(airplaneToggleable != null
				&& airplaneToggleable.contains(name));
	}

	@Override
	protected void onResume() {
		super.onResume();
		SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();

		prefs.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause() {
		Log.d(MSG_TAG, "Calling onPause()");
		super.onPause();
		getPreferenceScreen().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.endsWith("_toggleable")) {
			String radio = key.substring(0, key.indexOf('_'));
			boolean value = sharedPreferences.getBoolean(key, false);
			flightModeFix(AIRPLANE_MODE_TOGGLEABLE_RADIOS, radio, value);
		}
	}

	private void flightModeFix(String key, String radio, boolean newSetting) {
		final ContentResolver resolver = getContentResolver();
		String value = Settings.System.getString(resolver, key);
		if (value==null)
			value = "";
		boolean exists = value.contains(radio);

		if (newSetting == exists)
			return;
		if (newSetting)
			value += " " + radio;
		else
			value = value.replace(radio, "");
		try {
			Settings.System.putString(resolver, key, value);
		}catch (Exception e){
			// didn't work on this version of android. Oh well...
		}
	}

}
