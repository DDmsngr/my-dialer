package com.ddmsngr.mydialerprobe.service;

interface IProbeService {
    String startRecording(String path, int durationMs);
    String stopRecording();
    String identity();
}
