package com.webrtc.droid.demo.activity

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.webrtc.droid.demo.databinding.ActivityCallBinding
import com.webrtc.droid.demo.entity.CallInfoEntity
import com.webrtc.droid.demo.entity.MESSAGE_TYPE_ANSWER
import com.webrtc.droid.demo.entity.MESSAGE_TYPE_CANDIDATE
import com.webrtc.droid.demo.entity.MESSAGE_TYPE_HANGUP
import com.webrtc.droid.demo.entity.MESSAGE_TYPE_OFFER
import com.webrtc.droid.demo.observer.SimplePeerObserver
import com.webrtc.droid.demo.observer.SimpleSdpObserver
import com.webrtc.droid.demo.signal.RTCSignalClient.Companion.instance
import com.webrtc.droid.demo.toJson
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.Logging
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.RTCConfiguration
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import org.webrtc.VideoDecoderFactory
import org.webrtc.VideoEncoderFactory
import java.util.UUID

class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding
    private lateinit var mRootEglBase: EglBase
    private var mPeerConnection: PeerConnection? = null
    private lateinit var mPeerConnectionFactory: PeerConnectionFactory
    private lateinit var mAudioTrack: AudioTrack

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initUI()
        setContentView(binding.root)
        initService()

        instance.broadcastReceived = ::onBroadcastReceived
        val serverAddress = intent.getStringExtra(MainActivity.SERVER_ADDRESS)
        val roomName = intent.getStringExtra(MainActivity.ROOM_NAME)
        instance.joinRoom(serverAddress, UUID.randomUUID().toString(), roomName)

        Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE)
    }

    private fun initService() {
        mRootEglBase = EglBase.create()
        mPeerConnectionFactory = createPeerConnectionFactory(this)

        val audioSource = mPeerConnectionFactory.createAudioSource(MediaConstraints())
        mAudioTrack = mPeerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        mAudioTrack.setEnabled(true)
    }

    private fun initUI() {
        binding = ActivityCallBinding.inflate(layoutInflater)
        binding.StartCallButton.setOnClickListener {
            doStartCall()
        }
        binding.EndCallButton.setOnClickListener {
            doEndCall()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        doEndCall()
        PeerConnectionFactory.stopInternalTracingCapture()
        PeerConnectionFactory.shutdownInternalTracer()
        instance.leaveRoom()
    }


    private fun updateCallState(idle: Boolean) {
        runOnUiThread {
            if (idle) {
                binding.StartCallButton.visibility = View.VISIBLE
                binding.EndCallButton.visibility = View.GONE
            } else {
                binding.StartCallButton.visibility = View.GONE
                binding.EndCallButton.visibility = View.VISIBLE
            }
        }
    }

    private fun doStartCall() {
        logcatOnUI("Start Call, Wait ...")
        initPeerConnection()
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        }
        mPeerConnection?.createOffer(getSimpleObserverByMsgType(MESSAGE_TYPE_OFFER), mediaConstraints)
    }

    private fun doEndCall() {
        logcatOnUI("End Call, Wait ...")
        hangUp()
        instance.sendMessage(
            CallInfoEntity(
                instance.userId,
                MESSAGE_TYPE_HANGUP
            ).toJson()
        )
    }

    private fun doAnswerCall() {
        logcatOnUI("Answer Call, Wait ...")
        initPeerConnection()
        Log.i(TAG, "Create answer ...")
        mPeerConnection?.createAnswer(getSimpleObserverByMsgType(MESSAGE_TYPE_ANSWER), MediaConstraints())
        updateCallState(false)
    }

    private fun getSimpleObserverByMsgType(msgType: Int): SimpleSdpObserver {
        return object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.i(TAG, "Create $msgType success, description: ${sessionDescription.description}")
                mPeerConnection?.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                instance.sendMessage(
                    CallInfoEntity(
                        instance.userId,
                        msgType,
                        sessionDescription.description
                    ).toJson()
                )
            }
        }
    }

    private fun hangUp() {
        logcatOnUI("hangUp Call, Wait ...")
        mPeerConnection?.close()
        mPeerConnection = null
        logcatOnUI("hangUp Done.")
        updateCallState(true)
    }

    private fun createPeerConnection(): PeerConnection? {
        Log.i(TAG, "Create PeerConnection ...")
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val configuration = RTCConfiguration(iceServers)
        val connection =
            mPeerConnectionFactory.createPeerConnection(configuration, mPeerConnectionObserver)
        return connection?.apply { addTrack(mAudioTrack) } ?: kotlin.run {
            Log.e(TAG, "Failed to createPeerConnection !")
            null
        }
    }

    private fun initPeerConnection() {
        if (mPeerConnection == null) {
            mPeerConnection = createPeerConnection()
        }
    }

    private fun createPeerConnectionFactory(context: Context?): PeerConnectionFactory {
        val encoderFactory: VideoEncoderFactory
        val decoderFactory: VideoDecoderFactory
        encoderFactory = DefaultVideoEncoderFactory(
            mRootEglBase.eglBaseContext, false /* enableIntelVp8Encoder */, true
        )
        decoderFactory = DefaultVideoDecoderFactory(mRootEglBase.eglBaseContext)
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

    private val mPeerConnectionObserver: PeerConnection.Observer = object : SimplePeerObserver() {
        override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
            super.onIceCandidatesRemoved(iceCandidates)
            mPeerConnection?.removeIceCandidates(iceCandidates)
        }
    }

    private fun onBroadcastReceived(entity: CallInfoEntity?) {
        Log.i(TAG, "onBroadcastReceived: ${entity?.toJson()}")
        when (entity?.msgType) {
            MESSAGE_TYPE_OFFER -> onRemoteOfferReceived(entity)
            MESSAGE_TYPE_ANSWER -> onRemoteAnswerReceived(entity)
            MESSAGE_TYPE_CANDIDATE -> onRemoteCandidateReceived(entity)
            MESSAGE_TYPE_HANGUP -> onRemoteHangup(entity)
        }
    }

    private fun onRemoteOfferReceived(entity: CallInfoEntity) {
        logcatOnUI("Receive Remote Call ...")
        initPeerConnection()
        mPeerConnection!!.setRemoteDescription(
            SimpleSdpObserver(),
            SessionDescription(SessionDescription.Type.OFFER, entity.sdp)
        )
        doAnswerCall()
    }

    private fun onRemoteAnswerReceived(entity: CallInfoEntity) {
        logcatOnUI("Receive Remote Answer ...")
        mPeerConnection!!.setRemoteDescription(
            SimpleSdpObserver(),
            SessionDescription(SessionDescription.Type.ANSWER, entity.sdp)
        )
        updateCallState(false)
    }

    private fun onRemoteCandidateReceived(entity: CallInfoEntity) {
        logcatOnUI("Receive Remote Candidate ...")
        val remoteIceCandidate = IceCandidate(
            entity.candidateInfoEntity?.id,
            entity.candidateInfoEntity?.label ?: 0,
            entity.candidateInfoEntity?.candidate
        )
        mPeerConnection?.addIceCandidate(remoteIceCandidate)
    }

    private fun onRemoteHangup(entity: CallInfoEntity?) {
        logcatOnUI("Receive Remote Hanup Event ...")
        hangUp()
    }

    private fun logcatOnUI(msg: String) {
        Log.i(TAG, msg)
        runOnUiThread {
            val output = """
                ${binding.LogcatView.text}
                $msg
                """.trimIndent()
            binding.LogcatView.text = output
        }
    }

    companion object {
        private const val TAG = "CallActivity"
        const val AUDIO_TRACK_ID = "ARDAMSa0"
    }
}