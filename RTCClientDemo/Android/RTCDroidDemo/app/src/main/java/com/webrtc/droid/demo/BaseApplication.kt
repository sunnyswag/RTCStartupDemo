package com.webrtc.droid.demo

import android.app.Application
import android.content.Context

class BaseApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private var instance: BaseApplication? = null
        fun applicationContext(): Context {
            return instance!!.applicationContext
        }
    }
}

fun applicationContext(): Context {
    return BaseApplication.applicationContext()
}