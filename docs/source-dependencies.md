# Source Dependencies

This project is intentionally small wrapper code plus build glue. The native
game engine and SDL dependencies are fetched by Gradle into `external/`.

## Pinned Upstreams

The default pins are kept in `gradle.properties`:

- `sdlpop.repo`: `https://github.com/NagyD/SDLPoP.git`
- `sdlpop.ref`: `3c5add5fb7f83d4ceb542823ab66d00146c4271b`
- `sdl2.repo`: `https://github.com/libsdl-org/SDL.git`
- `sdl2.ref`: `release-2.32.10`
- `sdl2Image.repo`: `https://github.com/libsdl-org/SDL_image.git`
- `sdl2Image.ref`: `release-2.8.12`

Run this to materialize the ignored local checkouts:

```powershell
.\gradlew.bat prepareNativeDeps
```

## Patch Policy

Do not commit files from `external/`. Those directories are build artifacts and
Gradle may run `git reset --hard` and `git clean -fdx` inside them.

Any SDLPoP source changes needed by this Android wrapper should live as patch
files under `patches/` and be applied by the Gradle dependency-prep tasks. The
current patch only forces the Android build into keyboard/touch mode and avoids
SDL joystick/controller initialization so phone sensors or controller noise do
not behave like a held direction.

## Publishing An APK

SDLPoP is GPLv3. If you publish an APK containing SDLPoP code or data, publish
the corresponding source for the exact build: this wrapper repository, the
pinned upstream refs, and the local patch files.
