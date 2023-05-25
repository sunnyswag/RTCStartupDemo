package com.webrtc.droid.demo.core.service

import com.webrtc.droid.demo.core.api.IAudioServiceControl
import com.webrtc.droid.demo.core.api.IVideoServiceControl

/**
 * @author: huiqing.huang
 * create on 2023/5/25 14:54
 * desc:
 */

object NullAudioServiceControl : IAudioServiceControl {
    override fun getAudioServiceTrack() = null
}

object NullVideoServiceControl : IVideoServiceControl {
    override fun releaseService() = Unit

    override fun startCapture() = Unit

    override fun stopCapture() = Unit

    override fun getVideoServiceTrack() = null
}