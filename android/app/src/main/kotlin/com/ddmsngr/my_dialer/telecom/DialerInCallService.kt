package com.ddmsngr.my_dialer.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.ddmsngr.my_dialer.recording.CallRecordingService

class DialerInCallService : InCallService() {
    override fun onCreate() { super.onCreate(); current = this }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallRegistry.add(call)
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                CallRegistry.stateChanged(this@DialerInCallService, call, state)
                if (state == Call.STATE_RINGING || state == Call.STATE_ACTIVE || state == Call.STATE_DIALING) {
                    startCallActivity()
                }
            }
        })
        CallRegistry.stateChanged(this, call, call.state)
        startCallActivity()
    }

    private fun startCallActivity() {
        val intent = Intent(this, CallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        CallRegistry.remove(call)
        stopService(CallRecordingService.stopIntent(this))
        super.onCallRemoved(call)
    }

    fun answer(call: Call) = call.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun disconnect(call: Call) = call.disconnect()
    fun hold(call: Call) = call.hold()
    fun unhold(call: Call) = call.unhold()
    fun mute(enabled: Boolean) = setMuted(enabled)

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    companion object { @Volatile var current: DialerInCallService? = null }
}
