package com.webrtc.droid.demo.entity

/**
 * @author: huiqing.huang
 * create on 2023/5/23 16:43
 * desc: 通话接通过程中需要传输的信息
 */
data class CallInfoEntity(
    val userId: String,
    val msgType: Int,
    val sdp: String? = null,
    val candidateInfoEntity: CandidateInfoEntity? = null
)
