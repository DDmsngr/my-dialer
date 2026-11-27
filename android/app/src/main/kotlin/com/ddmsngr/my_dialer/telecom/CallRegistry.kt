package com.ddmsngr.my_dialer.telecom

import android.content.Context
import android.telecom.Call
import android.util.Log
import com.ddmsngr.my_dialer.recording.CallRecordingService
import java.util.concurrent.CopyOnWriteArraySet

object CallRegistry {
    private val calls = CopyOnWriteArraySet<Call>()
    @Volatile var activeCall: Call? = null
        private set
    @Volatile var state: Int = Call.STATE_DISCONNECTED
        private set
    @Volatile private var recordingStartedForCall: Call? = null

    fun add(call: Call) {
        calls.add(call)
        activeCall = call
        Log.i(TAG, "call added state=${stateName(call.state)}")
    }

    fun remove(context: Context, call: Call) {
        calls.remove(call)
        val wasRecording = recordingStartedForCall === call
        if (wasRecording) recordingStartedForCall = null
        if (activeCall === call) activeCall = calls.lastOrNull()
        Log.i(TAG, "call removed remaining=${calls.size} wasRecording=$wasRecording")
        if (wasRecording && calls.none { it.state == Call.STATE_ACTIVE }) {
            stopRecording(context)
        }
    }

    fun stateChanged(context: Context, call: Call, newState: Int) {
        state = newState
        Log.i(TAG, "call state=${stateName(newState)}")
        if (newState == Call.STATE_ACTIVE && recordingStartedForCall !== call) {
            activeCall = call
            recordingStartedForCall = call
            try {
                Log.i(TAG, "starting recording service")
                context.startForegroundService(CallRecordingService.startIntent(context))
            } catch (error: Throwable) {
                recordingStartedForCall = null
                Log.e(TAG, "recording service start failed: ${error.javaClass.simpleName}", error)
            }
        } else if (newState == Call.STATE_DISCONNECTED) {
            if (recordingStartedForCall === call) {
                recordingStartedForCall = null
                Log.i(TAG, "stopping recording service")
                stopRecording(context)
            }
        }
    }

    private fun stopRecording(context: Context) {
        try {
            context.startService(CallRecordingService.stopIntent(context))
        } catch (error: Throwable) {
            Log.e(TAG, "recording service stop failed: ${error.javaClass.simpleName}", error)
        }
    }

    private fun stateName(value: Int): String = when (value) {
        Call.STATE_RINGING -> "RINGING"
        Call.STATE_DIALING -> "DIALING"
        Call.STATE_CONNECTING -> "CONNECTING"
        Call.STATE_ACTIVE -> "ACTIVE"
        Call.STATE_HOLDING -> "HOLDING"
        Call.STATE_DISCONNECTED -> "DISCONNECTED"
        else -> value.toString()
    }

    private const val TAG = "MyDialerCall"
}
