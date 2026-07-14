/*
 * SATNET maintenance note:
 * This file is maintained as part of SATNET and builds on historical upstream work.
 * Copyright (C) 2011 The Serval Project.
 * Licensed under GPL-3.0-or-later; see LICENSE-SOFTWARE.md.
 */

/*
 * @author Paul Gardner-Stephen <paul@servalproject.org>
 * @author Jeremy Lakeman <jeremy@servalproject.org>
 * @author Romana Challans <romana@servalproject.org>
 *
 *  Wizard: set phone number and display name.
 *  Used for initial run and is called again to reset phone number.
 **/
package org.servalproject.wizard;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.servalproject.Main;
import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.account.AccountService;
import org.servalproject.servaldna.keyring.KeyringIdentity;

import androidx.core.content.ContextCompat;

public class SetPhoneNumber extends Activity {
	private ServalBatPhoneApplication app;
	private static final String TAG="SetPhoneNumber";
	private EditText number;
	private EditText name;
	private TextView sid;
	private Button button;
	private KeyringIdentity identity;

	private String getPhoneNumberFromDevice() {
		PackageManager packageManager = getPackageManager();
		if (packageManager == null || !packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
			return null;
		}

		TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		if (telephonyManager == null) {
			return null;
		}

		boolean hasPermission;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS)
					== PackageManager.PERMISSION_GRANTED
					|| ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
					== PackageManager.PERMISSION_GRANTED
					|| ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
					== PackageManager.PERMISSION_GRANTED;
		} else {
			hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
					== PackageManager.PERMISSION_GRANTED;
		}

		if (!hasPermission) {
			Log.i(TAG, "Skipping phone number autofill because telephony permission is not granted");
			return null;
		}

		try {
			return telephonyManager.getLine1Number();
		} catch (SecurityException e) {
			Log.w(TAG, "Unable to read line1 number", e);
			return null;
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app=(ServalBatPhoneApplication)this.getApplication();

		setContentView(R.layout.set_phone_no);
		number = (EditText)this.findViewById(R.id.batphoneNumberText);
		number.setSelectAllOnFocus(true);

		name = (EditText) this.findViewById(R.id.batphoneNameText);
		name.setSelectAllOnFocus(true);

		sid = (TextView) this.findViewById(R.id.sidText);

		button = (Button) this.findViewById(R.id.btnPhOk);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				button.setEnabled(false);

				new AsyncTask<String, Void, Boolean>() {

					@Override
					protected Boolean doInBackground(String... params) {
						try {

							identity = app.server.setIdentityDetails(identity, params[0], params[1]);

							// Create the SATNET Android account if possible, but do not block setup.
							try {
								Account account = AccountService.getAccount(SetPhoneNumber.this);
								if (account == null) {
									account = AccountService.createAccount(SetPhoneNumber.this, getString(R.string.app_name));

									Intent ourIntent = SetPhoneNumber.this.getIntent();
									if (ourIntent != null && ourIntent.getExtras() != null) {
										AccountAuthenticatorResponse response = ourIntent
												.getExtras()
												.getParcelable(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);
										if (response != null) {
											Bundle result = new Bundle();
											result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
											result.putString(AccountManager.KEY_ACCOUNT_TYPE, AccountService.TYPE);
											response.onResult(result);
										}
									}
								}
							} catch (SecurityException e) {
								Log.w(TAG, "Contacts permission denied; continuing setup without contact sync", e);
								app.displayToastMessage("Contacts permission not granted. Setup will continue without sync.");
							}

							app.startupComplete(identity);

							return true;
						} catch (IllegalArgumentException e) {
							app.displayToastMessage(e.getMessage());
						} catch (Exception e) {
							Log.e(TAG, e.getMessage(), e);
							app.displayToastMessage(e.getMessage());
						}
						return false;
					}

					@Override
					protected void onPostExecute(Boolean result) {
						if (result) {
							Intent intent = new Intent(SetPhoneNumber.this,
									Main.class);
							intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
							SetPhoneNumber.this.startActivity(intent);
							SetPhoneNumber.this.setResult(RESULT_OK);
							SetPhoneNumber.this.finish();
							return;
						}
						button.setEnabled(true);
					}
				}.execute(number.getText().toString(),
						name.getText().toString());
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();

		String existingName = null;
		String existingNumber = null;
		try {
			identity = app.server.getIdentity();
			if (identity != null) {
				sid.setText(identity.sid.abbreviation());
				existingNumber = identity.did;
				existingName = identity.name;
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}

		if (existingName==null && existingNumber==null){
			// try to get number from phone, probably wont work though...
			existingNumber = getPhoneNumberFromDevice();
		}
		number.setText(existingNumber);
		name.setText(existingName);
	}
}
