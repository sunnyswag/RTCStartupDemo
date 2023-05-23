package com.webrtc.droid.demo.activity

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import androidx.appcompat.app.AppCompatActivity
import com.webrtc.droid.demo.databinding.ActivityMainBinding
import pub.devrel.easypermissions.EasyPermissions

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater).apply { setContentView(root) }
        binding.JoinRoomBtn.setOnClickListener {
            val address = binding.ServerEditText.text.toString()
            val roomName = binding.RoomEditText.text.toString()
            if (!TextUtils.isEmpty(roomName)) {
                val intent = Intent(this@MainActivity, CallActivity::class.java)
                intent.putExtra(SERVER_ADDRESS, address)
                intent.putExtra(ROOM_NAME, roomName)
                startActivity(intent)
            }
        }
        val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (!EasyPermissions.hasPermissions(this, *perms)) {
            EasyPermissions.requestPermissions(
                this,
                "Need permissions for camera & microphone",
                0,
                *perms
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    companion object {
        const val SERVER_ADDRESS = "SERVER_ADDRESS"
        const val ROOM_NAME = "ROOM_NAME"
    }

}