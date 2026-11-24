package com.ddmsngr.mydialerprobe.service;

import android.os.Process;

import com.ddmsngr.mydialerprobe.WavRecorder;

public final class UserService extends IProbeService.Stub {
    private final WavRecorder recorder = new WavRecorder();

    public UserService() {
    }

    @Override
    public String identity() {
        return "uid=" + Process.myUid() + " pid=" + Process.myPid();
    }

    @Override
    public String startRecording(String path, int durationMs) {
        return recorder.start(path, durationMs);
    }

    @Override
    public String stopRecording() {
        return recorder.stop();
    }

    public void destroy() {
        recorder.stop();
        System.exit(0);
    }
}
