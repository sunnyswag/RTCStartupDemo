package com.webrtc.droid.demo.activity

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.webrtc.droid.demo.R
import pub.devrel.easypermissions.EasyPermissions

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val serverEditText = findViewById<EditText>(R.id.ServerEditText)
        val roomEditText = findViewById<EditText>(R.id.RoomEditText)
        findViewById<View>(R.id.JoinRoomBtn).setOnClickListener {
            val addr = serverEditText.text.toString()
            val roomName = roomEditText.text.toString()
            if ("" != roomName) {
                val intent = Intent(this@MainActivity, CallActivity::class.java)
                intent.putExtra("ServerAddr", addr)
                intent.putExtra("RoomName", roomName)
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

}