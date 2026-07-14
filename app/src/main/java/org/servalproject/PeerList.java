/*
 * SATNET maintenance note:
 * This file is maintained as part of SATNET and builds on historical upstream work.
 * Copyright (C) 2011 The Serval Project.
 * Licensed under GPL-3.0-or-later; see LICENSE-SOFTWARE.md.
 */
package org.servalproject;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import org.servalproject.batphone.CallHandler;
import org.servalproject.servald.IPeerListListener;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerComparator;
import org.servalproject.servald.PeerListService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Jeremy Lakeman <jeremy@servalproject.org>
 *
 *         Peer List fetches a list of known peers from the PeerListService.
 *         When a peer is received from the service this activity will attempt
 *         to resolve the peer by calling ServalD in an async task.
 */
public class PeerList extends ListActivity {
	private static final String TAG = "PeerList";

	private PeerListAdapter<Peer> listAdapter;
	private TextView emptyView;

	public static final String PICK_PEER_INTENT = "org.servalproject.PICK_FROM_PEER_LIST";

	public static final String CONTACT_NAME = "org.servalproject.PeerList.contactName";
	public static final String CONTACT_ID = "org.servalproject.PeerList.contactId";
	public static final String DID = "org.servalproject.PeerList.did";
	public static final String SID = "org.servalproject.PeerList.sid";
	public static final String NAME = "org.servalproject.PeerList.name";
	public static final String RESOLVED = "org.servalproject.PeerList.resolved";
	public static final String TITLE = "org.servalproject.PeerList.title";

	private boolean returnResult = false;
	private boolean listenerRegistered = false;

	private final List<Peer> peers = new ArrayList<Peer>();
	private final IPeerListListener listener = new IPeerListListener() {
		@Override
		public void peerChanged(final Peer p) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (!isFinishing()) {
						peerUpdated(p);
					}
				}
			});
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		applyIntentState(getIntent());

		listAdapter = new PeerListAdapter<Peer>(this, peers);
		listAdapter.setNotifyOnChange(false);
		this.setListAdapter(listAdapter);

		ListView lv = getListView();
		lv.setBackgroundColor(Color.BLACK);
		lv.setCacheColorHint(Color.BLACK);
		emptyView = new TextView(this);
		emptyView.setBackgroundColor(Color.BLACK);
		emptyView.setGravity(Gravity.CENTER);
		emptyView.setPadding(32, 64, 32, 64);
		emptyView.setTextColor(Color.WHITE);
		addContentView(emptyView, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		lv.setEmptyView(emptyView);
		updateEmptyState();

		// TODO Long click listener for more options, eg text message
		lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				try {
					Peer p = listAdapter.getItem(position);
					if (p == null) {
						ServalBatPhoneApplication.context.displayToastMessage("Peer is no longer available");
						updateEmptyState();
						return;
					}
					if (returnResult) {
						Log.i(TAG, "returning selected peer " + p);
						Intent returnIntent = new Intent();
						returnIntent.putExtra(
								CONTACT_NAME,
								p.getContactName());
						returnIntent.putExtra(SID, p.sid.toHex());
						returnIntent.putExtra(CONTACT_ID, p.contactId);
						returnIntent.putExtra(DID, p.did);
						returnIntent.putExtra(NAME, p.name);
						returnIntent.putExtra(RESOLVED,
								p.cacheUntil > SystemClock.elapsedRealtime());
						setResult(Activity.RESULT_OK, returnIntent);
						finish();
					} else {
						Log.i(TAG, "calling selected peer " + p);
						CallHandler.dial(p);
					}
				} catch (Exception e) {
					ServalBatPhoneApplication.context.displayToastMessage(e
							.getMessage());
					Log.e("BatPhone", e.getMessage(), e);
				}
			}
		});

	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		applyIntentState(intent);
		updateEmptyState();
	}

	private void peerUpdated(Peer p) {
		if (p == null) {
			updateEmptyState();
			return;
		}
		if (!p.isReachable()) {
			if (peers.remove(p)) {
				Collections.sort(peers, new PeerComparator());
				listAdapter.notifyDataSetChanged();
			}
			updateEmptyState();
			return;
		}
		if (!peers.contains(p)){
			peers.add(p);
		}
		Collections.sort(peers, new PeerComparator());
		listAdapter.notifyDataSetChanged();
		updateEmptyState();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (listenerRegistered) {
			PeerListService.removeListener(listener);
			listenerRegistered = false;
		}
		peers.clear();
		listAdapter.notifyDataSetChanged();
		updateEmptyState();
	}

	@Override
	protected void onResume() {
		super.onResume();
		listenerRegistered = true;
		updateEmptyState();
		ServalBatPhoneApplication.context.runOnBackgroundThread(new Runnable() {
			@Override
			public void run() {
				PeerListService.addListener(listener);
			}
		});
	}

	private void applyIntentState(Intent intent) {
		if (intent == null) {
			return;
		}
		returnResult = PICK_PEER_INTENT.equals(intent.getAction());
		String customTitle = intent.getStringExtra(TITLE);
		if (customTitle != null && !customTitle.isEmpty()) {
			setTitle(customTitle);
		}
	}

	private void updateEmptyState() {
		if (emptyView == null) {
			return;
		}
		if (!peers.isEmpty()) {
			emptyView.setText("");
			return;
		}
		if (PeerListService.havePeers()) {
			emptyView.setText(returnResult
					? "Searching for reachable peers to share with…"
					: "Searching for reachable peers…");
		} else {
			emptyView.setText(returnResult
					? "No reachable peers are available yet. Stay on the mesh and try again when another peer appears."
					: "No reachable peers are available yet.");
		}
	}

}
