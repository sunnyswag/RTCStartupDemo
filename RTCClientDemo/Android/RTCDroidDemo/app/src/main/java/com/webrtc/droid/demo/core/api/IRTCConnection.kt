package com.webrtc.droid.demo.core.api

import com.webrtc.droid.demo.entity.CallInfoEntity
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

/**
 * @author: huiqing.huang
 * create on 2023/5/24 16:07
 * desc: WebRTC 连接相关接口
 */
interface IRTCConnection {

    /** 当 webRTC 收集好 sdp 相关信息时，会收到这个回调 */
    var onConstructSdpSuccess: (msgType: Int, sessionDescription: SessionDescription) -> Unit
    /** 当 webRTC 收集好 candidate 相关信息时，会收到这个回调 */
    var onConstructIceCandidateSuccess: (iceCandidate: IceCandidate) -> Unit

    /**
     * 用户手动触发，开始通话，此时会发送 [MESSAGE_TYPE_OFFER] 类型的消息
     */
    fun doUserStartCall()

    /**
     * 收到 [MESSAGE_TYPE_OFFER] 类型的消息。如果选择接听通话，此时会发送 [MESSAGE_TYPE_ANSWER] 类型的消息
     * @param entity 为收到的 [CallInfoEntity]，会将其中的 sdp 信息设置到 [PeerConnection] 中
     */
    fun doUserAnswerRemoteOfferCall(entity: CallInfoEntity)

    /**
     * 收到 [MESSAGE_TYPE_ANSWER] 类型的消息，会将其中的 sdp 信息设置到 [PeerConnection] 中
     * @param entity 为收到的 [CallInfoEntity]
     */
    fun onRemoteAnswerReceived(entity: CallInfoEntity)

    /**
     * 收到 [MESSAGE_TYPE_CANDIDATE] 类型的消息，会将其中的 candidate 信息设置到 [PeerConnection] 中
     * @param entity 为收到的 [CallInfoEntity]
     */
    fun onRemoteIceCandidateReceived(entity: CallInfoEntity)

    /**
     * 收到 [MESSAGE_TYPE_HANGUP] 类型的消息，或者用户主动挂断对话，会调用此方法
     */
    fun doEndCall()
}