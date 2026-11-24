# My Dialer native probe

This is a disposable Android probe, not the product UI. It measures two paths during an active cellular call:

1. `AudioRecord` in the normal application process.
2. The same `AudioRecord` call in a Shizuku UserService, which should run as UID 2000 when Shizuku was started by ADB.

The probe does not claim that either path works until a real APK is installed and the captured WAV files are checked for both voices. Build with GitHub Actions only; do not run Gradle locally.
