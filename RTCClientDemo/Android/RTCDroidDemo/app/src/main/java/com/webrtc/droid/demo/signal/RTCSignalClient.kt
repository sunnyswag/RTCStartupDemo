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
    private var mSocket: Socket? = null
    lateinit var userId: String
        private set
    private lateinit var mRoomName: String

    var broadcastReceived: ((entity: CallInfoEntity?) -> Unit)? = null

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
        Log.i(TAG, "$CLIENT_COMMAND_BROADCAST: $message")
        mSocket?.emit(CLIENT_COMMAND_BROADCAST, message)
    }

    private fun listenSignalEvents() {
        mSocket?.on(Socket.EVENT_CONNECT_ERROR) { args -> Log.e(TAG, "onConnectError: $args") }
        mSocket?.on(Socket.EVENT_ERROR) { args -> Log.e(TAG, "onError: $args") }
        mSocket?.on(CLIENT_COMMAND_BROADCAST) { args ->
            val msg = (args[0] as String).toBean<CallInfoEntity>()
            if (this@RTCSignalClient.userId != msg?.userId) {
                broadcastReceived?.invoke(msg)
            }
        }
    }

    companion object {
        private const val TAG = "RTCSignalClient"
        const val CLIENT_COMMAND_BROADCAST = "broadcast"
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