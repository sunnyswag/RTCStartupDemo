package com.webrtc.droid.demo.observer

import android.util.Log
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/**
 * @author: huiqing.huang
 * create on 2023/5/23 18:33
 * desc:
 */
open class SimpleSdpObserver : SdpObserver {
    private val tag = "SimpleSdpObserver"

    override fun onCreateSuccess(sessionDescription: SessionDescription) {
        Log.i(tag, "SdpObserver: onCreateSuccess !")
    }

    override fun onSetSuccess() {
        Log.i(tag, "SdpObserver: onSetSuccess")
    }

    override fun onCreateFailure(msg: String) {
        Log.e(tag, "SdpObserver onCreateFailure: $msg")
    }

    override fun onSetFailure(msg: String) {
        Log.e(tag, "SdpObserver onSetFailure: $msg")
    }
}