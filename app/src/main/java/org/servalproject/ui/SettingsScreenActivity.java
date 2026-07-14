/*
 * SATNET maintenance note:
 * This file is maintained as part of SATNET and builds on historical upstream work.
 * Copyright (C) 2012 The Serval Project.
 * Licensed under GPL-3.0-or-later; see LICENSE-SOFTWARE.md.
 */

/*
 * Settings - main settings screen
 *
 * @author Romana Challans <romana@servalproject.org>
 */

package org.servalproject.ui;

import java.io.File;

import org.servalproject.LogActivity;
import org.servalproject.PreparationWizard;
import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewParent;

public class SettingsScreenActivity extends Activity implements OnClickListener {

	@Override
	public void onClick(View view) {
		int id = view.getId();
		if (id == R.id.btnWifiSettings) {
			startActivity(new Intent(this, SetupActivity.class));
		} else if (id == R.id.btnLogShow) {
			startActivity(new Intent(this, LogActivity.class));
		} else if (id == R.id.btnAccountsSettings) {// Accounts Settings Screen
			startActivity(new Intent(this, AccountsSettingsActivity.class));
		} else if (id == R.id.btnResetWifi) {// Reset Wi-fi Settings Screen
			// Clear out old attempt_ files
			File varDir = new File(
					ServalBatPhoneApplication.context.coretask.DATA_FILE_PATH
							+
							"/var/");
			if (varDir.isDirectory()) {
				File[] files = varDir.listFiles();
				if (files != null) {
					for (File f : files) {
						if (!f.getName().startsWith("attempt_"))
							continue;
						f.delete();
					}
				}
			}
			// Re-run wizard
			Intent prepintent = new Intent(SettingsScreenActivity.this,
					PreparationWizard.class);
			prepintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(prepintent);
		} else if (id == R.id.btnMMSSettings) {// Notification Sound Settings Screen
			startActivity(new Intent(this, SettingsMeshMSScreenActivity.class));
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settingsscreen);

		// hide flight mode settings tweaks as we can no longer modify them
		View wifiSettings = (View)this.findViewById(R.id.btnWifiSettings).getParent();
		wifiSettings.setVisibility(View.GONE);

		this.findViewById(R.id.btnWifiSettings).setOnClickListener(this);
		this.findViewById(R.id.btnLogShow).setOnClickListener(this);
		this.findViewById(R.id.btnAccountsSettings).setOnClickListener(this);
		this.findViewById(R.id.btnResetWifi).setOnClickListener(this);
		this.findViewById(R.id.btnMMSSettings).setOnClickListener(this);
	}

}
