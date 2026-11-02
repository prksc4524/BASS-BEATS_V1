# BASS BEATS V1 - Radio integration starter

This project preserves the existing offline player and adds a RADIO tab.

## Added
- Internet permission and network-state permission.
- Radio tab with Hindi Music, Chhattisgarh, and India News sections.
- Vividh Bharati, FM Gold Delhi, and FM Rainbow Delhi direct HLS stream candidates.
- Radio playback is routed through the existing PlaybackService so the existing audio-session Equalizer and BassBoost can be applied.
- Chhattisgarh and News entries are shown as pending until their direct stream URLs are verified.

## Important testing note
The three HLS URLs are publicly listed legacy Akashvani stream URLs. They were not tested from this build environment, so availability may have changed. They may need replacement with current official stream endpoints. The project is source code only; no APK has been built or device-tested.

## Build
Open this folder in Android Studio (compileSdk 35, Android Gradle Plugin compatible with your installed Android Studio). Sync Gradle, then Build > Build APK(s).
