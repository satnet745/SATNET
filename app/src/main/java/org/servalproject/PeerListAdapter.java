/*
 * SATNET maintenance note:
 * This file is maintained as part of SATNET and builds on historical upstream work.
 * Copyright (C) 2011 The Serval Project.
 * Licensed under GPL-3.0-or-later; see LICENSE-SOFTWARE.md.
 */

package org.servalproject;

/*
 *
 * @author Jeremy Lakeman <jeremy@servalproject.org>
 *
 *         Peer List fetches a list of known peers from the PeerListService.
 *         When a peer is received from the service it is resolved through
 *         ServalD using the current background execution path.
 */

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.servalproject.messages.ShowConversationActivity;
import org.servalproject.servald.IPeer;
import org.servalproject.servald.ServalD;
import org.servalproject.servaldna.SubscriberId;

import java.util.List;

public class PeerListAdapter<T extends IPeer> extends ArrayAdapter<T> {
	public PeerListAdapter(Context context, List<T> peers) {
		super(context, R.layout.peer, R.id.Name, peers);
	}

	@Override
	public @NonNull View getView(final int position, View convertView, @NonNull ViewGroup parent) {
		View ret = super.getView(position, convertView, parent);
		T p = this.getItem(position);

		TextView displayName = ret.findViewById(R.id.Name);
		TextView displaySid = ret.findViewById(R.id.sid);
		TextView displayNumber = ret.findViewById(R.id.Number);
		View chat = ret.findViewById(R.id.chat);
		View call = ret.findViewById(R.id.call);
		View contact = ret.findViewById(R.id.add_contact);
		if (p == null) {
			displayName.setText("");
			displaySid.setText("");
			displayNumber.setText("");
			chat.setEnabled(false);
			ret.setContentDescription(null);
			chat.setContentDescription(null);
			call.setContentDescription(null);
			contact.setContentDescription(null);
			call.setVisibility(View.INVISIBLE);
			contact.setVisibility(View.INVISIBLE);
			return ret;
		}
		SubscriberId subscriberId = p.getSubscriberId();
		if (subscriberId == null) {
			displayName.setText(p.toString());
			displaySid.setText("");
			displayNumber.setText("");
			chat.setEnabled(false);
			ret.setContentDescription(null);
			chat.setContentDescription(null);
			call.setContentDescription(null);
			contact.setContentDescription(null);
			call.setVisibility(View.INVISIBLE);
			contact.setVisibility(View.INVISIBLE);
			return ret;
		}

		displaySid.setText(subscriberId.abbreviation());
		String did = p.getDid();
		if (did == null || did.trim().isEmpty()) {
			did = getContext().getString(R.string.messages_list_no_number);
		}
		displayNumber.setText(did);

		if (subscriberId.isBroadcast()) {
			call.setVisibility(View.INVISIBLE);
		} else {
			call.setVisibility(View.VISIBLE);
		}

		if (p.getContactId() >= 0) {
			contact.setVisibility(View.INVISIBLE);
		} else {
			contact.setVisibility(View.VISIBLE);
		}
		chat.setEnabled(true);

		int reachableTextColor = ContextCompat.getColor(getContext(), R.color.satnet_text_primary);
		int unavailableTextColor = ContextCompat.getColor(getContext(), R.color.satnet_text_secondary);

		if (p.isReachable()){
			displayName.setTextColor(reachableTextColor);
			displayNumber.setTextColor(reachableTextColor);
			displaySid.setTextColor(reachableTextColor);
		}else{
			displayName.setTextColor(unavailableTextColor);
			displayNumber.setTextColor(unavailableTextColor);
			displaySid.setTextColor(unavailableTextColor);
		}

		String peerName = p.toString();
		String reachabilitySummary = getContext().getString(
				p.isReachable() ? R.string.peer_list_reachable : R.string.peer_list_unreachable);
		ret.setContentDescription(getContext().getString(
				R.string.peer_list_row_accessibility,
				peerName,
				did,
				subscriberId.abbreviation(),
				reachabilitySummary));
		chat.setContentDescription(getContext().getString(R.string.peer_list_action_message, peerName));
		call.setContentDescription(getContext().getString(R.string.peer_list_action_call, peerName));
		contact.setContentDescription(getContext().getString(R.string.peer_list_action_add_contact, peerName));

		chat.setOnClickListener(v -> {
			ServalBatPhoneApplication app = ServalBatPhoneApplication.context;

			T clickedPeer = getItem(position);
			if (clickedPeer == null) {
				app.displayToastMessage("Unable to open conversation for this peer");
				return;
			}

			if (!ServalD.isRhizomeEnabled()) {
				app.displayToastMessage("Messaging cannot function without an sdcard");
				return;
			}

			SubscriberId clickSubscriberId = clickedPeer.getSubscriberId();
			if (clickSubscriberId == null) {
				app.displayToastMessage("This peer is missing a valid subscriber ID");
				return;
			}

			Intent intent = ShowConversationActivity.createIntent(getContext(), clickSubscriberId);
			getContext().startActivity(intent);
		});
		contact.setOnClickListener(v -> {
			T clickedPeer = getItem(position);
			if (clickedPeer == null) {
				ServalBatPhoneApplication.context.displayToastMessage("Unable to open this peer");
				return;
			}

			// Create contact if required
			try {
				clickedPeer.addContact(getContext());

				v.setVisibility(View.INVISIBLE);

				// now display/edit contact
				Intent intent = new Intent(Intent.ACTION_VIEW,
						Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, String.valueOf(clickedPeer.getContactId())));
				getContext().startActivity(intent);
			} catch (Exception e) {
				Log.e("PeerList", e.getMessage(), e);
				ServalBatPhoneApplication.context.displayToastMessage(e.getMessage());
			}
		});

		return ret;
	}

}