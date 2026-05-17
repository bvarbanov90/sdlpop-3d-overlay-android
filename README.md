# SDLPoP Android 3D Overlay PoC

This is a proof-of-concept Android wrapper and touch/3D overlay for the real
[`NagyD/SDLPoP`](https://github.com/NagyD/SDLPoP) C/SDL2 engine. It is not an
official SDLPoP project.

The repository does not vendor SDLPoP. Gradle fetches upstream SDLPoP from the
official GitHub repository at the pinned ref in `gradle.properties`, applies the
small Android touch-only patch in `patches/`, then builds it with SDL2 and
SDL2_image from their official upstream repositories. The APK copies SDLPoP data
files into app storage on first launch and runs the native game through
`SDLActivity`.

Blunt status: this is a playable PoC wrapper, not a polished Android release.
Gameplay, level transitions, menus, quicksave, settings, cutscenes, and
completion logic come from SDLPoP itself. The 3D layer is a cosmetic transparent
OpenGL ES room-shell overlay above the native 2D game surface.

## Project Layout

- `app/src/main/java/com/example/sdlpopoverlay/NativeSDLPoPActivity.java`
  installs game assets, starts SDL, and layers the 3D overlay plus Android touch
  controls over the native game surface.
- `app/src/main/java/com/example/sdlpopoverlay/DepthOverlayView.java` renders
  the transparent 3D frame, grid, side rails, and animated glow markers.
- `app/src/main/cpp/CMakeLists.txt` builds SDL2, SDL2_image, and upstream
  SDLPoP into Android shared libraries, with `SDLPOP_ANDROID_TOUCH_ONLY`
  enabled for the Android APK.
- `app/src/main/cpp/android_sdlpop_main.c` changes into the installed game data
  directory, disables SDL accelerometer/remote joystick hints, and calls
  SDLPoP's `pop_main()`.
- `gradle.properties` pins the upstream SDLPoP, SDL2, and SDL2_image
  repositories and refs.
- `patches/sdlpop-android-touch-only.patch` is the Android-specific SDLPoP
  patch applied after fetching upstream.
- `external/SDLPoP`, `external/SDL2`, and `external/SDL2_image` are generated
  Git checkouts used by the build. They are ignored by Git and may be reset or
  cleaned by Gradle tasks.

## Build

Prerequisites:

- Android SDK platform/build tools
- Android NDK `29.0.14206865`
- CMake `3.22.1`
- Git on `PATH`

Fetch native sources and build the debug APK from this directory:

```powershell
.\gradlew.bat prepareNativeDeps
.\gradlew.bat assembleDebug
```

`assembleDebug` also depends on `prepareNativeDeps`, so the explicit prepare
step is optional. It is useful when checking that upstream sources can be
fetched and patched cleanly.

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install and launch on a connected device/emulator:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.example.sdlpopoverlay/.NativeSDLPoPActivity
```

## Upstream Sources

Default dependency pins live in `gradle.properties`:

- SDLPoP: `https://github.com/NagyD/SDLPoP.git` at
  `3c5add5fb7f83d4ceb542823ab66d00146c4271b`
- SDL2: `https://github.com/libsdl-org/SDL.git` at `release-2.32.10`
- SDL2_image: `https://github.com/libsdl-org/SDL_image.git` at
  `release-2.8.12`

For SDLPoP changes, edit or add patches under `patches/`. Do not make lasting
changes directly under `external/SDLPoP`; Gradle treats that checkout as
disposable build output.

## Touch Controls

- Left D-pad: arrow keys for movement, crouch, climb, and menu navigation.
- Large diamond: Shift/action. Hold it to walk carefully, pick up items, fight,
  or grab/hold ledges while falling.
- Right-side up arrow: duplicate Up key so you can hold action and press Up with
  another finger to climb from a ledge.
- Checkmark: Enter/confirm.
- Pause icon: Escape, opening SDLPoP's own pause/settings/quicksave menu.
- Cube icon: cycles the 3D overlay between strong, light, and off.

The important ledge interaction is native SDLPoP behavior: hold the diamond
while falling near a ledge, keep holding it while hanging, then press Up to climb
or Down to drop.

The touch layer now reconciles the complete pressed-button state on every touch
event instead of keeping per-pointer key counts. It sends key-down only for
newly pressed controls, sends key-up for controls no longer covered by any
finger, ignores cube-toggle touches for gameplay input, and force-releases all
synthetic SDL keys plus SDL's internal keyboard state when gestures end, focus
changes, or the window disappears. The Activity also swallows joystick/gamepad
navigation events so physical-phone sensor or controller noise cannot look like
a held direction. The button hit area is kept close to the visible circles to
reduce accidental left/right or up/down changes while dragging near the D-pad.

The Android native build deliberately disables SDLPoP joystick mode. SDL's
Android backend can expose the phone accelerometer as a joystick by default,
which makes SDLPoP menus behave as if Down is held when the phone is tilted.
The Android APK is touch-first, so controller/accelerometer input is ignored.

## 3D Overlay

The 3D overlay is a separate transparent OpenGL ES 2.0 surface. It is added
above SDL's game surface and below the Android controls, so it does not own
gameplay input. The current pass renders a responsive perspective room shell
with translucent side walls, floor and ceiling slabs, grid lines, scan planes,
side rails, and animated glow markers around the 2D SDLPoP viewport. Use the
cube control to reduce or disable it while playing.

## Verified

Smoke-tested on the local Android emulator:

- launches to the SDLPoP 1.24 RC title/info screen
- touch input advances through title and intro
- reaches level 1 with native assets
- 3D overlay renders over title and gameplay without hiding SDL output
- cube control cycles overlay strong/light/off
- left, right, up, down, action, confirm, and pause controls show held/released
  states correctly
- drag-across and drag-outside gestures release stale directions cleanly
- pause menu remains on the same row while idle, with no accelerometer/joystick
  Down-repeat
- holding and releasing Down in the pause menu stops on release instead of
  continuing to scroll
- repeated live right/left holds leave the game idle with no stuck input
- pause opens SDLPoP's native menu

Local smoke-test screenshots may exist in the project root as
`native-sdlpop-touch-*.png`, `sdlpop-3d-overlay-*.png`, and
`control-*.png` / `phone-lock-fix-*.png`. They are ignored by Git.

## License

SDLPoP is GPLv3. This Android wrapper is intended to be distributed under GPLv3
when shipped together with SDLPoP code or data; the root `LICENSE` contains the
GPLv3 text.

Any distributed APK that includes SDLPoP must keep the upstream license and
corresponding-source obligations intact. In practice, publish this wrapper
source, the pinned upstream source refs, and the Android patch used to build the
APK.
