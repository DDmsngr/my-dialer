package com.ddmsngr.my_dialer.recording;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;

import com.ddmsngr.my_dialer.service.ProbeBinder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Locale;

public final class WavRecorder {
    private final Object lock = new Object();
    private AudioRecord recorder;
    private Thread worker;
    private volatile boolean running;
    private String result = "not started";

    public String start(String path, int durationMs) {
        synchronized (lock) {
            if (running) return "already running";
            try {
                int rate = 16000;
                int channels = AudioFormat.CHANNEL_IN_MONO;
                int minBuffer = AudioRecord.getMinBufferSize(rate, channels, AudioFormat.ENCODING_PCM_16BIT);
                if (minBuffer <= 0) return "getMinBufferSize=" + minBuffer;
                AudioRecord audio = new AudioRecord(MediaRecorder.AudioSource.VOICE_CALL, rate, channels,
                        AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuffer * 2, 4096));
                recorder = audio;
                if (audio.getState() != AudioRecord.STATE_INITIALIZED) {
                    result = "AudioRecord state=" + audio.getState();
                    audio.release(); recorder = null; return result;
                }
                File output = new File(path);
                File parent = output.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) return "cannot create parent=" + parent;
                FileOutputStream stream = new FileOutputStream(output);
                stream.write(header(rate, 1, 0));
                running = true;
                result = "started uid=" + Process.myUid() + " path=" + path;
                worker = new Thread(() -> capture(audio, stream, output, rate, Math.max(1000, durationMs)), "dialer-recorder");
                worker.start();
                return result;
            } catch (Throwable error) {
                result = error.getClass().getSimpleName() + ": " + error.getMessage();
                releaseRecorder();
                return result;
            }
        }
    }

    public String stop() {
        Thread thread;
        synchronized (lock) { running = false; thread = worker; }
        if (thread != null && thread != Thread.currentThread()) {
            try { thread.join(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        return result;
    }

    private void capture(AudioRecord audio, FileOutputStream stream, File output, int rate, int durationMs) {
        long deadline = System.currentTimeMillis() + durationMs;
        int bytes = 0;
        try (FileOutputStream out = stream) {
            audio.startRecording();
            byte[] buffer = new byte[8192];
            while (running && System.currentTimeMillis() < deadline) {
                int count = audio.read(buffer, 0, buffer.length);
                if (count < 0) throw new IOException("AudioRecord.read=" + count);
                if (count > 0) { out.write(buffer, 0, count); bytes += count; }
            }
            out.flush();
            try (RandomAccessFile raf = new RandomAccessFile(output, "rw")) { raf.write(header(rate, 1, bytes)); }
            result = String.format(Locale.US, "finished uid=%d bytes=%d file=%s", Process.myUid(), bytes, output);
        } catch (Throwable error) {
            result = error.getClass().getSimpleName() + ": " + error.getMessage();
        } finally {
            synchronized (lock) { running = false; releaseRecorder(); worker = null; }
        }
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try { recorder.stop(); } catch (Throwable ignored) { }
            recorder.release(); recorder = null;
        }
    }

    private static byte[] header(int rate, int channels, int size) {
        byte[] h = new byte[44];
        ascii(h, 0, "RIFF"); putInt(h, 4, 36 + size); ascii(h, 8, "WAVEfmt "); putInt(h, 16, 16);
        putShort(h, 20, 1); putShort(h, 22, channels); putInt(h, 24, rate);
        putInt(h, 28, rate * channels * 2); putShort(h, 32, channels * 2); putShort(h, 34, 16);
        ascii(h, 36, "data"); putInt(h, 40, size);
        return h;
    }
    private static void ascii(byte[] h, int offset, String value) { for (int i = 0; i < value.length(); i++) h[offset + i] = (byte) value.charAt(i); }
    private static void putShort(byte[] h, int offset, int value) { h[offset] = (byte) value; h[offset + 1] = (byte) (value >>> 8); }
    private static void putInt(byte[] h, int offset, int value) { for (int i = 0; i < 4; i++) h[offset + i] = (byte) (value >>> (8 * i)); }
}
