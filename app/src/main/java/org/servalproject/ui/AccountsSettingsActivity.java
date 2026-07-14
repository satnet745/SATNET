/*
 * SATNET maintenance note:
 * This file is maintained as part of SATNET and builds on historical upstream work.
 * Copyright (C) 2012 The Serval Project.
 * Licensed under GPL-3.0-or-later; see LICENSE-SOFTWARE.md.
 */

/*
 * Settings - Accounts Settings screen
 *
 * @author Romana Challans <romana@servalproject.org>
 */

package org.servalproject.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servaldna.keyring.KeyringIdentity;

public class AccountsSettingsActivity extends Activity {

	private static final String TAG = "AccountSettings";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.accountssetting);

		// Accounts Settings Screen
		Button btnphoneReset = (Button) this.findViewById(R.id.btnphoneReset);
		btnphoneReset.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				AccountsSettingsActivity.this.startActivity(new Intent(
						AccountsSettingsActivity.this,
						org.servalproject.wizard.SetPhoneNumber.class));

			}
		});


		// Set Textviews and blank strings
		TextView acPN = (TextView) this.findViewById(R.id.acphonenumber);
		TextView acSID = (TextView) this.findViewById(R.id.acsid);
		TextView acNAME = (TextView) this.findViewById(R.id.acname);

		String PNid = getString(R.string.ac_no_unavailable);
		String SIDid = getString(R.string.ac_sid_unavailable);
		String NMid = getString(R.string.ac_name_unavailable);

		try {
			KeyringIdentity identity = ServalBatPhoneApplication.context.server.getIdentity();
			if (identity.did !=null)
				PNid = identity.did;
			if (identity.name !=null)
				NMid = identity.name;
			if (identity.sid !=null)
				SIDid = identity.sid.abbreviation();
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}

		// set values to display
		acPN.setText(PNid); // Phone number
		acSID.setText(SIDid); // SATNET ID
		acNAME.setText(NMid); // Name

	}
}
