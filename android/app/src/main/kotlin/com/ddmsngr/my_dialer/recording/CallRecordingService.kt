package com.ddmsngr.my_dialer.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.Environment
import android.os.Process
import android.util.Log
import com.ddmsngr.my_dialer.service.ProbeBinder
import com.ddmsngr.my_dialer.service.UserService
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

class CallRecordingService : Service() {
    private var worker: Thread? = null
    private var userService: ProbeBinder.Proxy? = null
    private var userServiceArgs: Shizuku.UserServiceArgs? = null
    private var shizukuBound = false
    @Volatile private var running = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Log.i(TAG, "Shizuku user service connected")
            userService = ProbeBinder.Proxy(binder)
            startShellCapture()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            Log.w(TAG, "Shizuku user service disconnected")
            userService = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        Log.i(TAG, "service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForeground(NOTIFICATION_ID, notification("Подготовка записи звонка"))
            Log.i(TAG, "foreground started")
        } catch (error: Throwable) {
            Log.e(TAG, "foreground start failed: ${error.javaClass.simpleName}", error)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running && !shizukuBound && worker == null) startWithShizuku()
        return START_NOT_STICKY
    }

    private fun startWithShizuku() {
        Log.i(TAG, "starting Shizuku capture")
        try {
            if (!Shizuku.pingBinder()) { startAppCapture("Shizuku недоступен"); return }
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startAppCapture("Нет разрешения Shizuku")
                return
            }
            userServiceArgs = Shizuku.UserServiceArgs(ComponentName(packageName, UserService::class.java.name))
                .daemon(false).processNameSuffix("recording").debuggable(true).version(1)
            Shizuku.bindUserService(userServiceArgs!!, serviceConnection)
            shizukuBound = true
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification("Запрос shell-записи через Shizuku"))
        } catch (error: Throwable) {
            Log.e(TAG, "Shizuku start failed: ${error.javaClass.simpleName}", error)
            startAppCapture("Не удалось подключить Shizuku")
        }
    }

    private fun startShellCapture() {
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyDialer/Recordings")
        val file = File(directory, "call-${System.currentTimeMillis()}.wav")
        val status = userService?.startRecording(file.absolutePath, 12 * 60 * 60 * 1000) ?: "UserService недоступен"
        Log.i(TAG, "shell recorder result=$status file=${file.name}")
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification("Shell: $status"))
    }

    private fun startAppCapture(fallbackReason: String) {
        val directory = File(getExternalFilesDir("Recordings"), "calls")
        if (!directory.exists() && !directory.mkdirs()) return
        val file = File(directory, "call-${System.currentTimeMillis()}.wav")
        val rate = 16000
        val channels = AudioFormat.CHANNEL_IN_MONO
        val minBuffer = AudioRecord.getMinBufferSize(rate, channels, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) {
            Log.w(TAG, "AudioRecord buffer unavailable reason=$fallbackReason")
            updateNotification("Запись недоступна: $fallbackReason")
            return
        }
        Log.i(TAG, "starting app capture reason=$fallbackReason")
        val audio = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_CALL, rate, channels, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer * 2, 4096))
        } catch (_: Throwable) { updateNotification("AudioRecord недоступен: $fallbackReason"); return }
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord not initialized reason=$fallbackReason")
            audio.release()
            updateNotification("VOICE_CALL запрещён системой; Shizuku $fallbackReason")
            return
        }
        running = true
        updateNotification("Запись звонка активна, uid ${Process.myUid()}")
        worker = Thread {
            var bytes = 0
            try {
                FileOutputStream(file).use { stream ->
                    stream.write(header(rate, 1, 0))
                    val buffer = ByteArray(8192)
                    audio.startRecording()
                    while (running) {
                        val count = audio.read(buffer, 0, buffer.size)
                        if (count < 0) break
                        if (count > 0) { stream.write(buffer, 0, count); bytes += count }
                    }
                    stream.flush()
                }
                RandomAccessFile(file, "rw").use { it.write(header(rate, 1, bytes)) }
                updateNotification("Запись сохранена: ${file.name}, $bytes байт")
            } catch (error: Throwable) {
                updateNotification("Ошибка записи: ${error.javaClass.simpleName}")
            } finally {
                try { audio.stop() } catch (_: Throwable) { }
                audio.release()
                running = false
                worker = null
            }
        }.also { it.start() }
    }

    private fun stopCapture() {
        running = false
        worker?.let { if (it !== Thread.currentThread()) try { it.join(2500) } catch (_: InterruptedException) { Thread.currentThread().interrupt() } }
        worker = null
        try { userService?.stopRecording() } catch (_: Throwable) { }
        if (shizukuBound) {
            try { userServiceArgs?.let { Shizuku.unbindUserService(it, serviceConnection, true) } } catch (_: Throwable) { }
            shizukuBound = false
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("My Dialer")
            .setContentText(text.take(120))
            .setOngoing(running || shizukuBound)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Запись звонков", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        Log.i(TAG, "service destroyed")
        stopCapture()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MyDialerRecording"
        private const val CHANNEL = "call_recording"
        private const val NOTIFICATION_ID = 701
        private const val ACTION_START = "com.ddmsngr.mydialer.START_RECORDING"
        private const val ACTION_STOP = "com.ddmsngr.mydialer.STOP_RECORDING"
        fun startIntent(context: Context) = Intent(context, CallRecordingService::class.java).setAction(ACTION_START)
        fun stopIntent(context: Context) = Intent(context, CallRecordingService::class.java).setAction(ACTION_STOP)

        private fun header(rate: Int, channels: Int, data: Int): ByteArray {
            val h = ByteArray(44)
            fun ascii(offset: Int, value: String) { value.forEachIndexed { i, c -> h[offset + i] = c.code.toByte() } }
            fun int(offset: Int, value: Int) { for (i in 0..3) h[offset + i] = (value ushr (i * 8)).toByte() }
            fun short(offset: Int, value: Int) { h[offset] = value.toByte(); h[offset + 1] = (value ushr 8).toByte() }
            ascii(0, "RIFF"); int(4, 36 + data); ascii(8, "WAVEfmt "); int(16, 16); short(20, 1)
            short(22, channels); int(24, rate); int(28, rate * channels * 2); short(32, channels * 2)
            short(34, 16); ascii(36, "data"); int(40, data)
            return h
        }
    }
}
