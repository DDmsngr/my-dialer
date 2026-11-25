package com.ddmsngr.my_dialer.telecom

import android.content.Context
import android.telecom.Call
import com.ddmsngr.my_dialer.recording.CallRecordingService
import java.util.concurrent.CopyOnWriteArraySet

object CallRegistry {
    private val calls = CopyOnWriteArraySet<Call>()
    @Volatile var activeCall: Call? = null
        private set
    @Volatile var state: Int = Call.STATE_DISCONNECTED
        private set

    fun add(call: Call) { calls.add(call); activeCall = call }
    fun remove(call: Call) { calls.remove(call); if (activeCall === call) activeCall = calls.lastOrNull() }

    fun stateChanged(context: Context, call: Call, newState: Int) {
        state = newState
        if (newState == Call.STATE_ACTIVE) {
            activeCall = call
            context.startForegroundService(CallRecordingService.startIntent(context))
        } else if (newState == Call.STATE_DISCONNECTED) {
            context.stopService(CallRecordingService.stopIntent(context))
        }
    }
}
