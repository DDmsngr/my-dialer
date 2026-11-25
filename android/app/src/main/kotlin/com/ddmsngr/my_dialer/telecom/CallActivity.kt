package com.ddmsngr.my_dialer.telecom

import android.app.Activity
import android.os.Bundle
import android.telecom.Call
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class CallActivity : Activity() {
    private var held = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        render()
    }

    private fun render() {
        val call = CallRegistry.activeCall ?: run { finish(); return }
        val details = call.details
        val number = details.handle?.schemeSpecificPart ?: "Неизвестный номер"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        val title = TextView(this).apply {
            text = number
            textSize = 28f
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        val stateText = TextView(this).apply {
            text = stateName(call.state)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        root.addView(stateText, LinearLayout.LayoutParams(-1, -2))
        if (call.state == Call.STATE_RINGING) {
            root.addView(action("Принять") { DialerInCallService.current?.answer(call); render() })
        }
        if (call.state == Call.STATE_ACTIVE || call.state == Call.STATE_HOLDING) {
            root.addView(action(if (held) "Снять удержание" else "Удержать") {
                if (held) DialerInCallService.current?.unhold(call) else DialerInCallService.current?.hold(call)
                held = !held
                render()
            })
        }
        root.addView(action("Завершить") { DialerInCallService.current?.disconnect(call); finish() })
        setContentView(root)
    }

    private fun action(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private fun stateName(state: Int): String = when (state) {
        Call.STATE_RINGING -> "Входящий вызов"
        Call.STATE_DIALING -> "Набор номера"
        Call.STATE_CONNECTING -> "Соединение"
        Call.STATE_ACTIVE -> "Разговор"
        Call.STATE_HOLDING -> "На удержании"
        else -> "Вызов завершён"
    }
}
