#include "common.h"

#include <errno.h>
#include <unistd.h>

int main(int argc, char *argv[]) {
    const char *root = argc > 1 ? argv[1] : ".";

    SDL_SetHintWithPriority(SDL_HINT_ACCELEROMETER_AS_JOYSTICK, "0", SDL_HINT_OVERRIDE);
    SDL_SetHintWithPriority(SDL_HINT_TV_REMOTE_AS_JOYSTICK, "0", SDL_HINT_OVERRIDE);
    SDL_SetHintWithPriority(SDL_HINT_AUTO_UPDATE_JOYSTICKS, "0", SDL_HINT_OVERRIDE);
    SDL_SetHintWithPriority(SDL_HINT_GAMECONTROLLER_IGNORE_DEVICES_EXCEPT, "", SDL_HINT_OVERRIDE);

    if (chdir(root) != 0) {
        SDL_Log("SDLPoP Android: chdir(%s) failed: %s", root, strerror(errno));
    }
    setenv("HOME", root, 1);

    char executable_path[POP_MAX_PATH];
    snprintf_check(executable_path, sizeof(executable_path), "%s/sdlpop", root);

    g_argc = 1;
    g_argv = calloc(2, sizeof(char *));
    if (g_argv == NULL) {
        SDL_Log("SDLPoP Android: unable to allocate argv");
        return 1;
    }
    g_argv[0] = executable_path;
    g_argv[1] = NULL;

    pop_main();

    free(g_argv);
    return 0;
}
