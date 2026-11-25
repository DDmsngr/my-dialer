package com.ddmsngr.my_dialer.service;

import android.os.Process;

import com.ddmsngr.my_dialer.recording.WavRecorder;

public final class UserService extends ProbeBinder {
    private final WavRecorder recorder = new WavRecorder();

    public UserService() { }

    @Override public String startRecording(String path, int durationMs) {
        return recorder.start(path, durationMs);
    }

    @Override public String stopRecording() { return recorder.stop(); }

    public String identity() { return "uid=" + Process.myUid() + " pid=" + Process.myPid(); }
    public void destroy() { recorder.stop(); System.exit(0); }
}
