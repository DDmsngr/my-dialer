package com.ddmsngr.mydialerprobe;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Process;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

import com.ddmsngr.mydialerprobe.service.IProbeService;
import com.ddmsngr.mydialerprobe.service.UserService;

import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private static final int RECORD_PERMISSION = 100;
    private static final int SHIZUKU_PERMISSION = 101;
    private final StringBuilder log = new StringBuilder();
    private TextView output;
    private WavRecorder appRecorder;
    private IProbeService userService;
    private Shizuku.UserServiceArgs userArgs;
    private final android.content.ServiceConnection serviceConnection = new android.content.ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            userService = IProbeService.Stub.asInterface(binder);
            append("UserService connected: " + call(() -> userService.identity()));
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            userService = null;
            append("UserService disconnected");
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildView();
        Shizuku.addBinderReceivedListenerSticky(() -> append("Shizuku binder uid=" + safeShizukuUid()));
        Shizuku.addBinderDeadListener(() -> append("Shizuku binder dead"));
        Shizuku.addRequestPermissionResultListener((requestCode, result) -> append("Shizuku permission " + requestCode + " result=" + result));
        append(String.format(Locale.US, "app uid=%d sdk=%d", Process.myUid(), android.os.Build.VERSION.SDK_INT));
        append("Shizuku=" + safeShizukuUid());
    }

    private void buildView() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(32, 32, 32, 32);
        TextView title = new TextView(this);
        title.setText("My Dialer native probe\nЗапускать во время активного обычного звонка");
        title.setTextSize(18);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));
        content.addView(button("Запросить RECORD_AUDIO", v -> requestRecordPermission()));
        content.addView(button("Обычный UID: запись 10 секунд", v -> recordApp()));
        content.addView(button("Запросить Shizuku", v -> requestShizuku()));
        content.addView(button("Подключить UserService", v -> bindUserService()));
        content.addView(button("Shell UID: запись 10 секунд", v -> recordShell()));
        output = new TextView(this);
        output.setTextIsSelectable(true);
        output.setTextSize(13);
        content.addView(output, new LinearLayout.LayoutParams(-1, 0, 1));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
    }

    private Button button(String text, android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(listener);
        return button;
    }

    private void requestRecordPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_PERMISSION);
        } else append("RECORD_AUDIO already granted");
    }

    private void requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) { append("Shizuku binder unavailable"); return; }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) append("Shizuku permission already granted");
            else Shizuku.requestPermission(SHIZUKU_PERMISSION);
        } catch (Throwable error) { append(error.toString()); }
    }

    private void bindUserService() {
        try {
            if (!Shizuku.pingBinder()) { append("Shizuku binder unavailable"); return; }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) { append("Grant Shizuku first"); return; }
            userArgs = new Shizuku.UserServiceArgs(new ComponentName(getPackageName(), UserService.class.getName()))
                    .daemon(false).processNameSuffix("probe").debuggable(true).version(1);
            Shizuku.bindUserService(userArgs, serviceConnection);
            append("UserService requested");
        } catch (Throwable error) { append(error.toString()); }
    }

    private void recordApp() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            append("Grant RECORD_AUDIO first"); return;
        }
        File file = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "app-voice-call.wav");
        appRecorder = new WavRecorder();
        append("APP " + appRecorder.start(file.getAbsolutePath(), 10000));
        new Thread(() -> { sleep(11500); append("APP " + appRecorder.stop()); }).start();
    }

    private void recordShell() {
        if (userService == null) { append("Connect UserService first"); return; }
        String path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyDialerProbe/shell-voice-call.wav").getAbsolutePath();
        append("SHELL " + call(() -> userService.startRecording(path, 10000)));
        new Thread(() -> { sleep(11500); append("SHELL " + call(() -> userService.stopRecording())); }).start();
    }

    private int safeShizukuUid() { try { return Shizuku.getUid(); } catch (Throwable ignored) { return -1; } }
    private String call(Callable callable) { try { return callable.call(); } catch (Throwable error) { return error.toString(); } }
    private void sleep(long millis) { try { Thread.sleep(millis); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } }
    private void append(String line) { runOnUiThread(() -> { log.append(line).append('\n'); if (output != null) output.setText(log.toString()); }); }
    private interface Callable { String call() throws Exception; }
}
