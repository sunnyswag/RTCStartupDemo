package com.webrtc.droid.demo.observer

import android.util.Log
import com.webrtc.droid.demo.entity.CallInfoEntity
import com.webrtc.droid.demo.entity.CandidateInfoEntity
import com.webrtc.droid.demo.entity.MESSAGE_TYPE_CANDIDATE
import com.webrtc.droid.demo.signal.RTCSignalClient
import com.webrtc.droid.demo.toJson
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver

/**
 * @author: huiqing.huang
 * create on 2023/5/23 18:37
 * desc:
 */
open class SimplePeerObserver : PeerConnection.Observer {
    private val tag = "SimplePeerObserver"

    override fun onSignalingChange(signalingState: PeerConnection.SignalingState) {
        Log.i(tag, "onSignalingChange: $signalingState")
    }

    override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
        when (iceConnectionState) {
            PeerConnection.IceConnectionState.FAILED -> Log.i(tag, "IceConnectionState: FAILED")
            PeerConnection.IceConnectionState.DISCONNECTED -> Log.i(tag, "IceConnectionState: DISCONNECTED")
            PeerConnection.IceConnectionState.CONNECTED -> Log.i(tag, "IceConnectionState: CONNECTED")
            else -> Log.i(tag, "IceConnectionState: UNKNOWN")
        }
    }

    override fun onIceConnectionReceivingChange(b: Boolean) {
        Log.i(tag, "onIceConnectionChange: $b")
    }

    override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState) {
        Log.i(tag, "onIceGatheringChange: $iceGatheringState")
    }

    override fun onIceCandidate(iceCandidate: IceCandidate) {
        Log.i(tag, "onIceCandidate: $iceCandidate")
        RTCSignalClient.instance!!.sendMessage(
            CallInfoEntity(
            RTCSignalClient.instance!!.userId,
            MESSAGE_TYPE_CANDIDATE,
            candidateInfoEntity = CandidateInfoEntity(
                iceCandidate.sdpMLineIndex,
                iceCandidate.sdpMid,
                iceCandidate.sdp
            )
        ).toJson())
    }

    override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
        for (i in iceCandidates.indices) {
            Log.i(tag, "onIceCandidatesRemoved: " + iceCandidates[i])
        }
    }

    override fun onAddStream(mediaStream: MediaStream) {
        Log.i(tag, "onAddStream: " + mediaStream.videoTracks.size)

        // Check if the stream contains an audio track
        if (mediaStream.audioTracks.isNotEmpty()) {
            val track = mediaStream.audioTracks[0]

            // Enable the audio track of the incoming stream
            track.setEnabled(true)
        }
    }

    override fun onRemoveStream(mediaStream: MediaStream) {
        Log.i(tag, "onRemoveStream")
    }

    override fun onDataChannel(dataChannel: DataChannel) {
        Log.i(tag, "onDataChannel")
    }

    override fun onRenegotiationNeeded() {
        Log.i(tag, "onRenegotiationNeeded")
    }

    override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<MediaStream>) {}
}