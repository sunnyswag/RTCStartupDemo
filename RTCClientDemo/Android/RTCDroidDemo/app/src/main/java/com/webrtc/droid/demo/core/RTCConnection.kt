package com.webrtc.droid.demo.core

import android.content.Context
import android.util.Log
import com.webrtc.droid.demo.core.api.IAudioServiceControl
import com.webrtc.droid.demo.core.api.IRTCControl
import com.webrtc.droid.demo.core.api.IVideoServiceControl
import com.webrtc.droid.demo.core.service.AudioService
import com.webrtc.droid.demo.core.service.NullAudioServiceControl
import com.webrtc.droid.demo.core.service.NullVideoServiceControl
import com.webrtc.droid.demo.core.service.VideoService
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
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import org.webrtc.VideoDecoderFactory
import org.webrtc.VideoEncoderFactory
import org.webrtc.VideoTrack

/**
 * @author: huiqing.huang
 * create on 2023/5/24 15:10
 * desc: WebRTC连接相关
 */
class RTCConnection(
    context: Context,
    override var onConstructSdpSuccess: (msgType: Int, sessionDescription: SessionDescription) -> Unit,
    override var onConstructIceCandidateSuccess: (iceCandidate: IceCandidate) -> Unit,
    private val audioService: AudioService? = null,
    private val videoService: VideoService? = null,
) : IRTCControl,
    IVideoServiceControl by (videoService ?: NullVideoServiceControl) ,
    IAudioServiceControl by (audioService ?: NullAudioServiceControl)
{

    private var mPeerConnection: PeerConnection? = null
    private var mRootEglBase: EglBase = EglBase.create()
    private val mPeerConnectionFactory: PeerConnectionFactory

    init {
        mPeerConnectionFactory = createPeerConnectionFactory(context)
        videoService?.doInit(mPeerConnectionFactory, mRootEglBase)
        audioService?.doInit(mPeerConnectionFactory)
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
                onConstructSdpSuccess(msgType, sessionDescription)
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
        return connection?.apply {
            videoService?.getVideoServiceTrack()?.let { addTrack(it) }
            audioService?.getAudioServiceTrack()?.let { addTrack(it) }
        } ?: kotlin.run {
            Log.e(TAG, "Failed to createPeerConnection !")
            null
        }
    }

    private val mPeerConnectionObserver: PeerConnection.Observer = object : SimplePeerObserver() {
        override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
            super.onIceCandidatesRemoved(iceCandidates)
            mPeerConnection?.removeIceCandidates(iceCandidates)
        }

        override fun onIceCandidate(iceCandidate: IceCandidate) {
            super.onIceCandidate(iceCandidate)
            onConstructIceCandidateSuccess(iceCandidate)
        }

        override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<MediaStream>) {
            val remoteVideoTrack = rtpReceiver.track()
            if (remoteVideoTrack is VideoTrack) {
                Log.i(TAG, "onAddVideoTrack")
                remoteVideoTrack.setEnabled(true)
                remoteVideoTrack.addSink {
                    videoService?.remoteSurfaceView?.onFrame(it)
                }
            }
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

    companion object {
        private const val TAG = "RTCConnection"
    }
}