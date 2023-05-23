package com.webrtc.droid.demo.signal

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONException
import org.json.JSONObject
import java.net.URISyntaxException

class RTCSignalClient {
    private var mOnSignalEventListener: OnSignalEventListener? = null
    private var mSocket: Socket? = null
    var userId: String? = null
        private set
    private var mRoomName: String? = null

    interface OnSignalEventListener {
        fun onConnected()
        fun onConnecting()
        fun onDisconnected()
        fun onRemoteUserJoined(userId: String?)
        fun onRemoteUserLeft(userId: String?)
        fun onBroadcastReceived(message: JSONObject?)
    }

    fun setSignalEventListener(listener: OnSignalEventListener?) {
        mOnSignalEventListener = listener
    }

    fun joinRoom(url: String, userId: String, roomName: String) {
        Log.i(TAG, "joinRoom: $url, $userId, $roomName")
        try {
            mSocket = IO.socket(url)
            mSocket?.connect()
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            return
        }
        this.userId = userId
        mRoomName = roomName
        listenSignalEvents()
        try {
            val args = JSONObject()
            args.put("userId", userId)
            args.put("roomName", roomName)
            mSocket?.emit("join-room", args.toString())
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun leaveRoom() {
        Log.i(TAG, "leaveRoom: $mRoomName")
        if (mSocket == null) {
            return
        }
        try {
            val args = JSONObject()
            args.put("userId", userId)
            args.put("roomName", mRoomName)
            mSocket!!.emit("leave-room", args.toString())
            mSocket!!.close()
            mSocket = null
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun sendMessage(message: JSONObject) {
        Log.i(TAG, "broadcast: $message")
        if (mSocket == null) {
            return
        }
        mSocket!!.emit("broadcast", message)
    }

    private fun listenSignalEvents() {
        if (mSocket == null) {
            return
        }
        mSocket!!.on(Socket.EVENT_CONNECT_ERROR) { args -> Log.e(TAG, "onConnectError: $args") }
        mSocket!!.on(Socket.EVENT_ERROR) { args -> Log.e(TAG, "onError: $args") }
        mSocket!!.on(Socket.EVENT_CONNECT) {
            val sessionId = mSocket!!.id()
            Log.i(TAG, "onConnected")
            if (mOnSignalEventListener != null) {
                mOnSignalEventListener!!.onConnected()
            }
        }
        mSocket!!.on(Socket.EVENT_CONNECTING) {
            Log.i(TAG, "onConnecting")
            if (mOnSignalEventListener != null) {
                mOnSignalEventListener!!.onConnecting()
            }
        }
        mSocket!!.on(Socket.EVENT_DISCONNECT) {
            Log.i(TAG, "onDisconnected")
            if (mOnSignalEventListener != null) {
                mOnSignalEventListener!!.onDisconnected()
            }
        }
        mSocket!!.on("user-joined") { args ->
            val userId = args[0] as String
            if (this@RTCSignalClient.userId != userId && mOnSignalEventListener != null) {
                mOnSignalEventListener!!.onRemoteUserJoined(userId)
            }
            Log.i(TAG, "onRemoteUserJoined: $userId")
        }
        mSocket!!.on("user-left") { args ->
            val userId = args[0] as String
            if (this@RTCSignalClient.userId != userId && mOnSignalEventListener != null) {
                mOnSignalEventListener!!.onRemoteUserLeft(userId)
            }
            Log.i(TAG, "onRemoteUserLeft: $userId")
        }
        mSocket!!.on("broadcast") { args ->
            val msg = args[0] as JSONObject
            try {
                val userId = msg.getString("userId")
                if (this@RTCSignalClient.userId != userId && mOnSignalEventListener != null) {
                    mOnSignalEventListener!!.onBroadcastReceived(msg)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        private const val TAG = "RTCSignalClient"
        const val MESSAGE_TYPE_OFFER = 0x01
        const val MESSAGE_TYPE_ANSWER = 0x02
        const val MESSAGE_TYPE_CANDIDATE = 0x03
        const val MESSAGE_TYPE_HANGUP = 0x04
        private var mInstance: RTCSignalClient? = null
        @JvmStatic
        val instance: RTCSignalClient?
            get() {
                synchronized(RTCSignalClient::class.java) {
                    if (mInstance == null) {
                        mInstance = RTCSignalClient()
                    }
                }
                return mInstance
            }
    }
}