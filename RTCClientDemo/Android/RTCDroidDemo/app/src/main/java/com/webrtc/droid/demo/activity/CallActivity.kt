package com.webrtc.droid.demo.activity

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.webrtc.droid.demo.core.service.AudioService
import com.webrtc.droid.demo.core.RTCConnection
import com.webrtc.droid.demo.core.service.VideoService
import com.webrtc.droid.demo.databinding.ActivityCallBinding
import com.webrtc.droid.demo.entity.CallInfoEntity
import com.webrtc.droid.demo.entity.CandidateInfoEntity
import com.webrtc.droid.demo.entity.MESSAGE_TYPE_ANSWER
import com.webrtc.droid.demo.entity.MESSAGE_TYPE_CANDIDATE
import com.webrtc.droid.demo.entity.MESSAGE_TYPE_HANGUP
import com.webrtc.droid.demo.entity.MESSAGE_TYPE_OFFER
import com.webrtc.droid.demo.safeLet
import com.webrtc.droid.demo.signal.RTCSignalClient.Companion.instance
import com.webrtc.droid.demo.toJson
import org.webrtc.IceCandidate
import org.webrtc.Logging
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import java.util.UUID

class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding
    private lateinit var mRTCConnection: RTCConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initUI()
        setContentView(binding.root)

        mRTCConnection = RTCConnection(
            this,
            ::onConstructSdpSuccess,
            ::onConstructIceCandidateSuccess,
            AudioService(),
            VideoService(binding.LocalSurfaceView, binding.RemoteSurfaceView)
        )
        initSignalClient()
        Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE)
    }

    private fun initSignalClient() {
        instance.broadcastReceived = ::onBroadcastReceived
        safeLet(
            intent.getStringExtra(MainActivity.SERVER_ADDRESS),
            intent.getStringExtra(MainActivity.ROOM_NAME)
        ) { serverAddress, roomName ->
            instance.joinRoom(serverAddress, UUID.randomUUID().toString(), roomName)
        }
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

    override fun onResume() {
        super.onResume()
        mRTCConnection.startCapture()
    }

    override fun onPause() {
        super.onPause()
        mRTCConnection.stopCapture()
    }

    override fun onDestroy() {
        super.onDestroy()
        doEndCall()
        mRTCConnection.releaseService()
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

    private fun onConstructSdpSuccess(msgType: Int, sessionDescription: SessionDescription) {
        Log.i(TAG, "onConstructSuccess: $msgType")
        instance.sendMessage(
            CallInfoEntity(
                instance.userId,
                msgType,
                sessionDescription.description
            ).toJson()
        )
    }

    private fun onConstructIceCandidateSuccess(iceCandidate: IceCandidate) {
        instance.sendMessage(
            CallInfoEntity(
                instance.userId,
                MESSAGE_TYPE_CANDIDATE,
                candidateInfoEntity = CandidateInfoEntity(
                    iceCandidate.sdpMLineIndex,
                    iceCandidate.sdpMid,
                    iceCandidate.sdp
                )
            ).toJson()
        )
    }

    private fun doStartCall() {
        logcatOnUI("Start Call, Wait ...")
        mRTCConnection.doUserStartCall()
    }

    private fun doEndCall() {
        logcatOnUI("End Call, Wait ...")
        mRTCConnection.doEndCall()
        hangUp()
        instance.sendMessage(
            CallInfoEntity(
                instance.userId,
                MESSAGE_TYPE_HANGUP
            ).toJson()
        )
    }

    private fun hangUp() {
        logcatOnUI("hangUp Done.")
        updateCallState(true)
    }

    private fun onBroadcastReceived(entity: CallInfoEntity?) {
        Log.i(TAG, "onBroadcastReceived: ${entity?.toJson()}")
        when (entity?.msgType) {
            MESSAGE_TYPE_OFFER -> onRemoteOfferReceived(entity)
            MESSAGE_TYPE_ANSWER -> onRemoteAnswerReceived(entity)
            MESSAGE_TYPE_CANDIDATE -> onRemoteCandidateReceived(entity)
            MESSAGE_TYPE_HANGUP -> onRemoteHangup()
        }
    }

    private fun onRemoteOfferReceived(entity: CallInfoEntity) {
        logcatOnUI("Receive Remote Offer Call ...")
        mRTCConnection.doUserAnswerRemoteOfferCall(entity)
        Log.i(TAG, "Create answer ...")
        updateCallState(false)
    }

    private fun onRemoteAnswerReceived(entity: CallInfoEntity) {
        logcatOnUI("Receive Remote Answer ...")
        mRTCConnection.onRemoteAnswerReceived(entity)
        updateCallState(false)
    }

    private fun onRemoteCandidateReceived(entity: CallInfoEntity) {
        logcatOnUI("Receive Remote Candidate ...")
        mRTCConnection.onRemoteIceCandidateReceived(entity)
    }

    private fun onRemoteHangup() {
        logcatOnUI("Receive Remote Hanup Event ...")
        hangUp()
        mRTCConnection.doEndCall()
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
    }
}