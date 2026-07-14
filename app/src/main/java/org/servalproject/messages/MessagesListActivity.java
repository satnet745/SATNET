/*
 * Copyright (C) 2012 The Serval Project
 *
 * This file is part of Serval Software (http://www.servalproject.org)
 *
 * Serval Software is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.servalproject.messages;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.rhizome.MeshMS;
import org.servalproject.servald.IPeerListListener;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servaldna.keyring.KeyringIdentity;
import org.servalproject.servaldna.meshms.MeshMSConversation;
import org.servalproject.servaldna.meshms.MeshMSConversationList;
import org.servalproject.ui.SimpleAdapter;

import java.util.List;

import androidx.core.content.ContextCompat;

/**
 * main activity to display the list of messages
 */
public class MessagesListActivity extends Activity implements
		OnItemClickListener, IPeerListListener, SimpleAdapter.ViewBinder<MeshMSConversation> {

	private ServalBatPhoneApplication app;
	private final String TAG = "MessagesListActivity";
	private KeyringIdentity identity;
	private ListView listView;
	private TextView statusView;

	private SimpleAdapter<MeshMSConversation> adapter;
	private boolean receiverRegistered = false;
	private int loadRequestGeneration = 0;

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (MeshMS.NEW_MESSAGES.equals(intent.getAction()))
				populateList();
		}

	};


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app = ServalBatPhoneApplication.context;
		try {
			this.identity = app.server.getIdentity();
			setContentView(R.layout.messages_list);
			setTitle(getString(R.string.messages_list_header));
			listView = findViewById(android.R.id.list);
			statusView = findViewById(R.id.messages_list_status);
			listView.setOnItemClickListener(this);
			TextView emptyView = findViewById(android.R.id.empty);
			if (emptyView != null) {
				listView.setEmptyView(emptyView);
				emptyView.setText(R.string.messages_list_empty);
			}

			adapter = new SimpleAdapter<>(this, this);
			listView.setAdapter(adapter);
			updateConversationSummary(0, 0, false);
		}catch (Exception e){
			this.finish();
		}
	}

	/*
	 * get the required data and populate the cursor
	 */
	private void populateList() {
		if (!app.isMainThread()) {
			runOnUiThread(this::populateList);
			return;
		}

		final int requestGeneration = ++loadRequestGeneration;
		updateConversationSummary(0, 0, true);
		loadConversationsInBackground(requestGeneration);
	}

	private void loadConversationsInBackground(final int requestGeneration) {
		app.runOnBackgroundThread(() -> {
			final List<MeshMSConversation> loadedConversations = loadConversations();
			runOnUiThread(() -> applyLoadedConversations(requestGeneration, loadedConversations));
		});
	}

	private List<MeshMSConversation> loadConversations() {
		try {
			MeshMSConversationList conversationList = app.server.getRestfulClient().meshmsListConversations(identity.subscriber.sid);
			return conversationList.toList();
		} catch (Exception e) {
			app.displayToastMessage(e.getMessage());
			Log.e(TAG, e.getMessage(), e);
			return null;
		}
	}

	private void applyLoadedConversations(int requestGeneration, List<MeshMSConversation> loadedConversations) {
		if (isFinishing() || requestGeneration != loadRequestGeneration) {
			return;
		}
		if (loadedConversations != null) {
			adapter.setItems(loadedConversations);
			updateConversationSummary(
					loadedConversations.size(),
					countUnreadConversations(loadedConversations),
					false);
			return;
		}
		updateConversationSummary(0, 0, false);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onPause()
	 */
	@Override
	public void onPause() {
		PeerListService.removeListener(this);

		// unbind service
		if (receiverRegistered) {
			this.unregisterReceiver(receiver);
			receiverRegistered = false;
		}

		super.onPause();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onResume()
	 */
	@Override
	public void onResume() {
		PeerListService.addListener(this);
		populateList();

		if (!receiverRegistered) {
			IntentFilter filter = new IntentFilter();
			filter.addAction(MeshMS.NEW_MESSAGES);
			ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
			receiverRegistered = true;
		}
		super.onResume();
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {

		try{
			MeshMSConversation conversation = adapter.getItem(position);
			if (conversation == null) {
				return;
			}
			Intent mIntent = ShowConversationActivity.createIntent(this, conversation.them.sid);
			startActivity(mIntent);
		}catch (Exception e){
			Log.e(TAG, e.getMessage(), e);
		}
	}

	@Override
	public void peerChanged(Peer p) {
		// force the list to re-bind everything

		if (!app.isMainThread()) {
			runOnUiThread(() -> adapter.notifyDataSetChanged());
			return;
		}

		adapter.notifyDataSetChanged();
	}

	@Override
	public long getId(int position, MeshMSConversation meshMSConversation) {
		return meshMSConversation._id;
	}

	@Override
	public int getViewType(int position, MeshMSConversation meshMSConversation) {
		return 0;
	}

	@Override
	public void bindView(int position, MeshMSConversation meshMSConversation, View view) {
		Peer p = PeerListService.getPeer(meshMSConversation.them.sid);
		boolean unread = !meshMSConversation.isRead;

		TextView name = view.findViewById(R.id.Name);
		name.setText(p.toString());

		TextView displaySid = view.findViewById(R.id.sid);
		String sidSummary = getString(R.string.messages_list_sid_format, p.getSubscriberId().abbreviation());
		displaySid.setText(sidSummary);

		TextView displayNumber = view.findViewById(R.id.Number);
		String did = p.getDid();
		String numberSummary = did == null || did.trim().isEmpty()
				? getString(R.string.messages_list_no_number)
				: did;
		displayNumber.setText(numberSummary);

		Bitmap photo = null;
		ImageView image = view.findViewById(R.id.messages_list_item_image);
		if (p.contactId != -1)
			photo = MessageUtils.loadContactPhoto(this, p.contactId);

		// use photo if found else use default image
		if (photo != null) {
			image.setImageBitmap(photo);
		} else {
			image.setImageResource(R.drawable.ic_contact_picture);
		}
		TextView unreadBadge = view.findViewById(R.id.messages_list_unread_badge);
		if (unreadBadge != null) {
			unreadBadge.setVisibility(unread ? View.VISIBLE : View.GONE);
		}
		name.setTypeface(null, unread ? Typeface.BOLD : Typeface.NORMAL);
		view.setBackgroundResource(unread
				? R.drawable.messages_conversation_card_unread
				: R.drawable.messages_conversation_card);
		view.setAlpha(unread ? 1.0f : 0.92f);
		view.setContentDescription(getString(
				R.string.messages_list_row_accessibility,
				p.toString(),
				numberSummary,
				sidSummary,
				unread ? getString(R.string.messages_list_row_accessibility_unread_suffix) : ""));
	}

	private int countUnreadConversations(List<MeshMSConversation> conversations) {
		int unreadCount = 0;
		if (conversations == null) {
			return 0;
		}
		for (MeshMSConversation conversation : conversations) {
			if (conversation != null && !conversation.isRead) {
				unreadCount++;
			}
		}
		return unreadCount;
	}

	private void updateConversationSummary(int count, int unreadCount, boolean loading) {
		if (statusView == null) {
			return;
		}
		if (loading) {
			statusView.setText(R.string.messages_list_status_loading);
			return;
		}
		if (count <= 0) {
			statusView.setText(R.string.messages_list_status_default);
			return;
		}
		if (unreadCount > 0) {
			statusView.setText(getResources().getQuantityString(
					R.plurals.messages_list_conversation_count_with_unread,
					count,
					count,
					unreadCount));
			return;
		}
		statusView.setText(getResources().getQuantityString(
				R.plurals.messages_list_conversation_count,
				count,
				count));
	}

	@Override
	public int[] getResourceIds() {
		return new int[]{R.layout.messages_list_item};
	}

	@Override
	public boolean hasStableIds() {
		return false;
	}

	@Override
	public boolean isEnabled(MeshMSConversation meshMSConversation) {
		return true;
	}
}
