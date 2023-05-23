package com.webrtc.droid.demo.entity

/**
 * @author: huiqing.huang
 * create on 2023/5/23 10:24
 * desc: 房间相关的命令，如：加入房间、离开房间等
 */
data class RoomCommandEntity(
    val userId: String,
    val roomName: String,
)

const val JOIN_ROOM = "join-room"
const val LEAVE_ROOM = "leave-room"