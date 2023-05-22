package com.webrtc.droid.demo.activity;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.webrtc.droid.demo.R;
import com.webrtc.droid.demo.signal.RTCSignalClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.util.ArrayList;
import java.util.UUID;

public class CallActivity extends AppCompatActivity {

    private TextView mLogcatView;
    private Button mStartCallBtn;
    private Button mEndCallBtn;

    private static final String TAG = "CallActivity";

    public static final String AUDIO_TRACK_ID = "ARDAMSa0";

    private EglBase mRootEglBase;

    private PeerConnection mPeerConnection;
    private PeerConnectionFactory mPeerConnectionFactory;

    private AudioTrack mAudioTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        mLogcatView = findViewById(R.id.LogcatView);
        mStartCallBtn = findViewById(R.id.StartCallButton);
        mEndCallBtn = findViewById(R.id.EndCallButton);

        RTCSignalClient.getInstance().setSignalEventListener(mOnSignalEventListener);

        String serverAddr = getIntent().getStringExtra("ServerAddr");
        String roomName = getIntent().getStringExtra("RoomName");
        RTCSignalClient.getInstance().joinRoom(serverAddr, UUID.randomUUID().toString(), roomName);

        mRootEglBase = EglBase.create();

        mPeerConnectionFactory = createPeerConnectionFactory(this);

        Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE);

        AudioSource audioSource = mPeerConnectionFactory.createAudioSource(new MediaConstraints());
        mAudioTrack = mPeerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        mAudioTrack.setEnabled(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doEndCall();
        PeerConnectionFactory.stopInternalTracingCapture();
        PeerConnectionFactory.shutdownInternalTracer();
        RTCSignalClient.getInstance().leaveRoom();
    }

    public static class ProxyVideoSink implements VideoSink {
        private VideoSink mTarget;
        @Override
        synchronized public void onFrame(VideoFrame frame) {
            if (mTarget == null) {
                Log.d(TAG, "Dropping frame in proxy because target is null.");
                return;
            }
            mTarget.onFrame(frame);
        }
    }

    public static class SimpleSdpObserver implements SdpObserver {
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            Log.i(TAG, "SdpObserver: onCreateSuccess !");
        }

        @Override
        public void onSetSuccess() {
            Log.i(TAG, "SdpObserver: onSetSuccess");
        }

        @Override
        public void onCreateFailure(String msg) {
            Log.e(TAG, "SdpObserver onCreateFailure: " + msg);
        }

        @Override
        public void onSetFailure(String msg) {
            Log.e(TAG, "SdpObserver onSetFailure: " + msg);
        }
    }

    public void onClickStartCallButton(View v) {
        doStartCall();
    }

    public void onClickEndCallButton(View v) {
        doEndCall();
    }

    private void updateCallState(boolean idle) {
        runOnUiThread(() -> {
            if (idle) {
                mStartCallBtn.setVisibility(View.VISIBLE);
                mEndCallBtn.setVisibility(View.GONE);
            } else {
                mStartCallBtn.setVisibility(View.GONE);
                mEndCallBtn.setVisibility(View.VISIBLE);
            }
        });
    }

    public void doStartCall() {
        logcatOnUI("Start Call, Wait ...");
        if (mPeerConnection == null) {
            mPeerConnection = createPeerConnection();
        }
        MediaConstraints mediaConstraints = new MediaConstraints();
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        mediaConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
        mPeerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.i(TAG, "Create local offer success: \n" + sessionDescription.description);
                mPeerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                JSONObject message = new JSONObject();
                try {
                    message.put("userId", RTCSignalClient.getInstance().getUserId());
                    message.put("msgType", RTCSignalClient.MESSAGE_TYPE_OFFER);
                    message.put("sdp", sessionDescription.description);
                    RTCSignalClient.getInstance().sendMessage(message);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, mediaConstraints);
    }

    public void doEndCall() {
        logcatOnUI("End Call, Wait ...");
        hanup();
        JSONObject message = new JSONObject();
        try {
            message.put("userId", RTCSignalClient.getInstance().getUserId());
            message.put("msgType", RTCSignalClient.MESSAGE_TYPE_HANGUP);
            RTCSignalClient.getInstance().sendMessage(message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void doAnswerCall() {
        logcatOnUI("Answer Call, Wait ...");
        if (mPeerConnection == null) {
            mPeerConnection = createPeerConnection();
        }
        MediaConstraints sdpMediaConstraints = new MediaConstraints();
        Log.i(TAG, "Create answer ...");
        mPeerConnection.createAnswer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.i(TAG, "Create answer success !");
                mPeerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                JSONObject message = new JSONObject();
                try {
                    message.put("userId", RTCSignalClient.getInstance().getUserId());
                    message.put("msgType", RTCSignalClient.MESSAGE_TYPE_ANSWER);
                    message.put("sdp", sessionDescription.description);
                    RTCSignalClient.getInstance().sendMessage(message);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, sdpMediaConstraints);
        updateCallState(false);
    }

    private void hanup() {
        logcatOnUI("Hanup Call, Wait ...");
        if (mPeerConnection == null) {
            return;
        }
        mPeerConnection.close();
        mPeerConnection = null;
        logcatOnUI("Hanup Done.");
        updateCallState(true);
    }

    public PeerConnection createPeerConnection() {
        Log.i(TAG, "Create PeerConnection ...");
        PeerConnection.RTCConfiguration configuration = new PeerConnection.RTCConfiguration(new ArrayList<>());
        PeerConnection connection = mPeerConnectionFactory.createPeerConnection(configuration, mPeerConnectionObserver);
        if (connection == null) {
            Log.e(TAG, "Failed to createPeerConnection !");
            return null;
        }
        connection.addTrack(mAudioTrack);
        return connection;
    }

    public PeerConnectionFactory createPeerConnectionFactory(Context context) {
        final VideoEncoderFactory encoderFactory;
        final VideoDecoderFactory decoderFactory;

        encoderFactory = new DefaultVideoEncoderFactory(
                mRootEglBase.getEglBaseContext(), false /* enableIntelVp8Encoder */, true);
        decoderFactory = new DefaultVideoDecoderFactory(mRootEglBase.getEglBaseContext());

        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions());

        PeerConnectionFactory.Builder builder = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory);
        builder.setOptions(null);

        return builder.createPeerConnectionFactory();
    }

    private PeerConnection.Observer mPeerConnectionObserver = new PeerConnection.Observer() {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.i(TAG, "onSignalingChange: " + signalingState);
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.i(TAG, "onIceConnectionChange: " + iceConnectionState);

            switch (iceConnectionState) {
                case FAILED:
                    logcatOnUI("IceConnectionState: FAILED");
                    // Handle failure: reconnect or alert the user
                    break;
                case DISCONNECTED:
                    logcatOnUI("IceConnectionState: DISCONNECTED");
                    // Handle disconnection: try to reconnect or alert the user
                    break;
                case CONNECTED:
                    logcatOnUI("IceConnectionState: CONNECTED");
                    // Handle successful connection
                    break;
            }
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {
            Log.i(TAG, "onIceConnectionChange: " + b);
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            Log.i(TAG, "onIceGatheringChange: " + iceGatheringState);
        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            Log.i(TAG, "onIceCandidate: " + iceCandidate);

            try {
                JSONObject message = new JSONObject();
                message.put("userId", RTCSignalClient.getInstance().getUserId());
                message.put("msgType", RTCSignalClient.MESSAGE_TYPE_CANDIDATE);
                message.put("label", iceCandidate.sdpMLineIndex);
                message.put("id", iceCandidate.sdpMid);
                message.put("candidate", iceCandidate.sdp);
                RTCSignalClient.getInstance().sendMessage(message);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            for (int i = 0; i < iceCandidates.length; i++) {
                Log.i(TAG, "onIceCandidatesRemoved: " + iceCandidates[i]);
            }
            mPeerConnection.removeIceCandidates(iceCandidates);
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.i(TAG, "onAddStream: " + mediaStream.videoTracks.size());

            // Check if the stream contains an audio track
            if (!mediaStream.audioTracks.isEmpty()) {
                AudioTrack track = mediaStream.audioTracks.get(0);

                // Enable the audio track of the incoming stream
                track.setEnabled(true);
            }
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.i(TAG, "onRemoveStream");
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            Log.i(TAG, "onDataChannel");
        }

        @Override
        public void onRenegotiationNeeded() {
            Log.i(TAG, "onRenegotiationNeeded");
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {}
    };

    private RTCSignalClient.OnSignalEventListener mOnSignalEventListener = new RTCSignalClient.OnSignalEventListener() {
        @Override
        public void onConnected() {
            logcatOnUI("Signal Server Connected !");
        }

        @Override
        public void onConnecting() {
            logcatOnUI("Signal Server Connecting !");
        }

        @Override
        public void onDisconnected() {
            logcatOnUI("Signal Server Connecting !");
        }

        @Override
        public void onRemoteUserJoined(String userId) {
            logcatOnUI("Remote User Joined: " + userId);
        }

        @Override
        public void onRemoteUserLeft(String userId) {
            logcatOnUI("Remote User Leaved: " + userId);
        }

        @Override
        public void onBroadcastReceived(JSONObject message) {
            Log.i(TAG, "onBroadcastReceived: " + message);
            try {
                String userId = message.getString("userId");
                int type = message.getInt("msgType");
                switch (type) {
                    case RTCSignalClient.MESSAGE_TYPE_OFFER:
                        onRemoteOfferReceived(userId, message);
                        break;
                    case RTCSignalClient.MESSAGE_TYPE_ANSWER:
                        onRemoteAnswerReceived(userId, message);
                        break;
                    case RTCSignalClient.MESSAGE_TYPE_CANDIDATE:
                        onRemoteCandidateReceived(userId, message);
                        break;
                    case RTCSignalClient.MESSAGE_TYPE_HANGUP:
                        onRemoteHangup(userId);
                        break;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void onRemoteOfferReceived(String userId, JSONObject message) {
            logcatOnUI("Receive Remote Call ...");
            if (mPeerConnection == null) {
                mPeerConnection = createPeerConnection();
            }
            try {
                String description = message.getString("sdp");
                mPeerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(SessionDescription.Type.OFFER, description));
                doAnswerCall();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void onRemoteAnswerReceived(String userId, JSONObject message) {
            logcatOnUI("Receive Remote Answer ...");
            try {
                String description = message.getString("sdp");
                mPeerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(SessionDescription.Type.ANSWER, description));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            updateCallState(false);
        }

        private void onRemoteCandidateReceived(String userId, JSONObject message) {
            logcatOnUI("Receive Remote Candidate ...");
            try {
                IceCandidate remoteIceCandidate = new IceCandidate(message.getString("id"), message.getInt("label"), message.getString("candidate"));
                mPeerConnection.addIceCandidate(remoteIceCandidate);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void onRemoteHangup(String userId) {
            logcatOnUI("Receive Remote Hanup Event ...");
            hanup();
        }
    };

    private void logcatOnUI(String msg) {
        Log.i(TAG, msg);
        runOnUiThread(() -> {
            String output = mLogcatView.getText() + "\n" + msg;
            mLogcatView.setText(output);
        });
    }
}
