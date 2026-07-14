package org.servalproject.batphone;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;
import android.view.SurfaceHolder;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.audio.AudioBuffer;
import org.servalproject.audio.AudioPlaybackStream;
import org.servalproject.audio.AudioRecordStream;
import org.servalproject.audio.AudioStream;
import org.servalproject.audio.BufferList;
import org.servalproject.audio.JitterStream;
import org.servalproject.audio.TranscodeStream;
import org.servalproject.servald.DnaResult;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servald.ServalDMonitor;
import org.servalproject.servaldna.SubscriberId;
import org.servalproject.servaldna.keyring.KeyringIdentity;
import org.servalproject.features.FeatureFlags;
import org.servalproject.video.VideoCallManager;
import org.servalproject.routing.MultiHopRoutingManager;
import org.servalproject.relay.InternetRelayClient;
import org.servalproject.relay.SmsRelayClient;
import org.servalproject.relay.CensorshipResistantRelay;
import org.servalproject.relay.RelayPacket;
import org.servalproject.relay.RelayPacketListener;
import org.servalproject.relay.RhizomeRelay;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

// This class maintains the state of a call
// handles the lifecycle of recording and playback
// and the triggers the display of any activities.
@SuppressWarnings({"deprecation", "unused"})
public class CallHandler {
	final Peer remotePeer;
	String did;
	String name;
	private int local_id = 0;
	private String localIdString = null;

	public enum CallState{
		Prep(R.string.outgoing_call),
		Ringing(R.string.incoming_call),
		RemoteRinging(R.string.outgoing_call),
		InCall(R.string.in_call),
		End(R.string.call_ended);

		public final int displayResource;
		CallState(int resource){
			this.displayResource = resource;
		}
	}

	public CallState state = null;
	public VoMP.Codec codec = VoMP.Codec.Signed16;
	private long lastKeepAliveTime;
	private long callStarted = SystemClock.elapsedRealtime();
	private boolean uiStarted = false;
	private boolean initiated = false;
	private final ServalBatPhoneApplication app;
	private final ServalDMonitor monitor;
	private UnsecuredCall ui;
	private MediaPlayer mediaPlayer;
	private BufferList bufferList;
	private final Timer timer = new Timer();

	private Thread audioRecordThread;
	private AudioRecordStream recorder;
	public JitterStream player;
	private boolean ringing = false;
	private boolean audioRunning = false;

	// Video call support
	private VideoCallManager videoManager;
	private boolean videoEnabled = false;
	private boolean remoteVideoEnabled = false;

	// Multi-hop and relay support
	private MultiHopRoutingManager routingManager;
	private InternetRelayClient relayClient;
	private MultiHopRoutingManager.RouteInfo currentRoute;
	private boolean useRelay = false;

	// Censorship-resistant relay support
	private SmsRelayClient smsRelay;
	private CensorshipResistantRelay censorshipRelay;
	private boolean useCensorshipResistant = false;
	private String fallbackCallId;
	private static boolean relayCallbacksRegistered = false;

	private static final String TAG = "CallHandler";
	private AudioStream monitorOutput = new AudioStream() {
		@Override
		public int write(AudioBuffer buff) throws IOException {
			try {
				if (isUsingFallbackTransport()) {
					byte[] payload = new byte[buff.dataLen];
					System.arraycopy(buff.buff, 0, payload, 0, buff.dataLen);
					RelayPacket packet = RelayPacket.create(RelayPacket.TYPE_AUDIO,
							getLocalSubscriberId(),
							remotePeer == null ? null : remotePeer.getSubscriberId());
					packet.callId = getOrCreateFallbackCallId();
					packet.codec = buff.codec == null ? codec.code : buff.codec.code;
					packet.sampleStart = buff.sampleStart;
					packet.sequence = buff.sequence;
					packet.payload = payload;
					sendFallbackPacket(packet);
					return 0;
				}
				if (monitor.hasStopped())
					throw new EOFException();
				monitor.sendMessageAndData(buff.buff, buff.dataLen, "audio ",
						localIdString, " ",
						buff.codec.codeString, " ",
						Integer.toString(buff.sampleStart), " ",
						Integer.toString(buff.sequence));
			} finally {
				buff.release();
			}
			return 0;
		}
	};

	public static void dial(DnaResult result) throws IOException {
		CallHandler call = createCall(result.peer);
		call.did = result.ext == null ? result.did : result.ext;
		call.name = result.name;
		call.dial();
	}

	public static void dial(Peer peer) throws IOException {
		dial(null, peer);
	}

	public static CallHandler dial(UnsecuredCall ui, Peer peer) throws IOException {
		CallHandler call = createCall(peer);
		call.ui = ui;
		call.dial();
		return call;
	}

	private static synchronized CallHandler createCall(Peer peer)
			throws IOException {
		ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
		if (app.callHandler != null)
			throw new IOException(
					"Only one call is allowed at a time");
		ServalDMonitor monitor = app.server.getMonitor();
		if (monitor == null)
			throw new IOException(
					"Not currently connected to serval daemon");
		app.callHandler = new CallHandler(app, monitor, peer);
		return app.callHandler;
	}

	private static class EventMonitor implements ServalDMonitor.Messages {
		private final ServalDMonitor monitor;
		ServalBatPhoneApplication app = ServalBatPhoneApplication.context;

		private EventMonitor(ServalDMonitor monitor){
			this.monitor = monitor;
			monitor.addHandler("CALLFROM", this);
			monitor.addHandler("CALLTO", this);
			monitor.addHandler("CODECS", this);
			monitor.addHandler("RINGING", this);
			monitor.addHandler("ANSWERED", this);
			monitor.addHandler("AUDIO", this);
			monitor.addHandler("HANGUP", this);
			monitor.addHandler("KEEPALIVE", this);
		}

		@Override
		public void onConnect(ServalDMonitor monitor) {
			// tell servald that we can initiate and answer phone calls, and
			// the list of codecs we support
			StringBuilder sb = new StringBuilder("monitor vomp");
			for (VoMP.Codec codec : VoMP.Codec.values()) {
				if (codec.isSupported())
					sb.append(' ').append(codec.codeString);
			}
			try {
				monitor.sendMessage(sb.toString());
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}

		@Override
		public void onDisconnect(ServalDMonitor monitor) {

		}

		private boolean checkSession(Iterator<String> args){
			int local_session = ServalDMonitor.parseIntHex(args.next());
			CallHandler call = app.callHandler;
			if (call != null && call.local_id == local_session){
				call.lastKeepAliveTime = SystemClock.elapsedRealtime();
				return true;
			}

			// one call at a time
			monitor.sendMessageAndLog("hangup ", Integer.toHexString(local_session));
			return false;
		}

		@Override
		public int message(String cmd, Iterator<String> args, InputStream in,
				int dataLength) throws IOException {
			int ret = 0;
			CallHandler call = app.callHandler;

			if (cmd.equalsIgnoreCase("HANGUP") && call==null)
				// NOOP
				return 0;

			int local_session = ServalDMonitor.parseIntHex(args.next());
			if (call==null){
				if(cmd.equals("CALLFROM")){
					try {
						args.next(); // local_sid
						args.next(); // local_did
						SubscriberId remote_sid = new SubscriberId(args.next());
						String remote_did = args.next();
						Peer peer = PeerListService.getPeer(remote_sid);

						call = createCall(peer);
						call.local_id = local_session;
						call.localIdString = Integer.toHexString(local_session);
						call.did = remote_did;
						call.lastKeepAliveTime = SystemClock.elapsedRealtime();
						monitor.sendMessageAndLog("ringing ",
								Integer.toHexString(local_session));
						call.setCallState(CallState.Ringing);
						return 0;
					} catch (SubscriberId.InvalidHexException e) {
						throw new IOException("invalid SubscriberId token: " + e);
					}
				}
			}else if (cmd.equalsIgnoreCase("CALLTO")) {
				try{
					SubscriberId my_sid = new SubscriberId(args.next());
					args.next(); // local_did
					SubscriberId remote_sid = new SubscriberId(args.next());
					args.next(); // remote_did

					if (   call.state == null
							&& call.remotePeer.getSubscriberId().equals(remote_sid)
							&& call.initiated){
						call.local_id = local_session;
						call.localIdString = Integer.toHexString(local_session);
						call.lastKeepAliveTime = SystemClock.elapsedRealtime();
						call.setCallState(CallState.Prep);
						return 0;
					}
				} catch (SubscriberId.InvalidHexException e) {
					throw new IOException("invalid SubscriberId token: " + e);
				}
			}else if(call.local_id==local_session){
				call.lastKeepAliveTime = SystemClock.elapsedRealtime();
				if (cmd.equalsIgnoreCase("CODECS")) {
					call.codecs(args);
				}else if(cmd.equalsIgnoreCase("RINGING")) {
					call.setCallState(CallState.RemoteRinging);
				}else if(cmd.equalsIgnoreCase("ANSWERED")) {
					call.setCallState(CallState.InCall);
				} else if (cmd.equalsIgnoreCase("AUDIO")) {
					ret += call.receivedAudio(args, in, dataLength);
				} else if (cmd.equalsIgnoreCase("HANGUP")) {
					call.setCallState(CallState.End);
				}
				return ret;
			}
			// one call at a time
			monitor.sendMessageAndLog("hangup ", Integer.toHexString(local_session));
			return ret;
		}
	}

	public static void registerMessageHandlers(ServalDMonitor monitor) {
		new EventMonitor(monitor);
	}

	private CallHandler(ServalBatPhoneApplication app, ServalDMonitor monitor,
			Peer peer) {
		this.app = app;
		this.monitor = monitor;
		this.remotePeer = peer;
		this.did = peer.did;
		this.name = peer.name;
		lastKeepAliveTime = SystemClock.elapsedRealtime();

		this.routingManager = MultiHopRoutingManager.getInstance();

		if (FeatureFlags.isRelayEnabled()) {
			this.relayClient = InternetRelayClient.getInstance();
			this.smsRelay = SmsRelayClient.getInstance(app);
			this.censorshipRelay = CensorshipResistantRelay.getInstance(app);
			applyRelayPreferences();
			registerRelayCallbacks();
		} else {
			Log.i(TAG, "Relay feature is disabled in this build");
		}

		discoverRouteToPeer();

		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				long now = SystemClock.elapsedRealtime();
				if (state == CallState.InCall && isUsingFallbackTransport()) {
					sendFallbackKeepAlive();
				}
				if (now > (lastKeepAliveTime + 5000)) {
					// End call if no keep alive received
					Log.d(TAG,
							"Keepalive expired for call: "
									+ lastKeepAliveTime + " vs "
									+ now);
					hangup();
				}
			}
		}, 0, 3000);
	}

	/**
	 * Discover the best route to the remote peer
	 */
	private void discoverRouteToPeer() {
		if (remotePeer == null) {
			return;
		}

		if (!FeatureFlags.isExperimentalRoutingEnabled()) {
			currentRoute = null;
			useRelay = false;
			useCensorshipResistant = false;
			return;
		}

		currentRoute = routingManager.findBestRoute(remotePeer.getSubscriberId());

		if (currentRoute != null) {
			Log.i(TAG, "Route to " + remotePeer.getDisplayName() + ": " +
					  currentRoute.type + " (" + currentRoute.hopCount + " hops)");

			switch (currentRoute.type) {
				case INTERNET_RELAY:
				case HYBRID:
					if (!FeatureFlags.isRelayEnabled()) {
						Log.i(TAG, "Skipping relay path because relay feature is disabled");
						break;
					}
					useRelay = true;
					establishRelaySession();
					break;

				case SMS_RELAY:
				case TOR_RELAY:
				case I2P_RELAY:
					if (!FeatureFlags.isRelayEnabled()) {
						Log.i(TAG, "Skipping censorship-resistant relay path because relay feature is disabled");
						break;
					}
					useCensorshipResistant = true;
					if (currentRoute.type == MultiHopRoutingManager.RouteType.SMS_RELAY) {
						establishSmsRelay();
					} else if (currentRoute.type == MultiHopRoutingManager.RouteType.TOR_RELAY) {
						establishTorRelay();
					} else {
						establishI2PRelay();
					}
					break;

				case SNEAKERNET:
					useCensorshipResistant = true;
					Log.i(TAG, "Using store-and-forward (Rhizome) - expect delays");
					break;

				default:
					break;
			}
		} else {
			Log.w(TAG, "No route found to " + remotePeer.getDisplayName());
		}
	}

	private void establishRelaySession() {
		if (!FeatureFlags.isRelayEnabled()) {
			useRelay = false;
			return;
		}
		if (relayClient == null) {
			useRelay = false;
			return;
		}
		if (!relayClient.isConnected()) {
			Log.i(TAG, "Connecting to relay server...");
			if (!relayClient.connect()) {
				Log.e(TAG, "Failed to connect to relay server");
				useRelay = false;
				return;
			}
		}

		boolean success = relayClient.establishSession(remotePeer.getSubscriberId());
		if (!success) {
			Log.e(TAG, "Failed to establish relay session");
			useRelay = false;
		} else {
			Log.i(TAG, "Relay session established");
		}
	}

	private void establishSmsRelay() {
		if (!FeatureFlags.isRelayEnabled()) {
			useCensorshipResistant = false;
			return;
		}
		if (smsRelay == null) {
			useCensorshipResistant = false;
			return;
		}
		if (!smsRelay.isAvailable()) {
			Log.e(TAG, "SMS relay not available");
			useCensorshipResistant = false;
			return;
		}

		if (remotePeer != null && (remotePeer.did != null || smsRelay.isAvailable())) {
			Log.i(TAG, "SMS relay established - audio will be low quality");
			app.displayToastMessage("Using SMS relay - lower quality, may incur SMS charges");
		} else {
			Log.e(TAG, "Failed to establish SMS relay");
			useCensorshipResistant = false;
		}
	}

	private void establishTorRelay() {
		if (!FeatureFlags.isRelayEnabled()) {
			useCensorshipResistant = false;
			return;
		}
		if (censorshipRelay == null) {
			useCensorshipResistant = false;
			return;
		}
		censorshipRelay.setProxyType(CensorshipResistantRelay.ProxyType.TOR);

		if (!censorshipRelay.connect()) {
			Log.e(TAG, "Failed to connect via Tor");
			app.displayToastMessage("Tor not available. Install Orbot.");
			useCensorshipResistant = false;
			return;
		}

		boolean success = censorshipRelay.establishSession(remotePeer.getSubscriberId());
		if (success) {
			Log.i(TAG, "Tor relay established - anonymous & censorship-resistant");
			app.displayToastMessage("Connected via Tor (anonymous)");
		} else {
			Log.e(TAG, "Failed to establish Tor session");
			useCensorshipResistant = false;
		}
	}

	private void establishI2PRelay() {
		if (!FeatureFlags.isRelayEnabled()) {
			useCensorshipResistant = false;
			return;
		}
		if (censorshipRelay == null) {
			useCensorshipResistant = false;
			return;
		}
		censorshipRelay.setProxyType(CensorshipResistantRelay.ProxyType.I2P);

		if (!censorshipRelay.connect()) {
			Log.e(TAG, "Failed to connect via I2P");
			app.displayToastMessage("I2P not available. Install I2P Android.");
			useCensorshipResistant = false;
			return;
		}

		boolean success = censorshipRelay.establishSession(remotePeer.getSubscriberId());
		if (success) {
			Log.i(TAG, "I2P relay established - anonymous & censorship-resistant");
			app.displayToastMessage("Connected via I2P (anonymous)");
		} else {
			Log.e(TAG, "Failed to establish I2P session");
			useCensorshipResistant = false;
		}
	}

	/**
	 * Check if peer is reachable (nearby or faraway)
	 */
	public boolean isPeerReachable() {
		if (currentRoute != null && currentRoute.isActive) {
			return true;
		}

		// Try to rediscover route
		discoverRouteToPeer();
		return currentRoute != null && currentRoute.isActive;
	}

	/**
	 * Get current route information
	 */
	public MultiHopRoutingManager.RouteInfo getCurrentRoute() {
		return currentRoute;
	}

	/**
	 * Check if call is using internet relay
	 */
	public boolean isUsingRelay() {
		return useRelay;
	}

	/**
	 * Get hop count to peer
	 */
	public int getHopCount() {
		if (currentRoute != null) {
			return currentRoute.hopCount;
		}
		return -1;
	}

	public void hangup() {
		Log.d(TAG, "Hanging up");

		if (!isUsingFallbackTransport() && !monitor.hasStopped())
			monitor.sendMessageAndLog("hangup ", Integer.toHexString(local_id));
		else if (isUsingFallbackTransport())
			sendFallbackControl(RelayPacket.TYPE_CALL_END);

		// Stop video if enabled
		stopVideo();

		// Close relay session if using relay
		if (useRelay && remotePeer != null) {
			relayClient.closeSession(remotePeer.getSubscriberId());
		}

		// Close censorship-resistant relay if using
		if (useCensorshipResistant && remotePeer != null) {
			if (currentRoute != null) {
				switch (currentRoute.type) {
					case SMS_RELAY:
						smsRelay.sendCallSignal(remotePeer.getSubscriberId(), "CALL_END");
						break;
					case TOR_RELAY:
					case I2P_RELAY:
						censorshipRelay.closeSession(remotePeer.getSubscriberId());
						break;
					default:
						break;
				}
			}
		}

		setCallState(CallState.End);
	}

	/**
	 * Enable video calling for this call
	 */
	public boolean enableVideo(SurfaceHolder localPreview, android.view.Surface remoteSurface) {
		if (videoEnabled) {
			Log.w(TAG, "Video already enabled");
			return true;
		}

		if (videoManager == null) {
			videoManager = new VideoCallManager();
			videoManager.setVideoStreamCallback(new VideoCallManager.VideoStreamCallback() {
				@Override
				public void onVideoDataReady(byte[] data, int length) {
					// Send video data through monitor
					sendVideoData(data, length);
				}

				@Override
				public void onVideoFrameDecoded(byte[] data, int width, int height) {
					// Video frame decoded and ready for display
				}
			});
		}

		boolean success = videoManager.startVideoCapture(localPreview);
		if (success && remoteSurface != null) {
			videoManager.initializeVideoDecoder(remoteSurface);
		}

		if (success) {
			videoEnabled = true;
			// Notify remote peer that video is enabled
			if (!monitor.hasStopped()) {
				monitor.sendMessageAndLog("video enable ", Integer.toHexString(local_id));
			}
			Log.i(TAG, "Video enabled for call");
		}

		return success;
	}

	/**
	 * Disable video calling
	 */
	public void disableVideo() {
		if (!videoEnabled) {
			return;
		}

		videoEnabled = false;

		if (videoManager != null) {
			videoManager.stopVideoCapture();
		}

		// Notify remote peer that video is disabled
		if (!monitor.hasStopped()) {
			monitor.sendMessageAndLog("video disable ", Integer.toHexString(local_id));
		}

		Log.i(TAG, "Video disabled for call");
	}

	/**
	 * Toggle video on/off
	 */
	public boolean toggleVideo(SurfaceHolder localPreview, android.view.Surface remoteSurface) {
		if (videoEnabled) {
			disableVideo();
			return false;
		} else {
			return enableVideo(localPreview, remoteSurface);
		}
	}

	/**
	 * Switch between front and back camera
	 */
	public void switchCamera() {
		if (videoManager != null && videoEnabled) {
			videoManager.switchCamera();
		}
	}

	/**
	 * Send video data to remote peer
	 */
	private void sendVideoData(byte[] data, int length) {
		if (!monitor.hasStopped() && videoEnabled) {
			try {
				monitor.sendMessageAndData(data, length, "video ",
						localIdString, " ", Integer.toString(length));
			} catch (IOException e) {
				Log.e(TAG, "Error sending video data", e);
			}
		}
	}

	/**
	 * Handle incoming video data from remote peer
	 */
	public void handleIncomingVideoData(byte[] data, int length) {
		if (videoManager != null && videoEnabled) {
			videoManager.feedVideoData(data, length);
		}
	}

	/**
	 * Stop video and release resources
	 */
	private void stopVideo() {
		if (videoManager != null) {
			videoManager.stopVideoCapture();
			videoManager = null;
		}
		videoEnabled = false;
		remoteVideoEnabled = false;
	}

	/**
	 * Check if video is currently enabled
	 */
	public boolean isVideoEnabled() {
		return videoEnabled;
	}

	/**
	 * Check if remote peer has video enabled
	 */
	public boolean isRemoteVideoEnabled() {
		return remoteVideoEnabled;
	}

	/**
	 * Set remote video status (called when receiving video enable/disable from peer)
	 */
	public void setRemoteVideoEnabled(boolean enabled) {
		this.remoteVideoEnabled = enabled;
		if (ui != null) {
			ui.runOnUiThread(ui.updateCallStatus);
		}
	}

	private void stopRinging(){
		if (!ringing)
			return;
		Log.v(TAG, "Stopping ring tone");
		if (mediaPlayer != null) {
			try {
				mediaPlayer.stop();
			}catch (Exception e){
				Log.e(TAG, e.getMessage(), e);
			}
			mediaPlayer.release();
			mediaPlayer = null;
		}
		Vibrator v = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
		if (v != null)
			v.cancel();
		ringing = false;
	}

	public void pickup() {
		CallHandler call = app.callHandler;
		if (state == CallState.Ringing && call !=null){
			Log.d(TAG, "Picking up");
			if (isUsingFallbackTransport()) {
				ensureFallbackAudioReady();
				sendFallbackControl(RelayPacket.TYPE_CALL_ACCEPT);
			} else {
				monitor.sendMessageAndLog("pickup ", Integer.toHexString(local_id));
			}
			call.setCallState(CallState.InCall);
		}
	}

	private void startRinging() {
		if (ringing)
			return;

		Log.v(TAG, "Starting ring tone");
		final AudioManager audioManager = (AudioManager) app
				.getSystemService(Context.AUDIO_SERVICE);
		if (audioManager.getStreamVolume(AudioManager.STREAM_RING) != 0) {
			Uri alert = RingtoneManager
					.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
			MediaPlayer m = new MediaPlayer();
			try {
				m.setDataSource(app, alert);
				m.setAudioStreamType(AudioManager.STREAM_RING);
				m.setLooping(true);
				m.prepare();
				m.start();
			} catch (Exception e) {
				m.release();
				Log.e(TAG,
						"Could not get ring tone: " + e.getMessage(), e);
			}
			mediaPlayer = m;
		} else {
			// volume off, so vibrate instead
			Vibrator v = (Vibrator) app
					.getSystemService(Context.VIBRATOR_SERVICE);
			if (v != null) {
				// bzzt-bzzt ...... bzzt,bzzt ......
				long[] pattern = {
						0, 300, 200, 300, 2000
				};
				v.vibrate(pattern, 0);
			}
		}

		ringing = true;
	}

	private void startAudio() {
		try {
			if (isUsingFallbackTransport()) {
				ensureFallbackAudioReady();
			}
			if (this.recorder == null)
				throw new IllegalStateException(
						"Audio recorder has not been initialised");
			Log.v(TAG, "Starting audio");

			this.recorder.setStream(TranscodeStream.getEncoder(monitorOutput,
					codec));

			AudioManager am = (AudioManager) app
					.getSystemService(Context.AUDIO_SERVICE);

			AudioPlaybackStream playback = new AudioPlaybackStream(
					am,
					AudioManager.STREAM_VOICE_CALL,
					SAMPLE_RATE,
					AudioFormat.CHANNEL_OUT_MONO,
					AudioFormat.ENCODING_PCM_16BIT,
					8 * 60 * 2);

			AudioStream output = TranscodeStream.getDecoder(playback);

			this.player = new JitterStream(output);
			this.player.startPlaying();

			audioRunning = true;
		} catch (Exception e) {
			Log.v(TAG, e.getMessage(), e);
		}
	}

	private void stopAudio() {
		if (this.recorder == null)
			throw new IllegalStateException(
					"Audio recorder has not been initialised");
		Log.v(TAG, "Stopping audio");
		this.recorder.close();
		try {
			this.player.close();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		audioRunning = false;
	}

	static final int SAMPLE_RATE = 8000;

	private synchronized void setCallState(CallState state) {
		if (this.state == state)
			return;
		this.state = state;
		Log.v(TAG, "Call state changed to " + state);

		if (state == CallState.InCall && isUsingFallbackTransport()) {
			ensureFallbackAudioReady();
		}

		// TODO play audio indicator for Prep / RemoteRinging / End

		if (ringing != (state == CallState.Ringing)) {
			if (ringing)
				stopRinging();
			else
				startRinging();
		}
		if (audioRunning != (state == CallState.InCall)) {
			if (audioRunning) {
				stopAudio();
			} else {
				callStarted = SystemClock.elapsedRealtime();
				startAudio();
			}
		}

		Intent myIntent = new Intent(
				app,
				UnsecuredCall.class);

		myIntent.putExtra(UnsecuredCall.EXTRA_SID, remotePeer.getSubscriberId().toHex());
		myIntent.putExtra(UnsecuredCall.EXTRA_EXISTING, true);

		// Create call as a standalone activity
		// stack
		myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
				Intent.FLAG_ACTIVITY_CLEAR_TOP |
				Intent.FLAG_ACTIVITY_SINGLE_TOP);

		// open the UI if we initiated the call, or we reached ringing
		// state.
		if (ui != null)
			ui.runOnUiThread(ui.updateCallStatus);
		else if(state != CallState.End && !uiStarted) {
			Log.v(TAG, "Starting in call ui");
			uiStarted = true;
			ServalBatPhoneApplication.context.startActivity(myIntent);
		}

		// make sure invalid states don't open the UI
		NotificationManager nm = (NotificationManager) app
				.getSystemService(Context.NOTIFICATION_SERVICE);

		if (state == CallState.End){
			if (this.recorder != null) {
				this.recorder.close();
				recorder = null;
			}
			if (this.player != null)
				try {
					this.player.close();
				} catch (IOException e) {
					Log.e(TAG, e.getMessage(), e);
				}
			timer.cancel();
			nm.cancel("Call", ServalBatPhoneApplication.NOTIFY_CALL);
			app.callHandler = null;
		}else{

			// Update the in call notification so the user can re-open the UI
			PendingIntent pendingIntent = PendingIntent.getActivity(app, 0,
					myIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);

			Notification inCall = ServalBatPhoneApplication.buildNotification(
					app,
					pendingIntent,
					android.R.drawable.stat_sys_phone_call,
					app.getString(R.string.ongoing_call),
					remotePeer.getDisplayName(),
					0
			);

			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
					|| app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
					== android.content.pm.PackageManager.PERMISSION_GRANTED) {
				nm.notify("Call", ServalBatPhoneApplication.NOTIFY_CALL, inCall);
			} else {
				Log.i(TAG, "Skipping call notification: POST_NOTIFICATIONS not granted");
			}
		}
	}

	public void setCallUI(UnsecuredCall ui) {
		this.ui = ui;
		uiStarted = ui != null;
	}

	public void dial() {

		try{
			if (isUsingFallbackTransport()) {
				initiated = true;
				local_id = (int) (System.currentTimeMillis() & 0x7fffffff);
				localIdString = Integer.toHexString(local_id);
				if (currentRoute != null && currentRoute.type == MultiHopRoutingManager.RouteType.SNEAKERNET) {
					RelayPacket packet = RelayPacket.create(RelayPacket.TYPE_MISSED_CALL,
							app.server.getIdentity().sid,
							remotePeer.sid);
					packet.callId = getOrCreateFallbackCallId();
					packet.text = transportHintForCurrentRoute();
					if (RhizomeRelay.publishPacket(packet)) {
						app.displayToastMessage("Queued Rhizome call request for delayed delivery");
						setCallState(CallState.End);
					} else {
						throw new IOException("Unable to queue Rhizome fallback");
					}
					return;
				}
				setCallState(CallState.Prep);
				sendFallbackControl(RelayPacket.TYPE_CALL_INIT);
				return;
			}
			KeyringIdentity identity = app.server.getIdentity();
			Log.v(TAG, "Calling " + remotePeer.sid.abbreviation() + "/"
					+ did);
			initiated = true;
			monitor.sendMessageAndLog("call ",
					remotePeer.sid.toHex(), " ",
					identity.did, " ", did);
		}catch (Exception e){
			Log.e(TAG, e.getMessage(), e);
			app.displayToastMessage(e.getMessage());
		}
	}

	public int receivedAudio(Iterator<String> args, InputStream in,
			int dataBytes) throws IOException {
		// ignore audio if not in call
		if (state != CallState.InCall)
			return 0;

		if (bufferList == null)
			bufferList = new BufferList(VoMP.Codec.Signed16.maxBufferSize() / 2);

		if (dataBytes > bufferList.mtu) {
			Log.v(TAG, "Audio size " + dataBytes
					+ " is larger than buffer MTU " + bufferList.mtu);
			return 0;
		}
		AudioBuffer buff = bufferList.getBuffer();

		buff.received = lastKeepAliveTime;

		buff.codec = VoMP.Codec.getCodec(ServalDMonitor
				.parseInt(args.next()));
		buff.sampleStart = ServalDMonitor.parseInt(args.next());
		buff.sequence = ServalDMonitor.parseInt(args.next()); // sequence
		player.setJitterDelay(ServalDMonitor.parseInt(args.next()));
		buff.thisDelay = ServalDMonitor.parseInt(args.next());
		buff.dataLen = dataBytes;

		int read = 0;
		while (read < dataBytes) {
			int actualRead = in.read(buff.buff, read, dataBytes - read);
			if (actualRead < 0)
				throw new EOFException();
			read += actualRead;
		}
		player.write(buff);
		return read;
	}

	private void receivedRelayAudio(RelayPacket packet) throws IOException {
		if (state != CallState.InCall || packet.payload == null)
			return;

		if (bufferList == null)
			bufferList = new BufferList(VoMP.Codec.Signed16.maxBufferSize() / 2);
		if (player == null)
			return;

		AudioBuffer buff = bufferList.getBuffer();
		buff.received = lastKeepAliveTime;
		buff.codec = VoMP.Codec.getCodec(packet.codec);
		if (buff.codec == null)
			buff.codec = codec;
		buff.sampleStart = packet.sampleStart;
		buff.sequence = packet.sequence;
		player.setJitterDelay(packet.jitterDelay);
		buff.thisDelay = packet.thisDelay;
		buff.dataLen = Math.min(packet.payload.length, buff.buff.length);
		System.arraycopy(packet.payload, 0, buff.buff, 0, buff.dataLen);
		player.write(buff);
	}

	public void codecs(Iterator<String> args) {
		try {
			VoMP.Codec best = null;

			while (args.hasNext()) {
				int c = ServalDMonitor.parseInt(args.next());
				VoMP.Codec codec = VoMP.Codec.getCodec(c);
				if (codec == null || !codec.isSupported())
					continue;

				if (best == null || codec.preference > best.preference) {
					best = codec;
				}
			}

			if (best == null)
				throw new IOException("Unable to find a common codec");

			this.codec = best;
			int audioSource = 7; // MediaRecorder.AudioSource.VOICE_COMMUNICATION;
			recorder = new AudioRecordStream(
					null,
					audioSource,
					codec.sampleRate,
					AudioFormat.CHANNEL_IN_MONO,
					AudioFormat.ENCODING_PCM_16BIT,
					8 * 100 * 2,
					codec.audioBufferSize(),
					codec.maxBufferSize());

			audioRecordThread = new Thread(recorder, "Recording");
			audioRecordThread.start();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
			this.hangup();
		}
	}

	public long getCallStarted() {
		return callStarted;
	}

	public String getRouteIndicatorText() {
		if (currentRoute == null || currentRoute.type == null) {
			return app.getString(R.string.route_indicator_none);
		}
		int routeLabel;
		switch (currentRoute.type) {
			case INTERNET_RELAY:
				routeLabel = R.string.route_relay;
				break;
			case HYBRID:
				routeLabel = R.string.route_hybrid;
				break;
			case SMS_RELAY:
				routeLabel = R.string.route_sms;
				break;
			case TOR_RELAY:
				routeLabel = R.string.route_tor;
				break;
			case I2P_RELAY:
				routeLabel = R.string.route_i2p;
				break;
			case SNEAKERNET:
				routeLabel = R.string.route_sneakernet;
				break;
			case MULTI_HOP:
				routeLabel = R.string.route_multihop;
				break;
			case DIRECT:
			default:
				routeLabel = R.string.route_direct;
				break;
		}
		String routeName = app.getString(routeLabel);
		if (currentRoute.hopCount > 1) {
			return app.getString(R.string.route_indicator_with_hops, routeName, currentRoute.hopCount);
		}
		return routeName;
	}

	private static synchronized void registerRelayCallbacks() {
		if (relayCallbacksRegistered) {
			return;
		}
		RelayPacketListener listener = new RelayPacketListener() {
			@Override
			public void onPacketReceived(RelayPacket packet) {
				handleIncomingRelayPacket(packet);
			}

			@Override
			public void onConnectionEstablished(SubscriberId peer) {
				Log.i(TAG, "Relay connection established with " + peer);
			}

			@Override
			public void onConnectionLost(SubscriberId peer) {
				CallHandler call = ServalBatPhoneApplication.context.callHandler;
				if (call != null && call.remotePeer != null && call.remotePeer.getSubscriberId().equals(peer)) {
					call.hangup();
				}
			}
		};
		InternetRelayClient.getInstance().setCallback(listener);
		SmsRelayClient.getInstance(ServalBatPhoneApplication.context).setCallback(listener);
		CensorshipResistantRelay.getInstance(ServalBatPhoneApplication.context).setCallback(listener);
		relayCallbacksRegistered = true;
	}

	public static synchronized void handleIncomingRelayPacket(RelayPacket packet) {
		if (packet == null) {
			return;
		}
		ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
		CallHandler call = app.callHandler;
		try {
			SubscriberId fromSid = packet.getFromSubscriberId();
			if (fromSid == null) {
				return;
			}
			if (call == null) {
				if (!RelayPacket.TYPE_CALL_INIT.equals(packet.type) && !RelayPacket.TYPE_MISSED_CALL.equals(packet.type)) {
					return;
				}
				Peer peer = PeerListService.getPeer(fromSid);
				call = createCall(peer);
				call.local_id = (int) (System.currentTimeMillis() & 0x7fffffff);
				call.localIdString = Integer.toHexString(call.local_id);
				call.fallbackCallId = packet.callId;
				call.applyTransportHint(packet.text);
				if (RelayPacket.TYPE_MISSED_CALL.equals(packet.type)) {
					app.displayToastMessage("Received delayed Rhizome call request from " + peer.getDisplayName());
					call.setCallState(CallState.End);
					return;
				}
				call.setCallState(CallState.Ringing);
				call.sendFallbackControl(RelayPacket.TYPE_CALL_RINGING);
				return;
			}

			call.lastKeepAliveTime = SystemClock.elapsedRealtime();
			if (packet.callId != null && !packet.callId.isEmpty()) {
				call.fallbackCallId = packet.callId;
			}
			if (packet.text != null && !packet.text.isEmpty()) {
				call.applyTransportHint(packet.text);
			}
			if (RelayPacket.TYPE_CALL_RINGING.equals(packet.type)) {
				call.setCallState(CallState.RemoteRinging);
			} else if (RelayPacket.TYPE_CALL_ACCEPT.equals(packet.type) || RelayPacket.TYPE_SESSION_ACK.equals(packet.type)) {
				call.ensureFallbackAudioReady();
				call.setCallState(CallState.InCall);
			} else if (RelayPacket.TYPE_AUDIO.equals(packet.type)) {
				call.receivedRelayAudio(packet);
			} else if (RelayPacket.TYPE_CALL_END.equals(packet.type)) {
				call.setCallState(CallState.End);
			}
		} catch (Exception e) {
			Log.e(TAG, "Failed to process incoming relay packet", e);
		}
	}

	private boolean isUsingFallbackTransport() {
		return useRelay || useCensorshipResistant || (currentRoute != null && currentRoute.type == MultiHopRoutingManager.RouteType.SMS_RELAY)
				|| (currentRoute != null && currentRoute.type == MultiHopRoutingManager.RouteType.SNEAKERNET)
				|| (currentRoute != null && currentRoute.type == MultiHopRoutingManager.RouteType.TOR_RELAY)
				|| (currentRoute != null && currentRoute.type == MultiHopRoutingManager.RouteType.I2P_RELAY)
				|| (currentRoute != null && currentRoute.type == MultiHopRoutingManager.RouteType.INTERNET_RELAY)
				|| (currentRoute != null && currentRoute.type == MultiHopRoutingManager.RouteType.HYBRID);
	}

	private void ensureFallbackAudioReady() {
		if (recorder != null) {
			return;
		}
		try {
			codec = VoMP.Codec.Signed16;
			recorder = new AudioRecordStream(
					null,
					7,
					codec.sampleRate,
					AudioFormat.CHANNEL_IN_MONO,
					AudioFormat.ENCODING_PCM_16BIT,
					8 * 100 * 2,
					codec.audioBufferSize(),
					codec.maxBufferSize());
			audioRecordThread = new Thread(recorder, "FallbackRecording");
			audioRecordThread.start();
		} catch (IOException e) {
			Log.e(TAG, "Unable to initialise fallback audio", e);
		}
	}

	private String getOrCreateFallbackCallId() {
		if (fallbackCallId == null || fallbackCallId.isEmpty()) {
			fallbackCallId = Long.toHexString(System.currentTimeMillis());
		}
		return fallbackCallId;
	}

	private void sendFallbackKeepAlive() {
		if (!isUsingFallbackTransport()) {
			return;
		}
		sendFallbackControl(RelayPacket.TYPE_KEEPALIVE);
	}

	private void sendFallbackControl(String type) {
		try {
			RelayPacket packet = RelayPacket.create(type, getLocalSubscriberId(),
					remotePeer == null ? null : remotePeer.getSubscriberId());
			packet.callId = getOrCreateFallbackCallId();
			packet.text = transportHintForCurrentRoute();
			sendFallbackPacket(packet);
		} catch (Exception e) {
			Log.e(TAG, "Failed to send fallback control packet", e);
		}
	}

	private void sendFallbackPacket(RelayPacket packet) {
		if (currentRoute == null) {
			return;
		}
		switch (currentRoute.type) {
			case INTERNET_RELAY:
			case HYBRID:
				if (relayClient != null)
					relayClient.sendPacket(packet);
				return;
			case SMS_RELAY:
				if (smsRelay != null)
					smsRelay.sendPacket(packet, remotePeer == null ? null : remotePeer.did);
				return;
			case TOR_RELAY:
			case I2P_RELAY:
				if (censorshipRelay != null)
					censorshipRelay.sendPacket(packet);
				return;
			case SNEAKERNET:
				RhizomeRelay.publishPacket(packet);
				return;
			default:
				return;
		}
	}

	private String transportHintForCurrentRoute() {
		if (currentRoute == null || currentRoute.type == null) {
			return "transport:direct";
		}
		return "transport:" + currentRoute.type.name().toLowerCase(Locale.ROOT);
	}

	private void applyTransportHint(String hint) {
		if (hint == null || !hint.startsWith("transport:")) {
			return;
		}
		String route = hint.substring("transport:".length()).toUpperCase(Locale.ROOT);
		try {
			MultiHopRoutingManager.RouteType type = MultiHopRoutingManager.RouteType.valueOf(route);
			currentRoute = new MultiHopRoutingManager.RouteInfo(type,
					java.util.Collections.singletonList(remotePeer.getSubscriberId()));
			useRelay = type == MultiHopRoutingManager.RouteType.INTERNET_RELAY || type == MultiHopRoutingManager.RouteType.HYBRID;
			useCensorshipResistant = !useRelay && type != MultiHopRoutingManager.RouteType.DIRECT && type != MultiHopRoutingManager.RouteType.MULTI_HOP;
		} catch (Exception e) {
			Log.w(TAG, "Unknown relay transport hint " + hint, e);
		}
	}

	private SubscriberId getLocalSubscriberId() throws IOException {
		try {
			KeyringIdentity identity = app.server.getIdentity();
			if (identity == null || identity.sid == null) {
				throw new IOException("Local identity unavailable");
			}
			return identity.sid;
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException("Unable to resolve local identity", e);
		}
	}

	private void applyRelayPreferences() {
		String relayHostPref = app.settings.getString("relay_host", "");
		if (relayHostPref != null) {
			relayHostPref = relayHostPref.trim();
		}
		String smsRelayNumber = app.settings.getString("sms_relay_number", "");
		String anonymousPreference = app.settings.getString("relay_anonymous_preference", "AUTO");

		if (relayClient != null && relayHostPref != null && !relayHostPref.isEmpty()) {
			String host = relayHostPref;
			int port = 4110;
			int colon = relayHostPref.lastIndexOf(':');
			if (colon > 0 && colon < relayHostPref.length() - 1) {
				host = relayHostPref.substring(0, colon).trim();
				try {
					port = Integer.parseInt(relayHostPref.substring(colon + 1).trim());
				} catch (NumberFormatException e) {
					port = 4110;
				}
			}
			relayClient.configureRelay(host, port);
			if (censorshipRelay != null) {
				censorshipRelay.configureRelayEndpoint(host, port);
			}
		}

		if (smsRelay != null && smsRelayNumber != null && !smsRelayNumber.trim().isEmpty()) {
			smsRelay.configureRelay(smsRelayNumber.trim());
		}

		if (censorshipRelay != null) {
			censorshipRelay.refreshConfiguration(app);
			if ("TOR".equalsIgnoreCase(anonymousPreference)) {
				censorshipRelay.setProxyType(CensorshipResistantRelay.ProxyType.TOR);
			} else if ("I2P".equalsIgnoreCase(anonymousPreference)) {
				censorshipRelay.setProxyType(CensorshipResistantRelay.ProxyType.I2P);
			} else {
				censorshipRelay.setProxyType(CensorshipResistantRelay.ProxyType.AUTO);
			}
		}
	}

}
