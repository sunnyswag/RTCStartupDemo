# WebRTC-Android-demo

An Android one-to-one video call demo using WebRTC.

### 1. Usage

* Change Signal Server IP to local IP address，in `res/values/strings.xml:default_server` and `res/xml/network_security_config.xml:domain` 

- Build & Deploy RTCSignalServer which runs on port 8080 by default.
- Build & Install the demo app on 2 Android phone and launch it.
  - Type the same room name, such as Room1
  - Click the `Start Call` button in one App, You'll see the remote video appear. And when clicked the `End Call` button, You'll see the remote video disappear.

### 2. Contact

Email：lujun.hust@gmail.com
