package com.webrtc.droid.demo.core.service

import android.util.Log
import com.webrtc.droid.demo.applicationContext
import com.webrtc.droid.demo.core.api.IVideoServiceControl
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.EglBase
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * @author: huiqing.huang
 * create on 2023/5/25 10:12
 * desc: 视频相关
 */
class VideoService(
    private val localSurfaceView: SurfaceViewRenderer? = null,
    val remoteSurfaceView: SurfaceViewRenderer? = null
) : IVideoServiceControl {
    private var videoTrack: VideoTrack? = null
    private var mSurfaceTextureHelper: SurfaceTextureHelper? = null
    private var mVideoCapture: VideoCapturer? = null

    fun doInit(peerConnectionFactory: PeerConnectionFactory, rootEglBase: EglBase) {
        initSurfaceView(rootEglBase)
        val videoSource = initTextureAndTrack(rootEglBase, peerConnectionFactory)
        initVideoCapture(videoSource)
    }

    override fun startCapture() {
        mVideoCapture?.startCapture(
            VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, VIDEO_FPS
        )
    }

    override fun stopCapture() {
        mVideoCapture?.stopCapture()
    }

    override fun releaseService() {
        localSurfaceView?.release()
        remoteSurfaceView?.release()
        videoTrack?.dispose()
        mSurfaceTextureHelper?.dispose()
        mVideoCapture?.dispose()
    }

    override fun getVideoServiceTrack(): MediaStreamTrack? {
        return videoTrack
    }

    private fun initSurfaceView(rootEglBase: EglBase) {

        localSurfaceView?.run {
            init(rootEglBase.eglBaseContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setMirror(true)
            setEnableHardwareScaler(false)
        }

        remoteSurfaceView?.run {
            init(rootEglBase.eglBaseContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setMirror(true)
            setEnableHardwareScaler(true)
            setZOrderMediaOverlay(true)
        }
    }

    private fun initTextureAndTrack(
        rootEglBase: EglBase,
        peerConnectionFactory: PeerConnectionFactory
    ): VideoSource {
        mSurfaceTextureHelper =
            SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext)
        val videoSource: VideoSource = peerConnectionFactory.createVideoSource(false)
        videoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource).apply {
            setEnabled(true)
            addSink { localSurfaceView?.onFrame(it) }
        }

        return videoSource
    }

    private fun initVideoCapture(videoSource: VideoSource) {
        mVideoCapture = createVideoCapture()
        mVideoCapture?.initialize(
            mSurfaceTextureHelper,
            applicationContext(),
            videoSource.capturerObserver
        )
    }

    /*
     * Read more about Camera2 here
     * https://developer.android.com/reference/android/hardware/camera2/package-summary.html
     **/
    private fun createVideoCapture(): VideoCapturer? {
        return if (Camera2Enumerator.isSupported(applicationContext())) {
            createCameraCapture(Camera2Enumerator(applicationContext()))
        } else {
            createCameraCapture(Camera1Enumerator(true))
        }
    }

    private fun createCameraCapture(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        Log.d(TAG, "Looking for front facing cameras.")
        return deviceNames.filter { enumerator.isFrontFacing(it) }
            .firstNotNullOfOrNull { enumerator.createCapturer(it, null) }
            ?: deviceNames.filter { !enumerator.isFrontFacing(it) }
                .firstNotNullOfOrNull { enumerator.createCapturer(it, null) }
    }

    companion object {
        private const val TAG = "VideoTracker"
        private const val VIDEO_TRACK_ID = "VIDEO_TRACKER_V0"
        private const val VIDEO_RESOLUTION_WIDTH = 1280
        private const val VIDEO_RESOLUTION_HEIGHT = 720
        private const val VIDEO_FPS = 30
    }
}