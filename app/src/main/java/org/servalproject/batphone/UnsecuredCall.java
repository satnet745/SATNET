package org.servalproject.batphone;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.account.AccountService;
import org.servalproject.permissions.RuntimePermissionGate;
import org.servalproject.servald.IPeerListListener;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servaldna.SubscriberId;

@SuppressWarnings("unused")
public class UnsecuredCall extends Activity implements OnClickListener {

	private static final int CALL_PERMISSIONS_REQUEST = 9001;
	private static final String[] AUDIO_PERMISSIONS = new String[]{Manifest.permission.RECORD_AUDIO};
	private static final String[] VIDEO_PERMISSIONS = new String[]{
			Manifest.permission.RECORD_AUDIO,
			Manifest.permission.CAMERA
	};

	ServalBatPhoneApplication app;
	CallHandler callHandler;

	private TextView remote_name;
	private TextView remote_number;
	private TextView action;
	private TextView routeIndicator;
	private TextView videoStatus;
	private ImageView callerImage;

	public static final String EXTRA_SID="sid";
	public static final String EXTRA_EXISTING="existing";
	public static final String EXTRA_VIDEO_ENABLED="video_enabled";

	// Video UI components
	private SurfaceView localVideoSurface;
	private SurfaceView remoteVideoSurface;
	private SurfaceHolder localVideoHolder;
	private SurfaceHolder remoteVideoHolder;
	private ImageButton videoToggleButton;
	private ImageButton cameraSwitchButton;
	private boolean videoMode = false;
	private boolean waitingForPermissionGrant = false;

	// Create runnable for posting
	final Runnable updateCallStatus = this::updateUI;
	private Button endButton;
	private Button incomingEndButton;
	private Button incomingAnswerButton;
	private Chronometer chron;

	private void updateUI()
	{
		if (callHandler==null)
			return;
		final Window win = getWindow();
		int incomingCallFlags =
				WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
				| WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
				| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
						| WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;

		Log.d("VoMPCall", "Updating UI for state " + callHandler.state);

		chron.setBase(callHandler.getCallStarted());

		action.setText(getString((callHandler.state==null? CallHandler.CallState.Prep:callHandler.state).displayResource));
		if (routeIndicator != null) {
			routeIndicator.setText(getString(R.string.route_indicator_format, callHandler.getRouteIndicatorText()));
		}
		if (callHandler.state == CallHandler.CallState.Ringing)
			win.addFlags(incomingCallFlags);
		else
			win.clearFlags(incomingCallFlags);

		if (callHandler.state!=null){
			switch (callHandler.state){
				case Ringing:
					incomingEndButton.setVisibility(View.VISIBLE);
					incomingAnswerButton.setVisibility(View.VISIBLE);
					endButton.setVisibility(View.GONE);
					return;
				case End:
					incomingEndButton.setVisibility(View.GONE);
					incomingAnswerButton.setVisibility(View.GONE);
					endButton.setVisibility(View.VISIBLE);
					chron.stop();
					callHandler.setCallUI(null);
					callHandler = null;
					return;
			}
		}
		incomingEndButton.setVisibility(View.GONE);
		incomingAnswerButton.setVisibility(View.GONE);
		endButton.setVisibility(View.VISIBLE);
	}

	private void processIntent(Intent intent) {
		CallHandler call = app.callHandler;
		try{
			SubscriberId sid = null;
			boolean existing = false;
			String action = intent.getAction();

			if (Intent.ACTION_VIEW.equals(action)) {
				// This activity has been triggered from clicking on a SID
				// in contacts.
				sid = AccountService.getContactSid(getContentResolver(), intent.getData());
			} else {
				String sidString = intent.getStringExtra(EXTRA_SID);
				if (sidString != null)
					sid = new SubscriberId(sidString);
				existing = intent.getBooleanExtra(EXTRA_EXISTING, false);
			}

			if (sid == null)
				throw new IllegalArgumentException("Missing argument sid");

			if (existing){
				if (call==null || !call.remotePeer.getSubscriberId().equals(sid))
					throw new Exception("That call no longer exists");

				call.setCallUI(this);
				this.callHandler = call;
			}else{
				this.callHandler = CallHandler.dial(this, PeerListService.getPeer(sid));
			}

			updatePeerDisplay();
			updateUI();
		}catch (Exception ex){
			ServalBatPhoneApplication.context.displayToastMessage(ex
					.getMessage());
			Log.e("BatPhone", ex.getMessage(), ex);
			finish();
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		processIntent(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Log.d("VoMPCall", "Activity started");

		app = (ServalBatPhoneApplication) this.getApplication();

		Intent intent = getIntent();
		videoMode = intent.getBooleanExtra(EXTRA_VIDEO_ENABLED, false);

		String[] requiredPermissions = videoMode ? VIDEO_PERMISSIONS : AUDIO_PERMISSIONS;
		if (!RuntimePermissionGate.ensurePermissions(this, requiredPermissions, CALL_PERMISSIONS_REQUEST)) {
			waitingForPermissionGrant = true;
			return;
		}

		if (videoMode) {
			setContentView(R.layout.incall_video);
		} else {
			setContentView(R.layout.incall);
		}

		chron = findViewById(R.id.call_time);
		remote_name = findViewById(R.id.caller_name);
		remote_number = findViewById(R.id.ph_no_display);
		action = findViewById(R.id.call_action_type);
		routeIndicator = findViewById(R.id.route_indicator);

		endButton = this.findViewById(R.id.cancel_call_button);
		endButton.setOnClickListener(this);

		incomingEndButton = this.findViewById(R.id.incoming_decline);
		incomingEndButton.setOnClickListener(this);

		incomingAnswerButton = this
				.findViewById(R.id.answer_button_incoming);
		incomingAnswerButton.setOnClickListener(this);

		// Initialize video UI if in video mode
		if (videoMode) {
			initializeVideoUI();
		}

		try{
			processIntent(this.getIntent());
		} catch (Exception e) {
			Log.e("VoMPCall", "Failed to process call intent", e);
		}
	}

	private void initializeVideoUI() {
		// Initialize video surfaces
		localVideoSurface = findViewById(R.id.local_video_surface);
		remoteVideoSurface = findViewById(R.id.remote_video_surface);
		videoStatus = findViewById(R.id.video_status);
		callerImage = findViewById(R.id.caller_image);

		if (localVideoSurface != null) {
			localVideoHolder = localVideoSurface.getHolder();
		}

		if (remoteVideoSurface != null) {
			remoteVideoHolder = remoteVideoSurface.getHolder();
		}

		// Initialize video control buttons
		videoToggleButton = findViewById(R.id.video_toggle_button);
		if (videoToggleButton != null) {
			videoToggleButton.setOnClickListener(this);
		}

		cameraSwitchButton = findViewById(R.id.camera_switch_button);
		if (cameraSwitchButton != null) {
			cameraSwitchButton.setOnClickListener(this);
		}
	}


	@Override
	public void onClick(View view) {
		int id = view.getId();
		if (id == R.id.cancel_call_button) {
			if (callHandler == null || callHandler.state == CallHandler.CallState.End){
				finish();
				return;
			}
			// fall through
			callHandler.hangup();
		} else if (id == R.id.incoming_decline) {
			if (callHandler != null)
				callHandler.hangup();
		} else if (id == R.id.answer_button_incoming) {
			if (callHandler != null)
				callHandler.pickup();
		} else if (id == R.id.video_toggle_button) {
			toggleVideo();
		} else if (id == R.id.camera_switch_button) {
			switchCamera();
		}
	}

	/**
	 * Toggle video on/off during call
	 */
	private void toggleVideo() {
		if (callHandler == null) {
			return;
		}

		if (callHandler.isVideoEnabled()) {
			// Disable video
			callHandler.disableVideo();
			updateVideoUI(false);
		} else {
			// Enable video
			android.view.Surface remoteSurface = null;
			if (remoteVideoHolder != null) {
				remoteSurface = remoteVideoHolder.getSurface();
			}

			boolean success = callHandler.enableVideo(localVideoHolder, remoteSurface);
			if (success) {
				updateVideoUI(true);
			}
		}
	}

	/**
	 * Switch between front and back camera
	 */
	private void switchCamera() {
		if (callHandler != null) {
			callHandler.switchCamera();
		}
	}

	/**
	 * Update UI to show/hide video elements
	 */
	private void updateVideoUI(boolean videoEnabled) {
		if (localVideoSurface != null) {
			localVideoSurface.setVisibility(videoEnabled ? View.VISIBLE : View.GONE);
		}

		if (remoteVideoSurface != null) {
			remoteVideoSurface.setVisibility(videoEnabled ? View.VISIBLE : View.GONE);
		}

		if (videoStatus != null) {
			videoStatus.setVisibility(videoEnabled ? View.VISIBLE : View.GONE);
		}

		if (callerImage != null) {
			callerImage.setVisibility(videoEnabled ? View.GONE : View.VISIBLE);
		}

		if (cameraSwitchButton != null) {
			cameraSwitchButton.setVisibility(videoEnabled ? View.VISIBLE : View.GONE);
		}
	}

	private final IPeerListListener peerListener = new IPeerListListener(){
		@Override
		public void peerChanged(Peer p) {
			if (callHandler==null)
				return;
			if (p == callHandler.remotePeer){
				runOnUiThread(UnsecuredCall.this::updatePeerDisplay);
			}
		}
	};

	private void updatePeerDisplay() {
		if (callHandler==null)
			return;
		remote_name.setText(callHandler.remotePeer.getContactName());
		remote_number.setText(callHandler.remotePeer.did);
	}

	@Override
	protected void onPause() {
		super.onPause();
		chron.stop();
		PeerListService.removeListener(peerListener);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (callHandler!=null){
			chron.setBase(callHandler.getCallStarted());
			if (callHandler.state != CallHandler.CallState.End)
				chron.start();
		}
		PeerListService.addListener(peerListener);
	}

	@Override
	public void onBackPressed() {
		// cancel call before going back.
		if (callHandler!=null && callHandler.state.ordinal() < CallHandler.CallState.InCall.ordinal())
			callHandler.hangup();
		super.onBackPressed();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode != CALL_PERMISSIONS_REQUEST) {
			return;
		}

		boolean granted = true;
		for (int result : grantResults) {
			if (result != PackageManager.PERMISSION_GRANTED) {
				granted = false;
				break;
			}
		}
		if (!granted) {
			Toast.makeText(this, "Microphone permission is required for calls", Toast.LENGTH_LONG).show();
			finish();
			return;
		}

		if (waitingForPermissionGrant) {
			waitingForPermissionGrant = false;
			recreate();
		}
	}

}
