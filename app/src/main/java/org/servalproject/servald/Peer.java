/*
 * SATNET maintenance note:
 * This file is maintained as part of SATNET and builds on historical upstream work.
 * Copyright (C) 2011 The Serval Project.
 * Licensed under GPL-3.0-or-later; see LICENSE-SOFTWARE.md.
 */
package org.servalproject.servald;

import android.content.Context;
import android.content.OperationApplicationException;
import android.os.RemoteException;

import org.servalproject.account.AccountService;
import org.servalproject.servaldna.SubscriberId;


public class Peer implements IPeer {
	public long contactId = -1;
	String contactName;
	public long cacheContactUntil = 0;
	public long cacheUntil = 0;
	public long nextRequest = 0;
	public final SubscriberId sid;
    private SubscriberId transmitter;
    private int hop_count;

	// did / name resolved from looking up the sid
	public String did;
	public String name;

	// every peer must have a sid
	Peer(SubscriberId sid) {
		this.sid = sid;
	}

	@Override
	public String getSortString() {
		return getContactName() + did + sid;
	}

	public String getDisplayName() {
		if (contactName != null && !contactName.equals(""))
			return contactName;
		if (name != null && !name.equals(""))
			return name;
		if (did != null && !did.equals(""))
			return did;
		return sid.abbreviation();
	}

	@Override
	public boolean hasName() {
		return (contactName != null && !contactName.equals(""))
				|| (name != null && !name.equals(""));
	}

	public String getContactName() {
		if (contactName != null && !contactName.equals(""))
			return contactName;
		if (name != null && !name.equals(""))
			return name;
		return "";
	}

	public void setContactName(String contactName) {
		this.contactName = contactName;
	}

    public boolean linkChanged(SubscriberId transmitter, int hop_count){
		if (transmitter == this.transmitter && hop_count== this.hop_count)
			return false;
        this.transmitter=transmitter;
        this.hop_count=hop_count;
		return true;
    }

    public SubscriberId getTransmitter(){
        return this.transmitter;
    }

    public int getHopCount(){
        return this.hop_count;
    }

	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (!(o instanceof Peer))
			return false;
		Peer other = (Peer) o;
		return this.sid.equals(other.sid);
	}

	@Override
	public int hashCode() {
		return sid.hashCode();
	}

	@Override
	public String toString() {
		if (contactName != null && !contactName.equals(""))
			return contactName;
		if (name != null && !name.equals(""))
			return name;
		if (did != null && !did.equals(""))
			return did;
		return sid.abbreviation();
	}

	public boolean hasDid() {
		return did != null && !did.equals("");
	}

	@Override
	public String getDid() {
		return did;
	}

	public boolean isReachable() {
		return this.transmitter!=null;
	}

	@Override
	public SubscriberId getSubscriberId() {
		return sid;
	}

	@Override
	public long getContactId() {
		return contactId;
	}

	@Override
	public void addContact(Context context) throws RemoteException,
			OperationApplicationException {
		if (contactId == -1) {
			contactId = AccountService.addContact(
					context, getContactName(), sid,
					did);
		}
	}
}
