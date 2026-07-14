package org.servalproject.rhizome;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.messages.MessagesListActivity;
import org.servalproject.messages.ShowConversationActivity;
import org.servalproject.servald.ServalD;
import org.servalproject.servaldna.SubscriberId;
import org.servalproject.servaldna.meshms.MeshMSConversation;
import org.servalproject.servaldna.meshms.MeshMSConversationList;

public class MeshMS {
	private final ServalBatPhoneApplication app;
	private final SubscriberId sid;
	private static final String TAG="MeshMS";
	public static final String NEW_MESSAGES="org.servalproject.meshms.NEW";

	public MeshMS(ServalBatPhoneApplication app, SubscriberId sid){
		this.app=app;
		this.sid=sid;
	}

	public void bundleArrived(RhizomeManifest_MeshMS meshms) throws RhizomeManifest.MissingField {
		if (sid.equals(meshms.getRecipient())){
			initialiseNotification();
			Intent refreshIntent = new Intent(NEW_MESSAGES).setPackage(app.getPackageName());
			app.sendBroadcast(refreshIntent);
		}
	}

	public void markRead(SubscriberId recipient){
		try {
			app.server.getRestfulClient().meshmsMarkAllMessagesRead(sid, recipient);
		} catch (Exception e) {
			app.displayToastMessage(e.getMessage());
			Log.e(TAG, e.getMessage(), e);
		}
		cancelNotification();
		initialiseNotification();
	}

	private int lastMessageHash =0;

	// build an initial notification on startup
	public void initialiseNotification() {
		if (app.isMainThread()){
			app.runOnBackgroundThread(this::initialiseNotification);
			return;
		}

		if (!ServalD.isRhizomeEnabled())
			return;

		SubscriberId recipient=null;
		boolean unread=false;
		int messageHash=0;
		try {
			MeshMSConversationList conversations = app.server.getRestfulClient().meshmsListConversations(sid);
			MeshMSConversation conv;
			while ((conv = conversations.nextConversation()) != null) {
				// detect when the number of incoming messages has changed
				if (conv.isRead)
					continue;

				messageHash =
						conv.them.sid.hashCode() ^
								(int) conv.lastMessageOffset ^
								(int) (conv.lastMessageOffset >> 32);

				Log.v(TAG, conv.them.sid.abbreviation()+", lastOffset = "+conv.lastMessageOffset+", hash = "+messageHash+", read = "+conv.isRead);

				// remember the recipient, if it is the only recipient with unread messages
				if (unread) {
					recipient = null;
				} else {
					recipient = conv.them.sid;
				}
				unread = true;
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}

		Log.v(TAG, "unread = "+unread+", hash = "+messageHash+", lastHash = "+lastMessageHash);
		if (!unread){
			cancelNotification();
			return;
		}
		if (messageHash == lastMessageHash)
			return;
		lastMessageHash = messageHash;
		// For now, just indicate that there are some unread messages
		String content = "Unread message(s)";
		Intent intent = new Intent(Intent.ACTION_MAIN);

		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
				| Intent.FLAG_ACTIVITY_SINGLE_TOP
				| Intent.FLAG_ACTIVITY_CLEAR_TOP);
		if (recipient==null) {
			intent.setClass(app, MessagesListActivity.class);
		}else{
			intent = ShowConversationActivity.createIntent(app, recipient)
					.setAction(Intent.ACTION_MAIN)
					.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
							| Intent.FLAG_ACTIVITY_SINGLE_TOP
							| Intent.FLAG_ACTIVITY_CLEAR_TOP);
		}

		PendingIntent pendingIntent = PendingIntent.getActivity(app, 0,
				intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

		Notification n = ServalBatPhoneApplication.buildNotification(
				app,
				pendingIntent,
				R.drawable.ic_serval_logo,
				"",
				content,
				Notification.FLAG_SHOW_LIGHTS
						| Notification.FLAG_AUTO_CANCEL
		);

		n.defaults |= Notification.DEFAULT_VIBRATE
				| Notification.DEFAULT_LIGHTS;

		SharedPreferences settings = app.settings;
		String sound = settings.getString("meshms_notification_sound",
				null);
		if (sound == null) {
			n.defaults |= Notification.DEFAULT_SOUND;
		} else {
			n.sound = Uri.parse(sound);
		}

		NotificationManager nm = (NotificationManager) app
				.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.notify("meshms", ServalBatPhoneApplication.NOTIFY_MESSAGES, n);
	}

	public void cancelNotification() {
		NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel("meshms", ServalBatPhoneApplication.NOTIFY_MESSAGES);
	}
}
