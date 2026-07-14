/*
 * SATNET maintenance note:
 * This file is maintained as part of SATNET and builds on historical upstream work.
 * Copyright (C) 2011 The Serval Project.
 * Licensed under GPL-3.0-or-later; see LICENSE-SOFTWARE.md.
 */

package org.servalproject.wizard;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;

import androidx.core.content.ContextCompat;

public class Wizard extends Activity {

	private ServalBatPhoneApplication app;
	private Button button;
	private ProgressBar progress;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app = ServalBatPhoneApplication.context;

		setContentView(R.layout.wizard);

		button = (Button) this.findViewById(R.id.btnwizard);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivityForResult(new Intent(Wizard.this,
						SetPhoneNumber.class), 0);
			}
		});
		progress = (ProgressBar) this.findViewById(R.id.progress);
	}

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			int stateOrd = intent.getIntExtra(
					ServalBatPhoneApplication.EXTRA_STATE, 0);
			ServalBatPhoneApplication.State state = ServalBatPhoneApplication.State.values()[stateOrd];
			stateChanged(state);
		}
	};
	private boolean registered = false;

	private void stateChanged(ServalBatPhoneApplication.State state) {
		switch (state) {
			case NotInstalled:
			case Installing:
			case Upgrading:
				progress.setVisibility(View.VISIBLE);
				button.setVisibility(View.GONE);
				break;
			case Running:
			case RequireDidName:
				progress.setVisibility(View.GONE);
				button.setVisibility(View.VISIBLE);
				break;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		IntentFilter filter = new IntentFilter();
		filter.addAction(ServalBatPhoneApplication.ACTION_STATE);
		ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
		registered = true;

		stateChanged(app.getState());
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (registered)
			this.unregisterReceiver(receiver);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			setResult(RESULT_OK);
			finish();
		}
	}
}
