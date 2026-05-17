# Native Port Status

The native Android port is implemented. The Android app no longer uses the
earlier Java gameplay prototype; it starts the upstream SDLPoP C runtime through
SDL's Android activity.

## Implemented

1. Android SDK command-line tools, CMake, and NDK are installed locally.
2. Gradle fetches SDLPoP, SDL2, and SDL2_image from the pinned upstream
   repositories and refs in `gradle.properties`.
3. The fetched checkouts live under `external/`, are ignored by Git, and may be
   reset or cleaned by dependency-prep tasks.
4. `externalNativeBuild` compiles fetched SDLPoP C sources into the APK.
5. `android_sdlpop_main.c` adapts the desktop entry point for Android assets
   and disables SDL Android accelerometer/remote joystick hints before SDLPoP
   initializes SDL.
6. SDLPoP data, config, mods, replays, README, and license files are bundled as
   Android assets and copied into app storage.
7. `NativeSDLPoPActivity` overlays Android touch controls that inject native SDL
   key events for arrows, Shift/action, Enter, and Escape. The controls
   reconcile the full pressed-button state on every touch event, use tighter
   visible hit areas for the D-pad/action buttons, ignore overlay-toggle touches
   for gameplay input, and force-release all synthetic keys on gesture end,
   focus loss, and window visibility changes. Activity-level filters swallow
   joystick/gamepad navigation events so device sensor/controller noise cannot
   appear as a held direction.
8. `DepthOverlayView` adds a transparent OpenGL ES 2.0 3D overlay above the SDL
   surface and below the Android controls. The cube control cycles the overlay
   through strong, light, and off modes.
9. The Android build defines `SDLPOP_ANDROID_TOUCH_ONLY`, and
   `patches/sdlpop-android-touch-only.patch` keeps SDLPoP in keyboard/touch
   mode instead of initializing or auto-switching to joystick mode.

## Remaining Polish

- Add optional landscape-specific control placement if portrait is not desired
  on a target device.
- Feed live SDLPoP room/player state into the 3D overlay if the overlay should
  react to exact tile geometry instead of rendering a generic depth frame.
- Add a release signing config and GPL source-distribution package before
  publishing an APK.
- Consider exposing `SDLPoP.ini` editing/import/export through Android storage
  if modding from the device matters.
