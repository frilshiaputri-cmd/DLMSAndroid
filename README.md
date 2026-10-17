# DLMS Audio Android (source project)

A starter Android Studio project for experimenting with Android 10+ Audio Playback Capture, a 5-band EQ, and delay controls.

## Important limitations
- This is source code, not a compiled APK. No Android SDK/Gradle build environment is bundled here.
- Android requires the user to approve system-audio capture each session.
- It only captures audio from other apps that permit playback capture and use capturable audio usages. Some apps/content will not be captured.
- Capturing then replaying audio may add latency and can behave differently across phone vendors. Do not use this as a guaranteed system-wide audio driver.
- For reliable system-wide EQ/latency with all apps, Android generally needs a privileged/system audio effect, root/custom ROM, or external DSP hardware.
- This is an experimental starter, not a production-ready audio processor. Test at low volume. Avoid using headphones at high levels.

## Build
1. Install Android Studio with Android SDK 35 and JDK 17.
2. Open this folder as a project.
3. Let Gradle sync, then Build > Build APK(s).
4. Install the debug APK on an Android 10+ device.
5. Tap **SAMBUNGKAN AUDIO SISTEM** and approve the Android capture prompt.
6. Start audio in a supported app, then test EQ and delay.

## Project details
- Package: `id.dlms.audio`
- Minimum Android: API 29 (Android 10)
- UI: native Kotlin / Android Views
