package org.servalproject;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.servald.PeerListService;

/**
 *
 * Control service responsible for enabling wifi network, and keeping our process alive
 *
 */
public class Control extends Service {
	private static final int CONTROL_NOTIFICATION_ID = 1001;
	private ServalBatPhoneApplication app;
	private boolean showingNotification = false;
	private int peerCount = -1;
	private static final String TAG = "Control";

	private void updateNotification() {
		if (app.controlService != this || peerCount<0){
			if (showingNotification)
				this.stopForeground(true);
			showingNotification = false;
			return;
		}

		Intent intent = new Intent(app, Main.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

		PendingIntent pendingIntent = PendingIntent.getActivity(app, 0, intent,
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

		Notification notification = ServalBatPhoneApplication.buildNotification(
				Control.this,
				pendingIntent,
				R.drawable.ic_serval_logo,
				getString(R.string.app_name),
				app.getResources().getQuantityString(R.plurals.peers_label, peerCount, peerCount),
				Notification.FLAG_ONGOING_EVENT
		);

		try {
			this.startForeground(CONTROL_NOTIFICATION_ID, notification);
			showingNotification = true;
		} catch (RuntimeException e) {
			Log.e(TAG, "Failed to promote Control service to the foreground", e);
			showingNotification = false;
		}
	}

	public void updatePeerCount(int peerCount) {
		if (this.peerCount == peerCount)
			return;
		this.peerCount = peerCount;
		updateNotification();
	}

	@Override
	public void onCreate() {
		this.app = (ServalBatPhoneApplication) this.getApplication();
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		app.controlService = null;
		peerCount = -1;
		updateNotification();
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		State existing = app.getState();
		// Don't attempt to start the service if the current state is invalid
		// (ie Installing...)
		if (existing != State.Running) {
			Log.v("Control", "Unable to process request as app state is "
					+ existing);
			return START_NOT_STICKY;
		}

		peerCount =-1;
		app.controlService = this;
		updatePeerCount(PeerListService.getLastPeerCount());

		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

}
