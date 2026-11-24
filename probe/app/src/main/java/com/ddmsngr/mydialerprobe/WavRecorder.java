package com.ddmsngr.mydialerprobe;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;

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
                int sampleRate = 48000;
                int channels = AudioFormat.CHANNEL_IN_STEREO;
                int channelCount = 2;
                int encoding = AudioFormat.ENCODING_PCM_16BIT;
                int minBuffer = AudioRecord.getMinBufferSize(sampleRate, channels, encoding);
                if (minBuffer <= 0) return "getMinBufferSize=" + minBuffer;
                AudioRecord audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.VOICE_CALL,
                        sampleRate,
                        channels,
                        encoding,
                        Math.max(minBuffer * 4, sampleRate * channelCount));
                recorder = audioRecord;
                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    result = "AudioRecord state=" + audioRecord.getState();
                    audioRecord.release();
                    recorder = null;
                    return result;
                }
                File output = new File(path);
                File parent = output.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    result = "cannot create parent=" + parent;
                    releaseRecorder();
                    return result;
                }
                FileOutputStream stream = new FileOutputStream(output);
                writeHeader(stream, sampleRate, channelCount, 0);
                running = true;
                result = String.format(Locale.US, "started uid=%d path=%s", Process.myUid(), path);
                worker = new Thread(() -> capture(audioRecord, stream, output, sampleRate, channelCount, durationMs), "wav-recorder");
                worker.start();
                return result;
            } catch (Throwable error) {
                result = error.getClass().getName() + ": " + error.getMessage();
                releaseRecorder();
                return result;
            }
        }
    }

    public String stop() {
        Thread thread;
        synchronized (lock) {
            if (!running) return result;
            running = false;
            thread = worker;
        }
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(3000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return result;
    }

    private void capture(AudioRecord audioRecord, FileOutputStream stream, File output, int sampleRate, int channels, int durationMs) {
        long deadline = System.currentTimeMillis() + Math.max(1000, durationMs);
        int bytes = 0;
        byte[] buffer = new byte[8192];
        try (FileOutputStream out = stream) {
            audioRecord.startRecording();
            while (running && System.currentTimeMillis() < deadline) {
                int count = audioRecord.read(buffer, 0, buffer.length);
                if (count < 0) throw new IOException("AudioRecord.read=" + count);
                if (count > 0) {
                    out.write(buffer, 0, count);
                    bytes += count;
                }
            }
            out.flush();
            patchHeader(output, bytes, sampleRate, channels);
            result = String.format(Locale.US, "finished uid=%d bytes=%d file=%s", Process.myUid(), bytes, output);
        } catch (Throwable error) {
            result = error.getClass().getName() + ": " + error.getMessage();
        } finally {
            synchronized (lock) {
                running = false;
                releaseRecorder();
            }
        }
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try { recorder.stop(); } catch (Throwable ignored) { }
            recorder.release();
            recorder = null;
        }
    }

    private static void writeHeader(FileOutputStream out, int sampleRate, int channels, int dataSize) throws IOException {
        byte[] header = header(sampleRate, channels, dataSize);
        out.write(header);
    }

    private static void patchHeader(File file, int dataSize, int sampleRate, int channels) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(0);
            raf.write(header(sampleRate, channels, dataSize));
        }
    }

    private static byte[] header(int sampleRate, int channels, int dataSize) {
        int byteRate = sampleRate * channels * 2;
        int blockAlign = channels * 2;
        byte[] h = new byte[44];
        putAscii(h, 0, "RIFF");
        putInt(h, 4, 36 + dataSize);
        putAscii(h, 8, "WAVEfmt ");
        putInt(h, 16, 16);
        putShort(h, 20, 1);
        putShort(h, 22, channels);
        putInt(h, 24, sampleRate);
        putInt(h, 28, byteRate);
        putShort(h, 32, blockAlign);
        putShort(h, 34, 16);
        putAscii(h, 36, "data");
        putInt(h, 40, dataSize);
        return h;
    }

    private static void putAscii(byte[] target, int offset, String value) {
        for (int i = 0; i < value.length(); i++) target[offset + i] = (byte) value.charAt(i);
    }

    private static void putShort(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
    }

    private static void putInt(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
        target[offset + 2] = (byte) (value >>> 16);
        target[offset + 3] = (byte) (value >>> 24);
    }
}
