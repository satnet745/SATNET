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
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.account.AccountService;
import org.servalproject.rhizome.MeshMS;
import org.servalproject.servald.IPeerListListener;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servaldna.SubscriberId;
import org.servalproject.servaldna.keyring.KeyringIdentity;
import org.servalproject.servaldna.meshms.MeshMSMessage;
import org.servalproject.servaldna.meshms.MeshMSMessageList;
import org.servalproject.ui.SimpleAdapter;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import androidx.core.content.ContextCompat;

/**
 * activity to show a conversation thread
 *
 */
public class ShowConversationActivity extends Activity implements OnClickListener, SimpleAdapter.ViewBinder<Object>, IPeerListListener {
	public static final String EXTRA_RECIPIENT = "recipient";
	public static final String EXTRA_DRAFT_TEXT = "draft_text";

	public static Intent createIntent(Context context, String recipientSid) {
		Intent intent = new Intent(context, ShowConversationActivity.class);
		String normalizedRecipientSid = normalizeRecipientSid(recipientSid);
		if (!TextUtils.isEmpty(normalizedRecipientSid)) {
			intent.putExtra(EXTRA_RECIPIENT, normalizedRecipientSid);
		}
		return intent;
	}

	public static Intent createIntent(Context context, SubscriberId recipientSid) {
		return recipientSid == null
				? createIntent(context, (String) null)
				: createIntent(context, recipientSid.toHex());
	}

	public static String normalizeRecipientSid(String recipientSid) {
		return recipientSid == null ? null : recipientSid.toUpperCase(Locale.ROOT);
	}

	private final String TAG = "ShowConversation";
	private ServalBatPhoneApplication app;
	private KeyringIdentity identity;
	private Peer recipient;
	// the message text field
	private ListView list;
	private EditText message;
	private TextView recipientMetaView;
	private TextView threadStatusView;
	private TextView emptyView;
	private SimpleAdapter<Object> adapter;
	private boolean receiverRegistered = false;
	private int loadRequestGeneration = 0;

	BroadcastReceiver receiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (MeshMS.NEW_MESSAGES.equals(intent.getAction())) {
				populateList();
			}
		}

	};

	@Override
	public void onClick(View view) {
		if (view.getId() == R.id.show_message_ui_btn_send_message) {
			sendMessage();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
        app = ServalBatPhoneApplication.context;
		setContentView(R.layout.show_conversation);
		setTitle(R.string.show_conversation_title);
		try {
			this.identity = app.server.getIdentity();

			message = findViewById(R.id.show_conversation_ui_txt_content);
			recipientMetaView = findViewById(R.id.show_conversation_ui_recipient_meta);
			threadStatusView = findViewById(R.id.show_conversation_ui_status);
			emptyView = findViewById(android.R.id.empty);
			list = findViewById(android.R.id.list);
			if (emptyView != null) {
				list.setEmptyView(emptyView);
			}
			adapter = new SimpleAdapter<>(this, this);
			list.setAdapter(adapter);

			// get the thread id from the intent
			Intent mIntent = getIntent();
			String did = null;
			SubscriberId recipientSid = null;

			if (Intent.ACTION_SENDTO.equals(mIntent.getAction())) {
				Uri uri = mIntent.getData();
				Log.v(TAG, "Received " + mIntent.getAction() + " " + uri);
				if (uri != null) {
					String scheme = uri.getScheme();
					if ("sms".equals(scheme)
							|| "smsto".equals(scheme)) {
						did = uri.getSchemeSpecificPart();
						did = did.trim();
						if (did.endsWith(","))
							did = did.substring(0, did.length() - 1).trim();
						if (did.indexOf("<") > 0)
							did = did.substring(did.indexOf("<") + 1,
									did.indexOf(">")).trim();

						Log.v(TAG, "Parsed did " + did);
					}
				}
			}

			{
				String recipientSidString = mIntent.getStringExtra(EXTRA_RECIPIENT);
				if (recipientSidString != null)
					recipientSid = new SubscriberId(recipientSidString);
			}

			if (recipientSid == null && did != null) {
				// lookup the sid from the contacts database
				long contactId = AccountService.getContactId(
						getContentResolver(), did);
				if (contactId >= 0)
					recipientSid = AccountService.getContactSid(
							getContentResolver(),
							contactId);

				if (recipientSid == null) {
					// TODO scan the network first and only complain when you
					// attempt to send?
					throw new UnsupportedOperationException(
							"Subscriber id not found for phone number " + did);
				}
			}

			if (recipientSid == null)
				throw new UnsupportedOperationException(
						"No Subscriber id found");

			recipient = PeerListService.getPeer(recipientSid);
			TextView recipientView = findViewById(R.id.show_conversation_ui_recipient);
			recipientView.setText(recipient.toString());
			updateRecipientHeader();
			setThreadStatus(R.string.show_conversation_status_ready);

			String draftMessage = mIntent.getStringExtra(EXTRA_DRAFT_TEXT);
			if (!TextUtils.isEmpty(draftMessage)) {
				message.setText(draftMessage);
				message.setSelection(draftMessage.length());
				setThreadStatus(getString(R.string.show_conversation_status_draft_ready, recipient.toString()));
			}

			View sendButton = findViewById(R.id.show_message_ui_btn_send_message);
			sendButton.setOnClickListener(this);
			sendButton.setContentDescription(getString(
					R.string.show_conversation_send_button_description,
					recipient.toString()));

			list.setStackFromBottom(true);
			list.setTranscriptMode(
					ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);

		}catch (Exception e){
			Log.e(TAG, e.getMessage(), e);
			app.displayToastMessage(e.getMessage());
			this.finish();
		}
	}

	private void sendMessage() {
		// send the message
			CharSequence messageText = message.getText();
			if (messageText == null || messageText.length() == 0)
				return;
			final String outgoingMessage = messageText.toString();
			app.runOnBackgroundThread(() -> {
				boolean sent = false;
				try {
					app.server.getRestfulClient().meshmsSendMessage(identity.subscriber.sid, recipient.getSubscriberId(), outgoingMessage);
					sent = true;
				} catch (Exception e) {
					Log.e(TAG, e.getMessage(), e);
					app.displayToastMessage(e.getMessage());
				}

				final boolean messageSent = sent;
				runOnUiThread(() -> {
					if (isFinishing()) {
						return;
					}
					if (messageSent) {
						message.setText("");
						setThreadStatus(R.string.show_conversation_status_sent);
						populateList();
					} else {
						setThreadStatus(R.string.show_conversation_status_send_failed);
					}
				});
			});
	}

	/*
	 * get the required data and populate the cursor
	 */
	private void populateList() {
		if (!app.isMainThread()) {
			// refresh the message list
			runOnUiThread(this::populateList);
			return;
		}
		final int requestGeneration = ++loadRequestGeneration;
		setThreadStatus(R.string.show_conversation_status_loading);
		app.runOnBackgroundThread(() -> {
			List<Object> finalItems = null;
			try {
				MeshMSMessageList results = app.server.getRestfulClient().meshmsListMessages(identity.subscriber.sid, recipient.getSubscriberId());
				MeshMSMessage item;
				LinkedList<Object> listItems = new LinkedList<>();
				boolean firstRead = true, firstDelivered = true, firstWindow = true;
				DateFormat df = DateFormat.getDateInstance();
				DateFormat tf = DateFormat.getTimeInstance(DateFormat.SHORT);
				long lastTimestamp = System.currentTimeMillis() / 1000;
				String lastDate = df.format(new Date());

				while ((item = results.next()) != null) {
					switch (item.type) {
						case MESSAGE_SENT:
							if (item.isDelivered && firstDelivered) {
								listItems.addFirst(getString(R.string.meshms_delivered));
								firstDelivered = false;
							}
							break;
						case MESSAGE_RECEIVED:
							if (item.isRead && firstRead) {
								listItems.addFirst(getString(R.string.meshms_read));
								firstRead = false;
							}
							break;
						default:
							continue;
					}

					if (item.timestamp != 0) {
						String messageDate = df.format(new Date(item.timestamp * 1000));
						if (!messageDate.equals(lastDate)) {
							listItems.addFirst("--- " + messageDate + " ---");
						} else if (lastTimestamp - item.timestamp >= 30 * 60) {
							listItems.addFirst("--- " + tf.format(new Date(item.timestamp * 1000)) + " ---");
						}
						lastDate = messageDate;
						lastTimestamp = item.timestamp;
					}

					listItems.addFirst(item);
					if (firstWindow && listItems.size() > 10) {
						firstWindow = false;
						ArrayList<Object> partialItems = new ArrayList<>(listItems);
						runOnUiThread(() -> applyConversationItems(requestGeneration, partialItems));
					}
				}

				finalItems = new ArrayList<>(listItems);
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
				app.displayToastMessage(e.getMessage());
			}

			final List<Object> loadedItems = finalItems;
			runOnUiThread(() -> {
				if (loadedItems != null) {
					applyConversationItems(requestGeneration, loadedItems);
				}
			});
		});
	}

	private void applyConversationItems(int requestGeneration, List<Object> listItems) {
		if (isFinishing() || requestGeneration != loadRequestGeneration) {
			return;
		}
		adapter.setItems(listItems);
		setThreadStatus(R.string.show_conversation_status_ready);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onPause()
	 */
	@Override
	public void onPause() {
		PeerListService.removeListener(this);
		if (receiverRegistered) {
			this.unregisterReceiver(receiver);
			receiverRegistered = false;
		}
		app.runOnBackgroundThread(new Runnable() {
			@Override
			public void run() {
				app.meshMS.markRead(recipient.getSubscriberId());
			}
		});
		super.onPause();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onResume()
	 */
	@Override
	public void onResume() {
		if (!receiverRegistered) {
			IntentFilter filter = new IntentFilter();
			filter.addAction(MeshMS.NEW_MESSAGES);
			ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
			receiverRegistered = true;
		}
		// get the data
		PeerListService.addListener(this);
		populateList();
		super.onResume();
	}

	@Override
	public long getId(int position, Object object) {
		return 0;
	}

	@Override
	public int getViewType(int position, Object object) {
		if (object instanceof MeshMSMessage){
			MeshMSMessage meshMSMessage = (MeshMSMessage) object;
			switch (meshMSMessage.type) {
				case MESSAGE_SENT:
					return 0;
				case MESSAGE_RECEIVED:
					return 1;
				case ACK_RECEIVED:
					return 2;
			}
		}
		return 2;
	}

	@Override
	public void bindView(int position, Object object, View view) {
		TextView messageText = view.findViewById(R.id.message_text);
		if (object instanceof MeshMSMessage) {
			MeshMSMessage meshMSMessage = (MeshMSMessage) object;
			messageText.setText(meshMSMessage.text);
			switch (meshMSMessage.type) {
				case MESSAGE_SENT:
					messageText.setContentDescription(getString(
							R.string.show_conversation_message_sent_accessibility,
							meshMSMessage.text));
					break;
				case MESSAGE_RECEIVED:
					messageText.setContentDescription(getString(
							R.string.show_conversation_message_received_accessibility,
							recipient == null ? getString(R.string.contacts_header) : recipient.toString(),
							meshMSMessage.text));
					break;
				default:
					messageText.setContentDescription(getString(
							R.string.show_conversation_status_accessibility,
							meshMSMessage.text));
					break;
			}
		}else {
			String statusText = object.toString();
			messageText.setText(statusText);
			messageText.setContentDescription(getString(
					R.string.show_conversation_status_accessibility,
					statusText));
		}
		view.setContentDescription(messageText.getContentDescription());
	}

	@Override
	public int[] getResourceIds() {
		return new int[]{
			R.layout.show_conversation_item_us,
			R.layout.show_conversation_item_them,
			R.layout.show_conversation_item_status
		};
	}

	@Override
	public boolean hasStableIds() {
		return false;
	}

	@Override
	public boolean isEnabled(Object object) {
		return false;
	}

	@Override
	public void peerChanged(Peer p) {
		if (p == recipient){
			runOnUiThread(() -> {
				TextView recipientView = findViewById(R.id.show_conversation_ui_recipient);
				recipientView.setText(recipient.toString());
				updateRecipientHeader();
				View sendButton = findViewById(R.id.show_message_ui_btn_send_message);
				sendButton.setContentDescription(getString(
						R.string.show_conversation_send_button_description,
						recipient.toString()));
			});
		}
	}

	private void updateRecipientHeader() {
		if (recipientMetaView == null || recipient == null) {
			return;
		}
		String did = recipient.getDid();
		if (did == null || did.trim().isEmpty()) {
			recipientMetaView.setText(getString(
					R.string.show_conversation_recipient_meta_no_number,
					recipient.getSubscriberId().abbreviation()));
			return;
		}
		recipientMetaView.setText(getString(
				R.string.show_conversation_recipient_meta,
				did,
				recipient.getSubscriberId().abbreviation()));
	}

	private void setThreadStatus(int statusResId) {
		if (threadStatusView != null) {
			threadStatusView.setText(statusResId);
		}
	}

	private void setThreadStatus(String statusText) {
		if (threadStatusView != null) {
			threadStatusView.setText(statusText);
		}
	}
}
