# Arima Pro Player 🎵

**Arima Pro Player** is an audiophile-grade, Bit-Perfect high-resolution music player designed natively for Android. Built purely with modern Android tools like **Jetpack Compose** and **Media3 (ExoPlayer)**, it focuses heavily on uncompromising audio quality, fluid UI/UX, and extreme robustness.

---

## 🌟 Key Features

*   **Bit-Perfect Audio Engine:** Bypasses standard Android OS audio mixers (where supported) to deliver untampered, raw PCM and high-res audio data directly to your DAC.
*   **Direct DSD Decoding (DSD256+):** Hardware-level decoding support for DSD tracks (DSF/DFF) via DoP (DSD over PCM) or Native DSD (for compatible USB DACs).
*   **USB DAC Exclusive Mode:** Automatically detects external USB DACs (hot-plugging) and takes exclusive hardware control to prevent down-sampling and system interruptions.
*   **10-Band Graphic Equalizer:** Integrated custom DSP equalizer allowing fine tuning across the frequency spectrum. *(Note: Enabling EQ bypasses Bit-Perfect Mode).*
*   **Robust Background Playback:** Protected against Android 12+ strict foreground service crashes and zombie processes.
*   **Fluid & Premium UI/UX:** Features dynamic mesh gradients generated from album art palettes (`Palette API`), responsive spring animations, smooth screen crossfades, and a sleek Dark Glass aesthetics.
*   **Fault-Tolerant Library Scanner:** Safely handles detached SD Cards (`SecurityException`) without crashing and seamlessly resumes when storage is re-mounted.

## 🛠️ Technology Stack

*   **Language:** Kotlin (100%)
*   **UI Framework:** Jetpack Compose (Material 3)
*   **Audio Core:** AndroidX Media3 (ExoPlayer) + Custom AudioEngine wrapper
*   **Audio Metadata:** JAudioTagger
*   **Architecture:** MVVM (Model-View-ViewModel) + StateFlow/Coroutines

## 🚀 How to Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open Android Studio.
2. Select **Open** and choose the directory containing this project (`arima-pro-player`).
3. Allow Gradle to sync and download all necessary dependencies (Media3, Compose, Palette, etc.).
4. Connect an Android Device or start an Emulator.
5. Click **Run** (Shift + F10) to build and deploy the application.

## 🎧 Usage Instructions

1. **Permissions:** Upon first launch, allow the requested `READ_MEDIA_AUDIO` and Notification permissions.
2. **Scan Library:** Navigate to **Settings** > **Scan Full Library Now** to index your local storage.
3. **USB DAC:** Connect an external USB DAC. The app will prompt you to route audio directly to the DAC using *Exclusive Mode*.
4. **Enjoy:** Use the Library tabs to navigate through Songs, Albums, or Artists and experience your music in premium quality.

---
*Built with ❤️ for true Audiophiles.*
