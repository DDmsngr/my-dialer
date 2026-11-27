package com.ddmsngr.my_dialer.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.ddmsngr.my_dialer.recording.CallRecordingService

class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Log.i(TAG, "phone state OFFHOOK, starting recording service")
                try {
                    context.startForegroundService(CallRecordingService.startIntent(context))
                } catch (error: Throwable) {
                    Log.e(TAG, "phone state recording start failed: ${error.javaClass.simpleName}", error)
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.i(TAG, "phone state IDLE, stopping recording service")
                try {
                    context.startService(CallRecordingService.stopIntent(context))
                } catch (error: Throwable) {
                    Log.e(TAG, "phone state recording stop failed: ${error.javaClass.simpleName}", error)
                }
            }
        }
    }

    companion object {
        private const val TAG = "MyDialerCall"
    }
}
