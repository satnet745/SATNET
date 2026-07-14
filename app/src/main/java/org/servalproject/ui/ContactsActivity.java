/*
 * SATNET maintenance note:
 * This file is maintained as part of SATNET and builds on historical upstream work.
 * Copyright (C) 2012 The Serval Project.
 * Licensed under GPL-3.0-or-later; see LICENSE-SOFTWARE.md.
 */
package org.servalproject.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;

/**
 * main activity for contact management
 */
public class ContactsActivity extends Activity implements OnClickListener {

	/*
	 * private class level constants
	 */
	// private final boolean V_LOG = true;
	private final String TAG = "ContactsActivity";

	private final int PEER_LIST_RETURN = 0;

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.contacts_activity);

		// attach handlers to the button
		ViewGroup mButton = (ViewGroup) findViewById(R.id.contacts_ui_lookup_phone_contact);
		mButton.setOnClickListener(this);

		mButton = (ViewGroup) findViewById(R.id.contacts_ui_lookup_serval_contact);
		mButton.setOnClickListener(this);

	}

	@Override
	public void onClick(View view) {
		Intent mIntent;

		int id = view.getId();
		if (id == R.id.contacts_ui_lookup_phone_contact) {
			try{
				// show the contact address book
				mIntent = new Intent(Intent.ACTION_VIEW);
				mIntent.setData(Uri.parse("content://contacts/people"));
				mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(mIntent);
			}catch(ActivityNotFoundException e){
				Log.e(TAG, e.getMessage(), e);
				ServalBatPhoneApplication.context.displayToastMessage(e.getMessage());
			}
		} else if (id == R.id.contacts_ui_lookup_serval_contact) {
			// show the peer list screen
			mIntent = new Intent(this, org.servalproject.PeerList.class);
			startActivityForResult(mIntent, PEER_LIST_RETURN);
		} else {
			Log.w(TAG, "unknown view called onClick method");
		}
	}

}
