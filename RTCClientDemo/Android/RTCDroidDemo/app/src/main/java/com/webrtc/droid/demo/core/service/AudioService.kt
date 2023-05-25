package com.webrtc.droid.demo.core.service

import com.webrtc.droid.demo.core.api.IAudioServiceControl
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnectionFactory

/**
 * @author: huiqing.huang
 * create on 2023/5/24 15:18
 * desc: 音频相关
 */
class AudioService: IAudioServiceControl {


    private lateinit var audioTrack: AudioTrack

    fun doInit(peerConnectionFactory: PeerConnectionFactory) {
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        audioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource).apply {
            setEnabled(true)
        }
    }

    override fun getAudioServiceTrack(): MediaStreamTrack {
        return audioTrack
    }

    companion object {
        private const val AUDIO_TRACK_ID = "AUDIO_TRACKER_A0"
    }
}