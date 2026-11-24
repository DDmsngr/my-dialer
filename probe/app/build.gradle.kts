plugins {
    id("com.android.application")
}

android {
    namespace = "com.ddmsngr.mydialerprobe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ddmsngr.mydialerprobe"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
