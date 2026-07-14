/*
 * SATNET maintenance note:
 * This file is maintained as part of SATNET and builds on historical upstream work.
 * Copyright (C) 2011 The Serval Project.
 * Licensed under GPL-3.0-or-later; see LICENSE-SOFTWARE.md.
 */

package org.servalproject;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.batphone.CallDirector;
import org.servalproject.rhizome.RhizomeMain;
import org.servalproject.satnet.SatnetRuntimeConfig;
import org.servalproject.satnet.SatnetStartupGate;
import org.servalproject.satnet.ui.SatnetMapsActivity;
import org.servalproject.servald.PeerListService;
import org.servalproject.servald.ServalD;
import org.servalproject.servaldna.keyring.KeyringIdentity;
import org.servalproject.ui.Networks;
import org.servalproject.ui.ShareUsActivity;
import org.servalproject.ui.help.HtmlHelp;
import org.servalproject.wizard.Wizard;

// SATNET AFRICA Imports
import org.servalproject.satnet.ui.SatnetRoleSetupActivity;


/**
 *
 * Main activity which presents the SATNET launcher-style screen. On the first
 * time SATNET is installed, this activity ensures that a warning dialog is
 * presented and the user is taken through the setup wizard. Once setup has been
 * confirmed the user is taken to the main screen.
 *
 * @author Paul Gardner-Stephen <paul@servalproject.org>
 * @author Andrew Bettison <andrew@servalproject.org>
 * @author Corey Wallis <corey@servalproject.org>
 * @author Jeremy Lakeman <jeremy@servalproject.org>
 * @author Romana Challans <romana@servalproject.org>
 */
public class Main extends Activity implements OnClickListener {
	public ServalBatPhoneApplication app;
	private static final String TAG = "Main";
	private TextView buttonToggle;
	private TextView homeStatusText;
	private TextView satnetStatusText;
	private ImageView buttonToggleImg;
	private Drawable powerOnDrawable;
	private Drawable powerOffDrawable;

	private void openMaps() {
		startActivity(new Intent(getApplicationContext(), SatnetMapsActivity.class));
	}

	@Override
	public void onClick(View view) {
		// Do nothing until upgrade finished.
		if (app.getState() != State.Running)
			return;

		int id = view.getId();
		if (id == R.id.btncall || id == R.id.callText) {
			if (!PeerListService.havePeers()) {
				app.displayToastMessage("You do not have a connection to any other phones");
				return;
			}
			try {
				startActivity(new Intent(Intent.ACTION_DIAL));
				return;
			} catch (ActivityNotFoundException e) {
				Log.e(TAG, e.getMessage(), e);
			}
			startActivity(new Intent(app, CallDirector.class));
		} else if (id == R.id.messageLabel || id == R.id.messageText) {
			if (!ServalD.isRhizomeEnabled()) {
				app.displayToastMessage("Messaging cannot function without an sdcard");
				return;
			}
			startActivity(new Intent(getApplicationContext(),
					org.servalproject.messages.MessagesListActivity.class));
		} else if (id == R.id.mapsLabel || id == R.id.mapsText) {
			openMaps();
		} else if (id == R.id.contactsLabel || id == R.id.contactsText) {
			startActivity(new Intent(getApplicationContext(),
					org.servalproject.ui.ContactsActivity.class));
		} else if (id == R.id.settingsLabel || id == R.id.settingsText) {
			startActivity(new Intent(getApplicationContext(),
					org.servalproject.ui.SettingsScreenActivity.class));
		} else if (id == R.id.sharingLabel || id == R.id.sharingText) {
			startActivity(new Intent(getApplicationContext(),
					RhizomeMain.class));
		} else if (id == R.id.helpLabel || id == R.id.helpText) {
			Intent intent = new Intent(getApplicationContext(),
					HtmlHelp.class);
			intent.putExtra("page", "helpindex.html");
			startActivity(intent);
		} else if (id == R.id.servalLabel || id == R.id.wifiText) {
			startActivity(new Intent(getApplicationContext(),
					ShareUsActivity.class));
		} else if (id == R.id.powerLabel || id == R.id.btntoggle) {
			startActivity(new Intent(getApplicationContext(),
					Networks.class));
		} else if (id == R.id.satnetLabel || id == R.id.satnetText) {
			try {
				SatnetStartupGate.Status satnetStatus = SatnetStartupGate.evaluate(this);
				if (!satnetStatus.canEnterInteractiveFlows()) {
					app.displayToastMessage(satnetStatus.getBlockingMessage());
					return;
				}
				startActivity(new Intent(getApplicationContext(),
						SatnetRoleSetupActivity.class));
			} catch (RuntimeException e) {
				Log.e(TAG, "Failed to open SATNET setup", e);
				app.displayToastMessage("Unable to open SATNET setup on this device");
			}
        }
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.app = (ServalBatPhoneApplication) this.getApplication();

		setContentView(R.layout.main);
        initViews();
	}

    private void initViews() {
        // adjust the power button label on startup
        buttonToggle = (TextView) findViewById(R.id.btntoggle);
		homeStatusText = (TextView) findViewById(R.id.main_home_status);
		satnetStatusText = (TextView) findViewById(R.id.main_satnet_status);
        buttonToggleImg = (ImageView) findViewById(R.id.powerLabel);
        if (buttonToggleImg != null) {
            buttonToggleImg.setOnClickListener(this);
        }

        // load the power drawables
        powerOnDrawable = getResources().getDrawable(
                R.drawable.ic_launcher_power);
        powerOffDrawable = getResources().getDrawable(
                R.drawable.ic_launcher_power_off);

        int listenTo[] = {
                R.id.btncall,
				R.id.callText,
                R.id.messageLabel,
				R.id.messageText,
                R.id.mapsLabel,
				R.id.mapsText,
                R.id.contactsLabel,
				R.id.contactsText,
                R.id.settingsLabel,
				R.id.settingsText,
                R.id.sharingLabel,
				R.id.sharingText,
                R.id.helpLabel,
				R.id.helpText,
                R.id.servalLabel,
				R.id.wifiText,
				R.id.btntoggle,
                R.id.satnetLabel,
				R.id.satnetText,
        };
        for (int i = 0; i < listenTo.length; i++) {
            View v = findViewById(listenTo[i]);
            if (v != null) {
                v.setOnClickListener(this);
            }
        }
    }

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			int stateOrd = intent.getIntExtra(
					ServalBatPhoneApplication.EXTRA_STATE, 0);
			if (stateOrd < 0 || stateOrd >= State.values().length) {
				Log.w(TAG, "Ignoring invalid app state broadcast: " + stateOrd);
				return;
			}
			State state = State.values()[stateOrd];
			stateChanged(state);
		}
	};

	boolean registered = false;

	private void stateChanged(State state) {
		switch (state){
			case Running: case Upgrading: case Starting:
				// change the image for the power button
                if (buttonToggleImg != null) {
				    buttonToggleImg.setImageDrawable(
						    app.isEnabled()?powerOnDrawable:powerOffDrawable);
                }

				TextView pn = (TextView) this.findViewById(R.id.mainphonenumber);
                if (pn != null) {
                    String id = this.getString(state.getResourceId());
                    if (state == State.Running) {
                        try {
                            KeyringIdentity identity = app.server.getIdentity();

                            if (identity.did != null)
                                id = identity.did;
                            else
                                id = identity.sid.abbreviation();
                        } catch (Exception e) {
                            Log.e(TAG, e.getMessage(), e);
                        }
                    }
								pn.setText(id);
                }
				break;
			case RequireDidName: case NotInstalled: case Installing:
				this.startActivity(new Intent(this, Wizard.class));
				finish();
				app.startBackgroundInstall();
				break;
			case Broken:
				// TODO display error?
				break;
		}
		updateHubStatus(state);
	}

	private void updateHubStatus(State state) {
		if (homeStatusText != null) {
			if (state == State.Running) {
				homeStatusText.setText(PeerListService.havePeers()
						? R.string.main_hub_status_ready_connected
						: R.string.main_hub_status_ready_waiting);
			} else if (state == State.RequireDidName || state == State.NotInstalled || state == State.Installing) {
				homeStatusText.setText(R.string.main_hub_status_setup);
			} else {
				homeStatusText.setText(state.getResourceId());
			}
		}

		if (satnetStatusText != null) {
			if (state != State.Running) {
				satnetStatusText.setText(R.string.main_satnet_pending_summary);
				return;
			}

			try {
				SatnetStartupGate.Status satnetStatus = SatnetStartupGate.evaluate(this);
				if (satnetStatus.canEnterInteractiveFlows()) {
					satnetStatusText.setText(getString(
							R.string.main_satnet_ready_summary,
							SatnetRuntimeConfig.getWalletSummary()));
				} else {
					satnetStatusText.setText(satnetStatus.getBlockingMessage());
				}
			} catch (RuntimeException e) {
				Log.e(TAG, "Failed to render SATNET launcher status", e);
				satnetStatusText.setText(R.string.main_satnet_pending_summary);
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (!registered) {
			IntentFilter filter = new IntentFilter();
			filter.addAction(ServalBatPhoneApplication.ACTION_STATE);
			ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
			registered = true;
		}

		stateChanged(app.getState());
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (registered) {
			this.unregisterReceiver(receiver);
			registered = false;
		}
	}
}
