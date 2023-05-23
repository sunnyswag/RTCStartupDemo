package com.webrtc.droid.demo.signal

import android.util.Log
import com.webrtc.droid.demo.entity.CallInfoEntity
import com.webrtc.droid.demo.entity.JOIN_ROOM
import com.webrtc.droid.demo.entity.LEAVE_ROOM
import com.webrtc.droid.demo.entity.RoomCommandEntity
import com.webrtc.droid.demo.safeLet
import com.webrtc.droid.demo.toBean
import com.webrtc.droid.demo.toJson
import io.socket.client.IO
import io.socket.client.Socket
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
        fun onBroadcastReceived(entity: CallInfoEntity?)
    }

    fun setSignalEventListener(listener: OnSignalEventListener?) {
        mOnSignalEventListener = listener
    }

    fun joinRoom(url: String, userId: String, roomName: String) {
        Log.i(TAG, "${JOIN_ROOM}: $url, $userId, $roomName")
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
        mSocket?.emit(JOIN_ROOM, RoomCommandEntity(userId, roomName).toJson())
    }

    fun leaveRoom() {
        Log.i(TAG, "${LEAVE_ROOM}: $mRoomName")
        safeLet(userId, mRoomName) { userId, roomName ->
            mSocket?.emit(LEAVE_ROOM, RoomCommandEntity(userId, roomName).toJson())
        }
        mSocket?.close()
        mSocket = null
    }

    fun sendMessage(message: String) {
        Log.i(TAG, "broadcast: $message")
        mSocket?.emit("broadcast", message)
    }

    private fun listenSignalEvents() {
        if (mSocket == null) {
            return
        }
        mSocket!!.on(Socket.EVENT_CONNECT_ERROR) { args -> Log.e(TAG, "onConnectError: $args") }
        mSocket!!.on(Socket.EVENT_ERROR) { args -> Log.e(TAG, "onError: $args") }
        mSocket!!.on(Socket.EVENT_CONNECT) {
            Log.i(TAG, "onConnected")
            mOnSignalEventListener?.onConnected()
        }
        mSocket!!.on(Socket.EVENT_CONNECTING) {
            Log.i(TAG, "onConnecting")
            mOnSignalEventListener?.onConnecting()
        }
        mSocket!!.on(Socket.EVENT_DISCONNECT) {
            Log.i(TAG, "onDisconnected")
            mOnSignalEventListener?.onDisconnected()
        }
        mSocket!!.on("user-joined") { args ->
            val userId = args[0] as String
            safeLet(userId, mOnSignalEventListener) { id, listener ->
                listener.onRemoteUserJoined(id)
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
            val msg = (args[0] as String).toBean<CallInfoEntity>()
            if (this@RTCSignalClient.userId != msg?.userId && mOnSignalEventListener != null) {
                mOnSignalEventListener!!.onBroadcastReceived(msg)
            }
        }
    }

    companion object {
        private const val TAG = "RTCSignalClient"
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