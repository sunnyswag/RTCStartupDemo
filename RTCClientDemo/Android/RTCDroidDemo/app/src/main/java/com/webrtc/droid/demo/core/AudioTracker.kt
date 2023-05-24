package com.webrtc.droid.demo.core

import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory

/**
 * @author: huiqing.huang
 * create on 2023/5/24 15:18
 * desc: 音频相关
 */
class AudioTracker(peerConnectionFactory: PeerConnectionFactory) {

    private val mAudioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
    val audioTrack: AudioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, mAudioSource).apply {
        setEnabled(true)
    }

    companion object {
        const val AUDIO_TRACK_ID = "AUDIO_TRACKER_A0"
    }
}