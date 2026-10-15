# Mondafono Agent Guide

## Project Scope

Mondafono is a minimal Android camera recorder written in Kotlin. It is intentionally limited and always have to use Android framework APIs over other Libs:

- `Camera2` for camera access and preview.
- `MediaCodec` and `MediaMuxer` for H.264/MP4 video recording.
- Native Android `Activity`, `SurfaceView`, `Canvas`, and `View` for the UI.

The application is landscape-only, fullscreen, and uses a minimal retro-style overlay. Do not add Compose, AndroidX, Material, AppCompat, CameraX, or coroutine dependencies unless explicitly requested.

## Repository Layout

- `app/src/main/java/dev/polyrics/mondafono/MainActivity.kt`: activity composition, permissions, and feature coordination.
- `app/src/main/java/dev/polyrics/mondafono/camera/CameraController.kt`: Camera2 lifecycle and capture sessions.
- `app/src/main/java/dev/polyrics/mondafono/recording/`: recording settings, MediaCodec buffer, and MP4 clips.
- `app/src/main/java/dev/polyrics/mondafono/sensor/CollisionDetector.kt`: accelerometer-based collision detection.
- `app/src/main/java/dev/polyrics/mondafono/sound/CollisionSound.kt`: generated collision alert tones.
- `app/src/main/java/dev/polyrics/mondafono/storage/RecordingStorage.kt`: public video storage through MediaStore.
- `app/src/main/java/dev/polyrics/mondafono/ui/`: native retro overlay and panels.
- `app/src/main/AndroidManifest.xml`: camera permission, landscape activity, and launcher declaration.
- `app/src/main/res/drawable/ic_launcher.xml`: minimal launcher icon.
- `app/build.gradle.kts`: Android build configuration and release shrinking.
- `gradle/libs.versions.toml`: plugin version catalog; keep it dependency-free apart from required build plugins.

## Build And Verify

Use the Gradle wrapper from the project root:

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

The optimized release APK is generated at:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

Always run at least `assembleRelease` after code or manifest changes. A physical Android device is required to verify camera and hardware encoder behavior.

## Implementation Rules

- Keep the APK as small as possible.
- Prefer framework APIs over third-party libraries.
- Do not add image assets for UI elements; draw simple UI elements with `Canvas`, except if the user explicitly requests it.
- Do not request audio or storage permissions unless the recording design explicitly requires them.
- Store recordings in app-specific storage unless a user-visible media export is requested.
- Preserve landscape orientation and immersive fullscreen behavior.
- Keep camera work on the camera `HandlerThread`; avoid blocking the UI thread.
- Release `CameraDevice`, `CameraCaptureSession`, `MediaCodec`, `MediaMuxer`, and `Surface` resources on every shutdown path.
- Use R8 and resource shrinking for release builds.

## Change Checklist

1. Check whether the change adds a dependency or resource that can be avoided with a framework API.
2. Build both debug and release variants when changing Kotlin, the manifest, or Gradle configuration.
3. Check the release APK size after dependency or resource changes.
4. Test camera permission denial, preview startup, recording start/stop, and activity destruction on a physical device.

## Code Rules
1. KISS for all the code
2. POO principes to the limit: abstraction, encapsulation, inheritance, and polymorphism.
3. SOLID for all.
4. Code Human-Understandable, keep the functions short, prioritizing the little classes and functions for do one thing or groups of related things, good code indentation.
5. Clean-Code all the time, Variables, functions, and classes should have meaningful names.
6. Don't put comments to all, and if you do include them, keep them short.
7. English first, all the code have to be in English

## Workflow
1. `main` is the main branch, so always fork it when adding new features, fix bux or whatever thing.
2. Merge directly to `main` is bad, use pull request or suggest to the user do a pull request.
3. Do commits with less of 200 lines (no strict), and less of 10 files.
4. SSD for all, and TDD, do the test first and then the code.
