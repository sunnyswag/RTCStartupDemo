package com.webrtc.droid.demo.activity

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.webrtc.droid.demo.R
import com.webrtc.droid.demo.signal.RTCSignalClient
import com.webrtc.droid.demo.signal.RTCSignalClient.Companion.instance
import com.webrtc.droid.demo.signal.RTCSignalClient.OnSignalEventListener
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.Logging
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceConnectionState
import org.webrtc.PeerConnection.IceGatheringState
import org.webrtc.PeerConnection.RTCConfiguration
import org.webrtc.PeerConnection.SignalingState
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoDecoderFactory
import org.webrtc.VideoEncoderFactory
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import java.util.UUID

class CallActivity : AppCompatActivity() {
    private var mLogcatView: TextView? = null
    private var mStartCallBtn: Button? = null
    private var mEndCallBtn: Button? = null
    private var mRootEglBase: EglBase? = null
    private var mPeerConnection: PeerConnection? = null
    private var mPeerConnectionFactory: PeerConnectionFactory? = null
    private var mAudioTrack: AudioTrack? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)
        mLogcatView = findViewById(R.id.LogcatView)
        mStartCallBtn = findViewById(R.id.StartCallButton)
        mEndCallBtn = findViewById(R.id.EndCallButton)
        instance!!.setSignalEventListener(mOnSignalEventListener)
        val serverAddr = intent.getStringExtra("ServerAddr")
        val roomName = intent.getStringExtra("RoomName")
        instance!!.joinRoom(serverAddr, UUID.randomUUID().toString(), roomName)
        mRootEglBase = EglBase.create()
        mPeerConnectionFactory = createPeerConnectionFactory(this)
        Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE)
        val audioSource = mPeerConnectionFactory!!.createAudioSource(MediaConstraints())
        mAudioTrack = mPeerConnectionFactory!!.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        mAudioTrack?.setEnabled(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        doEndCall()
        PeerConnectionFactory.stopInternalTracingCapture()
        PeerConnectionFactory.shutdownInternalTracer()
        instance!!.leaveRoom()
    }

    class ProxyVideoSink : VideoSink {
        private val mTarget: VideoSink? = null
        @Synchronized
        override fun onFrame(frame: VideoFrame) {
            if (mTarget == null) {
                Log.d(TAG, "Dropping frame in proxy because target is null.")
                return
            }
            mTarget.onFrame(frame)
        }
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sessionDescription: SessionDescription) {
            Log.i(TAG, "SdpObserver: onCreateSuccess !")
        }

        override fun onSetSuccess() {
            Log.i(TAG, "SdpObserver: onSetSuccess")
        }

        override fun onCreateFailure(msg: String) {
            Log.e(TAG, "SdpObserver onCreateFailure: $msg")
        }

        override fun onSetFailure(msg: String) {
            Log.e(TAG, "SdpObserver onSetFailure: $msg")
        }
    }

    fun onClickStartCallButton(v: View?) {
        doStartCall()
    }

    fun onClickEndCallButton(v: View?) {
        doEndCall()
    }

    private fun updateCallState(idle: Boolean) {
        runOnUiThread {
            if (idle) {
                mStartCallBtn!!.visibility = View.VISIBLE
                mEndCallBtn!!.visibility = View.GONE
            } else {
                mStartCallBtn!!.visibility = View.GONE
                mEndCallBtn!!.visibility = View.VISIBLE
            }
        }
    }

    fun doStartCall() {
        logcatOnUI("Start Call, Wait ...")
        if (mPeerConnection == null) {
            mPeerConnection = createPeerConnection()
        }
        val mediaConstraints = MediaConstraints()
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        mediaConstraints.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        mPeerConnection!!.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.i(
                    TAG, """
     Create local offer success: 
     ${sessionDescription.description}
     """.trimIndent()
                )
                mPeerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject()
                try {
                    message.put("userId", instance!!.userId)
                    message.put("msgType", RTCSignalClient.MESSAGE_TYPE_OFFER)
                    message.put("sdp", sessionDescription.description)
                    instance!!.sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }, mediaConstraints)
    }

    fun doEndCall() {
        logcatOnUI("End Call, Wait ...")
        hanup()
        val message = JSONObject()
        try {
            message.put("userId", instance!!.userId)
            message.put("msgType", RTCSignalClient.MESSAGE_TYPE_HANGUP)
            instance!!.sendMessage(message)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun doAnswerCall() {
        logcatOnUI("Answer Call, Wait ...")
        if (mPeerConnection == null) {
            mPeerConnection = createPeerConnection()
        }
        val sdpMediaConstraints = MediaConstraints()
        Log.i(TAG, "Create answer ...")
        mPeerConnection!!.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.i(TAG, "Create answer success !")
                mPeerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject()
                try {
                    message.put("userId", instance!!.userId)
                    message.put("msgType", RTCSignalClient.MESSAGE_TYPE_ANSWER)
                    message.put("sdp", sessionDescription.description)
                    instance!!.sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }, sdpMediaConstraints)
        updateCallState(false)
    }

    private fun hanup() {
        logcatOnUI("Hanup Call, Wait ...")
        if (mPeerConnection == null) {
            return
        }
        mPeerConnection!!.close()
        mPeerConnection = null
        logcatOnUI("Hanup Done.")
        updateCallState(true)
    }

    fun createPeerConnection(): PeerConnection? {
        Log.i(TAG, "Create PeerConnection ...")
        val configuration = RTCConfiguration(ArrayList())
        val connection =
            mPeerConnectionFactory!!.createPeerConnection(configuration, mPeerConnectionObserver)
        if (connection == null) {
            Log.e(TAG, "Failed to createPeerConnection !")
            return null
        }
        connection.addTrack(mAudioTrack)
        return connection
    }

    fun createPeerConnectionFactory(context: Context?): PeerConnectionFactory {
        val encoderFactory: VideoEncoderFactory
        val decoderFactory: VideoDecoderFactory
        encoderFactory = DefaultVideoEncoderFactory(
            mRootEglBase!!.eglBaseContext, false /* enableIntelVp8Encoder */, true
        )
        decoderFactory = DefaultVideoDecoderFactory(mRootEglBase!!.eglBaseContext)
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
        val builder = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
        builder.setOptions(null)
        return builder.createPeerConnectionFactory()
    }

    private val mPeerConnectionObserver: PeerConnection.Observer =
        object : PeerConnection.Observer {
            override fun onSignalingChange(signalingState: SignalingState) {
                Log.i(TAG, "onSignalingChange: $signalingState")
            }

            override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
                Log.i(TAG, "onIceConnectionChange: $iceConnectionState")
                when (iceConnectionState) {
                    IceConnectionState.FAILED -> logcatOnUI("IceConnectionState: FAILED")
                    IceConnectionState.DISCONNECTED -> logcatOnUI("IceConnectionState: DISCONNECTED")
                    IceConnectionState.CONNECTED -> logcatOnUI("IceConnectionState: CONNECTED")
                }
            }

            override fun onIceConnectionReceivingChange(b: Boolean) {
                Log.i(TAG, "onIceConnectionChange: $b")
            }

            override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
                Log.i(TAG, "onIceGatheringChange: $iceGatheringState")
            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                Log.i(TAG, "onIceCandidate: $iceCandidate")
                try {
                    val message = JSONObject()
                    message.put("userId", instance!!.userId)
                    message.put("msgType", RTCSignalClient.MESSAGE_TYPE_CANDIDATE)
                    message.put("label", iceCandidate.sdpMLineIndex)
                    message.put("id", iceCandidate.sdpMid)
                    message.put("candidate", iceCandidate.sdp)
                    instance!!.sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }

            override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
                for (i in iceCandidates.indices) {
                    Log.i(TAG, "onIceCandidatesRemoved: " + iceCandidates[i])
                }
                mPeerConnection!!.removeIceCandidates(iceCandidates)
            }

            override fun onAddStream(mediaStream: MediaStream) {
                Log.i(TAG, "onAddStream: " + mediaStream.videoTracks.size)

                // Check if the stream contains an audio track
                if (!mediaStream.audioTracks.isEmpty()) {
                    val track = mediaStream.audioTracks[0]

                    // Enable the audio track of the incoming stream
                    track.setEnabled(true)
                }
            }

            override fun onRemoveStream(mediaStream: MediaStream) {
                Log.i(TAG, "onRemoveStream")
            }

            override fun onDataChannel(dataChannel: DataChannel) {
                Log.i(TAG, "onDataChannel")
            }

            override fun onRenegotiationNeeded() {
                Log.i(TAG, "onRenegotiationNeeded")
            }

            override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<MediaStream>) {}
        }
    private val mOnSignalEventListener: OnSignalEventListener = object : OnSignalEventListener {
        override fun onConnected() {
            logcatOnUI("Signal Server Connected !")
        }

        override fun onConnecting() {
            logcatOnUI("Signal Server Connecting !")
        }

        override fun onDisconnected() {
            logcatOnUI("Signal Server Connecting !")
        }

        override fun onRemoteUserJoined(userId: String?) {
            logcatOnUI("Remote User Joined: $userId")
        }

        override fun onRemoteUserLeft(userId: String?) {
            logcatOnUI("Remote User Leaved: $userId")
        }

        override fun onBroadcastReceived(message: JSONObject?) {
            Log.i(TAG, "onBroadcastReceived: $message")
            try {
                val userId = message!!.getString("userId")
                val type = message.getInt("msgType")
                when (type) {
                    RTCSignalClient.MESSAGE_TYPE_OFFER -> onRemoteOfferReceived(userId, message)
                    RTCSignalClient.MESSAGE_TYPE_ANSWER -> onRemoteAnswerReceived(userId, message)
                    RTCSignalClient.MESSAGE_TYPE_CANDIDATE -> onRemoteCandidateReceived(
                        userId,
                        message
                    )

                    RTCSignalClient.MESSAGE_TYPE_HANGUP -> onRemoteHangup(userId)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        private fun onRemoteOfferReceived(userId: String, message: JSONObject?) {
            logcatOnUI("Receive Remote Call ...")
            if (mPeerConnection == null) {
                mPeerConnection = createPeerConnection()
            }
            try {
                val description = message!!.getString("sdp")
                mPeerConnection!!.setRemoteDescription(
                    SimpleSdpObserver(),
                    SessionDescription(SessionDescription.Type.OFFER, description)
                )
                doAnswerCall()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        private fun onRemoteAnswerReceived(userId: String, message: JSONObject?) {
            logcatOnUI("Receive Remote Answer ...")
            try {
                val description = message!!.getString("sdp")
                mPeerConnection!!.setRemoteDescription(
                    SimpleSdpObserver(),
                    SessionDescription(SessionDescription.Type.ANSWER, description)
                )
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            updateCallState(false)
        }

        private fun onRemoteCandidateReceived(userId: String, message: JSONObject?) {
            logcatOnUI("Receive Remote Candidate ...")
            try {
                val remoteIceCandidate = IceCandidate(
                    message!!.getString("id"),
                    message.getInt("label"),
                    message.getString("candidate")
                )
                mPeerConnection!!.addIceCandidate(remoteIceCandidate)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        private fun onRemoteHangup(userId: String) {
            logcatOnUI("Receive Remote Hanup Event ...")
            hanup()
        }
    }

    private fun logcatOnUI(msg: String) {
        Log.i(TAG, msg)
        runOnUiThread {
            val output = """
                ${mLogcatView!!.text}
                $msg
                """.trimIndent()
            mLogcatView!!.text = output
        }
    }

    companion object {
        private const val TAG = "CallActivity"
        const val AUDIO_TRACK_ID = "ARDAMSa0"
    }
}