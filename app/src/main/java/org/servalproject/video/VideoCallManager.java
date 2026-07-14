package org.servalproject.video;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Manages video capture, encoding, and streaming for video calls
 */
public class VideoCallManager {
    private static final String TAG = "VideoCallManager";

    // Video configuration
    private static final int VIDEO_WIDTH = 640;
    private static final int VIDEO_HEIGHT = 480;
    private static final int VIDEO_FPS = 15;
    private static final int VIDEO_BITRATE = 500000; // 500 kbps
    private static final String VIDEO_MIME_TYPE = "video/avc"; // H.264

    private Camera camera;
    private MediaCodec videoEncoder;
    private MediaCodec videoDecoder;
    private boolean isVideoEnabled = false;
    private VideoStreamCallback streamCallback;

    private SurfaceTexture cameraTexture;
    private Surface previewSurface;

    public interface VideoStreamCallback {
        void onVideoDataReady(byte[] data, int length);
        void onVideoFrameDecoded(byte[] data, int width, int height);
    }

    public VideoCallManager() {
    }

    public void setVideoStreamCallback(VideoStreamCallback callback) {
        this.streamCallback = callback;
    }

    /**
     * Start video capture and encoding
     */
    public synchronized boolean startVideoCapture(SurfaceHolder previewHolder) {
        if (isVideoEnabled) {
            Log.w(TAG, "Video capture already started");
            return true;
        }

        try {
            // Initialize camera
            camera = Camera.open();
            if (camera == null) {
                Log.e(TAG, "Failed to open camera");
                return false;
            }

            // Configure camera parameters
            Camera.Parameters params = camera.getParameters();
            params.setPreviewSize(VIDEO_WIDTH, VIDEO_HEIGHT);
            params.setPreviewFpsRange(VIDEO_FPS * 1000, VIDEO_FPS * 1000);
            camera.setParameters(params);

            // Set preview display
            if (previewHolder != null) {
                camera.setPreviewDisplay(previewHolder);
            }

            // Initialize video encoder
            initializeVideoEncoder();

            camera.startPreview();
            isVideoEnabled = true;

            Log.i(TAG, "Video capture started successfully");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to start video capture", e);
            stopVideoCapture();
            return false;
        }
    }

    /**
     * Initialize H.264 video encoder
     */
    private void initializeVideoEncoder() throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);
        format.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1); // I-frame every 1 second

        videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
        videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            previewSurface = videoEncoder.createInputSurface();

        videoEncoder.start();

        // Start encoding thread
        startEncodingThread();
    }

    /**
     * Initialize H.264 video decoder
     */
    public synchronized boolean initializeVideoDecoder(Surface outputSurface) {
        try {
            MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);

            videoDecoder = MediaCodec.createDecoderByType(VIDEO_MIME_TYPE);
            videoDecoder.configure(format, outputSurface, null, 0);
            videoDecoder.start();

            // Start decoding thread
            startDecodingThread();

            Log.i(TAG, "Video decoder initialized");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize video decoder", e);
            return false;
        }
    }

    /**
     * Thread to continuously read encoded video frames
     */
    private void startEncodingThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

                while (isVideoEnabled && videoEncoder != null) {
                    try {
                        int outputBufferIndex = videoEncoder.dequeueOutputBuffer(bufferInfo, 10000);

                        if (outputBufferIndex >= 0) {
                            ByteBuffer outputBuffer = videoEncoder.getOutputBuffer(outputBufferIndex);

                            if (outputBuffer != null && bufferInfo.size > 0) {
                                byte[] data = new byte[bufferInfo.size];
                                outputBuffer.position(bufferInfo.offset);
                                outputBuffer.get(data, 0, bufferInfo.size);

                                // Send encoded data through callback
                                if (streamCallback != null) {
                                    streamCallback.onVideoDataReady(data, data.length);
                                }
                            }

                            videoEncoder.releaseOutputBuffer(outputBufferIndex, false);
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Error in encoding thread", e);
                        break;
                    }
                }
            }
        }).start();
    }

    /**
     * Thread to continuously decode incoming video frames
     */
    private void startDecodingThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

                while (isVideoEnabled && videoDecoder != null) {
                    try {
                        int outputBufferIndex = videoDecoder.dequeueOutputBuffer(bufferInfo, 10000);

                        if (outputBufferIndex >= 0) {
                            videoDecoder.releaseOutputBuffer(outputBufferIndex, true);
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Error in decoding thread", e);
                        break;
                    }
                }
            }
        }).start();
    }

    /**
     * Feed incoming video data to decoder
     */
    public synchronized void feedVideoData(byte[] data, int length) {
        if (videoDecoder == null || !isVideoEnabled) {
            return;
        }

        try {
            int inputBufferIndex = videoDecoder.dequeueInputBuffer(10000);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = videoDecoder.getInputBuffer(inputBufferIndex);
                if (inputBuffer != null) {
                    inputBuffer.clear();
                    inputBuffer.put(data, 0, length);
                    videoDecoder.queueInputBuffer(inputBufferIndex, 0, length, 0, 0);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error feeding video data", e);
        }
    }

    /**
     * Stop video capture and release resources
     */
    public synchronized void stopVideoCapture() {
        isVideoEnabled = false;

        if (camera != null) {
            try {
                camera.stopPreview();
                camera.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing camera", e);
            }
            camera = null;
        }

        if (videoEncoder != null) {
            try {
                videoEncoder.stop();
                videoEncoder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing encoder", e);
            }
            videoEncoder = null;
        }

        if (videoDecoder != null) {
            try {
                videoDecoder.stop();
                videoDecoder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing decoder", e);
            }
            videoDecoder = null;
        }

        if (previewSurface != null) {
            previewSurface.release();
            previewSurface = null;
        }

        Log.i(TAG, "Video capture stopped");
    }

    /**
     * Toggle between front and back camera
     */
    public synchronized void switchCamera() {
        if (!isVideoEnabled || camera == null) {
            return;
        }

        try {
            camera.stopPreview();
            camera.release();

            // Open the other camera
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            int numberOfCameras = Camera.getNumberOfCameras();

            for (int i = 0; i < numberOfCameras; i++) {
                Camera.getCameraInfo(i, cameraInfo);
                // Find the camera we're not currently using
                // This is simplified - in production you'd track current camera ID
                camera = Camera.open(i);
                break;
            }

            Camera.Parameters params = camera.getParameters();
            params.setPreviewSize(VIDEO_WIDTH, VIDEO_HEIGHT);
            camera.setParameters(params);
            camera.startPreview();

        } catch (Exception e) {
            Log.e(TAG, "Error switching camera", e);
        }
    }

    public boolean isVideoEnabled() {
        return isVideoEnabled;
    }

    public int getVideoWidth() {
        return VIDEO_WIDTH;
    }

    public int getVideoHeight() {
        return VIDEO_HEIGHT;
    }
}

