package com.webrtc.droid.demo.core.api

import org.webrtc.MediaStreamTrack

/**
 * @author: huiqing.huang
 * create on 2023/5/25 11:28
 * desc: 服务控制相关接口
 */
interface IVideoServiceControl {

    fun releaseService()

    fun startCapture()

    fun stopCapture()

    fun getVideoServiceTrack(): MediaStreamTrack?
}