package com.webrtc.droid.demo.core

import android.content.Context
import android.util.Log
import com.webrtc.droid.demo.core.api.IRTCConnection
import com.webrtc.droid.demo.entity.CallInfoEntity
import com.webrtc.droid.demo.entity.MESSAGE_TYPE_ANSWER
import com.webrtc.droid.demo.entity.MESSAGE_TYPE_OFFER
import com.webrtc.droid.demo.observer.SimplePeerObserver
import com.webrtc.droid.demo.observer.SimpleSdpObserver
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import org.webrtc.VideoDecoderFactory
import org.webrtc.VideoEncoderFactory

/**
 * @author: huiqing.huang
 * create on 2023/5/24 15:10
 * desc: WebRTC连接相关
 */
class RTCConnection(
    context: Context,
    override var onConstructSdpSuccess: (msgType: Int, sessionDescription: SessionDescription) -> Unit
) : IRTCConnection {

    private var mPeerConnection: PeerConnection? = null
    private var mRootEglBase: EglBase = EglBase.create()
    private val mPeerConnectionFactory: PeerConnectionFactory

    init {
        mPeerConnectionFactory = createPeerConnectionFactory(context)
    }

    override fun doUserStartCall() {
        initPeerConnection()
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        }
        mPeerConnection?.createOffer(
            constructSimpleObserverByMsgType(MESSAGE_TYPE_OFFER),
            mediaConstraints
        )
    }

    override fun doUserAnswerRemoteOfferCall(entity: CallInfoEntity) {
        initPeerConnection()
        mPeerConnection?.setRemoteDescription(
            SimpleSdpObserver(),
            SessionDescription(SessionDescription.Type.OFFER, entity.sdp)
        )
        mPeerConnection?.createAnswer(
            constructSimpleObserverByMsgType(MESSAGE_TYPE_ANSWER),
            MediaConstraints()
        )
    }

    override fun onRemoteAnswerReceived(entity: CallInfoEntity) {
        mPeerConnection?.setRemoteDescription(
            SimpleSdpObserver(),
            SessionDescription(SessionDescription.Type.ANSWER, entity.sdp)
        )
    }

    override fun onRemoteIceCandidateReceived(entity: CallInfoEntity) {
        val remoteIceCandidate = IceCandidate(
            entity.candidateInfoEntity?.id,
            entity.candidateInfoEntity?.label ?: 0,
            entity.candidateInfoEntity?.candidate
        )
        mPeerConnection?.addIceCandidate(remoteIceCandidate)
    }

    override fun doEndCall() {
        mPeerConnection?.close()
        mPeerConnection = null
    }

    private fun constructSimpleObserverByMsgType(
        msgType: Int
    ): SimpleSdpObserver {
        return object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.i(
                    TAG,
                    "Create $msgType success, description: ${sessionDescription.description}"
                )
                mPeerConnection?.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                onConstructSdpSuccess.invoke(msgType, sessionDescription)
            }
        }
    }

    private fun initPeerConnection() {
        if (mPeerConnection == null) {
            mPeerConnection = createPeerConnection()
        }
    }

    private fun createPeerConnection(): PeerConnection? {
        Log.i(TAG, "Create PeerConnection ...")
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val configuration = PeerConnection.RTCConfiguration(iceServers)
        val connection =
            mPeerConnectionFactory.createPeerConnection(configuration, mPeerConnectionObserver)
        return connection?.apply { addTrack(getAudioTrack()) } ?: kotlin.run {
            Log.e(TAG, "Failed to createPeerConnection !")
            null
        }
    }

    private val mPeerConnectionObserver: PeerConnection.Observer = object : SimplePeerObserver() {
        override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
            super.onIceCandidatesRemoved(iceCandidates)
            mPeerConnection?.removeIceCandidates(iceCandidates)
        }
    }

    private fun createPeerConnectionFactory(context: Context?): PeerConnectionFactory {
        val encoderFactory: VideoEncoderFactory
        val decoderFactory: VideoDecoderFactory
        encoderFactory = DefaultVideoEncoderFactory(
            mRootEglBase.eglBaseContext, false, true
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

    private fun getAudioTrack() = AudioTracker(mPeerConnectionFactory).audioTrack

    companion object {
        private const val TAG = "RTCConnection"
    }
}