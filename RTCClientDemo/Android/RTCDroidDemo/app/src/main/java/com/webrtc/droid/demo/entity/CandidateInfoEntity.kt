package com.webrtc.droid.demo.entity

/**
 * @author: huiqing.huang
 * create on 2023/5/23 17:03
 * desc: WebRTC candidate 相关信息
 */
data class CandidateInfoEntity(
    val label : Int,
    val id : String,
    val candidate : String
)