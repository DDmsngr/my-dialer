package com.ddmsngr.my_dialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val channelName = "com.ddmsngr.mydialer/telecom"
    private val roleRequest = 4101
    private val permissionsRequest = 4102
    private var pendingNumber: String? = null

    override fun configureFlutterEngine(engine: FlutterEngine) {
        super.configureFlutterEngine(engine)
        MethodChannel(engine.dartExecutor.binaryMessenger, channelName).setMethodCallHandler { call, result ->
            when (call.method) {
                "requestDefaultDialer" -> {
                    requestDefaultDialer()
                    result.success(null)
                }
                "placeCall" -> {
                    val number = call.argument<String>("number")?.trim().orEmpty()
                    if (number.isEmpty()) result.error("EMPTY_NUMBER", "Введите номер", null)
                    else {
                        pendingNumber = number
                        requestCallPermissions()
                        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                            startPendingCall()
                            result.success(null)
                        } else {
                            result.error("MISSING_PERMISSION", "Подтвердите разрешение на телефонные вызовы", null)
                        }
                    }
                }
                "requestPermissions" -> {
                    requestCallPermissions()
                    result.success(null)
                }
                "openSettings" -> {
                    startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    result.success(null)
                }
                "isDefaultDialer" -> result.success(isDefaultDialer())
                else -> result.notImplemented()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionsRequest && pendingNumber != null && checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startPendingCall()
        }
    }

    private fun startPendingCall() {
        val number = pendingNumber ?: return
        pendingNumber = null
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}")))
    }

    private fun requestCallPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), permissionsRequest)
    }

    private fun isDefaultDialer(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)
    }

    private fun requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER), roleRequest)
            }
        }
    }
}
