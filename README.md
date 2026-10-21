# CineMode TV (WebView wrapper for Android TV)

## Build (Android Studio)
1. Install Android Studio, then File > Open > select this folder. Accept any Gradle/AGP upgrade prompts and let it sync.
2. Build > Build Bundle(s) / APK(s) > Build APK(s).
3. APK: app/build/outputs/apk/debug/app-debug.apk

## Install on the TV
On the TV: Settings > System > About > tap "Build" 7x, then Developer options > enable USB/Network debugging.
    adb connect 192.168.1.4:5555
    adb install -r app-debug.apk
(Approve the prompt on the TV the first time.)
Or copy the APK to a USB stick / use the Downloader app and open it from the TV.

## Customize
- URL: edit `startUrl` in MainActivity.kt
- Icon/banner: replace app/src/main/res/drawable/banner.xml with a 320x180 image
- The app blocks pop-ups and navigation to other domains. Edit shouldOverrideUrlLoading to relax this.
