# Video Calling Feature

## Overview

SATNET now supports **phone-to-phone video calling** over the mesh network, building on the existing VoIP voice call infrastructure. Video calls use H.264 encoding for efficient compression and can run alongside audio on the mesh network without requiring internet or cellular connectivity.

## Features

### Core Video Capabilities
- **Real-time video streaming** between mesh peers
- **H.264/AVC video codec** for efficient compression
- **Adaptive quality**: 640x480 resolution at 15 fps, 500 kbps bitrate
- **Picture-in-picture** local camera preview
- **Toggle video on/off** during active calls
- **Camera switching** between front and rear cameras
- **Graceful fallback** to audio-only if video fails

### User Interface
- **Dual layout system**: Standard audio layout and enhanced video layout
- **Full-screen remote video** with overlay controls
- **Local preview window** (120x160dp) in top-right corner
- **Video control buttons**:
  - Video toggle button (enable/disable video)
  - Camera switch button (front/back camera)
  - End call button
- **Visual indicators** for video status

## Technical Architecture

### Components

#### 1. VideoCallManager.java
Core video management class that handles:
- Camera initialization and configuration
- MediaCodec H.264 encoding/decoding
- Video frame capture and processing
- Streaming data callbacks
- Camera switching logic

**Key Parameters:**
```java
VIDEO_WIDTH = 640
VIDEO_HEIGHT = 480
VIDEO_FPS = 15
VIDEO_BITRATE = 500000 // 500 kbps
VIDEO_MIME_TYPE = "video/avc" // H.264
```

#### 2. CallHandler.java Extensions
Enhanced call handler with video support:
- `enableVideo()` - Start video capture and streaming
- `disableVideo()` - Stop video streaming
- `toggleVideo()` - Switch between video and audio-only modes
- `switchCamera()` - Change between front/back camera
- `handleIncomingVideoData()` - Process received video frames
- Video state management and remote peer notifications

#### 3. UnsecuredCall.java UI
Updated call activity supporting:
- Video-enabled layout (`incall_video.xml`)
- SurfaceView components for video display
- Video control button handlers
- Dynamic UI updates based on video state

### Video Streaming Protocol

Video data flows through the upstream Serval DNA monitor interface:

**Outgoing (Encoding):**
1. Camera captures frames → MediaCodec encoder
2. Encoder outputs H.264 packets
3. Packets sent via `monitor.sendMessageAndData("video", ...)`
4. Transmitted over mesh to remote peer

**Incoming (Decoding):**
1. Receive video data via monitor message handler
2. Feed data to MediaCodec decoder
3. Decoder renders to remote video surface
4. Display in full-screen SurfaceView

### Monitor Protocol Extensions

New video-related monitor messages:

```
// Enable video on call
video enable <session_id>

// Disable video on call
video disable <session_id>

// Video data packet
video <session_id> <data_length>
[raw H.264 data follows]
```

## Usage

### Starting a Video Call

**Option 1: From Call Director**
```java
Intent intent = new Intent(context, UnsecuredCall.class);
intent.putExtra(UnsecuredCall.EXTRA_SID, peerId);
intent.putExtra(UnsecuredCall.EXTRA_VIDEO_ENABLED, true);
startActivity(intent);
```

**Option 2: During Active Call**
- Tap the video toggle button
- Camera permissions will be requested if needed
- Video will start streaming to remote peer

### User Controls

**Toggle Video:**
- Button located in bottom control bar
- Enables/disables video streaming
- Audio continues uninterrupted

**Switch Camera:**
- Button appears when video is active
- Toggles between front and back camera
- Seamless switching without interruption

**End Call:**
- Standard end call button
- Automatically stops video and releases camera

## Permissions Required

Video calling requires the following Android permissions:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

Optional features:
```xml
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

## Implementation Details

### Camera Configuration
```java
Camera.Parameters params = camera.getParameters();
params.setPreviewSize(640, 480);
params.setPreviewFpsRange(15000, 15000);
camera.setParameters(params);
```

### Encoder Configuration
```java
MediaFormat format = MediaFormat.createVideoFormat("video/avc", 640, 480);
format.setInteger(MediaFormat.KEY_BIT_RATE, 500000);
format.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
format.setInteger(MediaFormat.KEY_COLOR_FORMAT, 
    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
```

### Threading Model
- **Encoding thread**: Continuously reads encoded frames from MediaCodec
- **Decoding thread**: Continuously decodes incoming video data
- **Camera callback**: Runs on camera preview callback thread
- **UI updates**: Posted to main UI thread via runOnUiThread()

## Bandwidth Considerations

Video calling significantly increases bandwidth usage:

| Mode | Bandwidth | Notes |
|------|-----------|-------|
| Audio only | ~10-20 kbps | Opus codec, variable bitrate |
| Video + Audio | ~510-530 kbps | H.264 video + audio |

**Recommendations:**
- Use video calling only with good mesh connectivity
- Consider reducing video quality in congested networks
- Monitor peer list - minimum 2-3 bars signal strength
- Video may not work well through multiple hops

## Limitations

### Current Limitations
1. **Single call at a time** - Inherited from voice call limitation
2. **No recording** - Video is not recorded or saved
3. **Camera API 1** - Uses legacy Camera API (not Camera2)
4. **Basic error handling** - Limited recovery from stream errors
5. **No resolution adaptation** - Fixed 640x480 resolution
6. **H.264 only** - No codec negotiation or fallback

### Device Requirements
- Android 4.3+ (API 18) for MediaCodec
- Device with front or rear camera
- Sufficient CPU for H.264 encoding/decoding
- OpenGL ES support for video rendering

## Future Enhancements

Potential improvements for video calling:

1. **Adaptive bitrate**: Adjust quality based on network conditions
2. **Multiple codecs**: VP8/VP9 support, codec negotiation
3. **Camera2 API**: Modern camera API for better control
4. **Error recovery**: Reconnect video on stream errors
5. **Statistics overlay**: Show FPS, bitrate, packet loss
6. **Screen sharing**: Share device screen instead of camera
7. **Group video**: Multi-party video conferences
8. **Recording**: Save video calls to Rhizome

## Debugging

### Enable Video Logs
```java
// In VideoCallManager.java
private static final String TAG = "VideoCallManager";
Log.d(TAG, "Your debug message");
```

### Common Issues

**Video doesn't start:**
- Check camera permission granted
- Verify device has camera hardware
- Check LogCat for MediaCodec errors

**Black screen:**
- Verify SurfaceHolder is initialized
- Check camera preview is started
- Ensure decoder has valid surface

**Choppy video:**
- Check network signal strength
- Verify CPU isn't overloaded
- Consider reducing frame rate/bitrate

**Audio works but no video:**
- Video enablement may have failed silently
- Check remote peer has video enabled
- Verify monitor protocol extensions working

## Testing

### Manual Testing
1. Start app on two devices on same mesh
2. Initiate voice call from device A to device B
3. On device A, tap video toggle button
4. Verify local preview appears on device A
5. Verify remote video appears on device B
6. Test camera switching
7. Test toggling video off/on
8. Test ending call

### Automated Testing
```java
// Unit tests for VideoCallManager
@Test
public void testVideoInitialization() {
    VideoCallManager vm = new VideoCallManager();
    assertNotNull(vm);
    assertFalse(vm.isVideoEnabled());
}
```

## Credits

Video calling feature developed as an extension to SATNET's existing VoIP infrastructure. Built on:
- Android MediaCodec API
- Camera API
- upstream Serval DNA monitor protocol
- H.264/AVC video compression

---

**Last Updated:** November 2025  
**Feature Status:** Experimental  
**Minimum Android Version:** 4.3 (API 18)

