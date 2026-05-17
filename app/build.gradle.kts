plugins {
    id("com.android.application")
}

val externalRoot = rootProject.layout.projectDirectory.dir("external")
val sdlpopRoot = externalRoot.dir("SDLPoP").asFile
val sdl2Root = externalRoot.dir("SDL2").asFile
val sdl2ImageRoot = externalRoot.dir("SDL2_image").asFile
val sdlpopPatch = rootProject.layout.projectDirectory.file("patches/sdlpop-android-touch-only.patch").asFile
val generatedAssets = layout.buildDirectory.dir("generated/sdlpopAssets")

fun runGit(workingDir: File, vararg args: String, ignoreExitValue: Boolean = false): Int {
    val output = providers.exec {
        this.workingDir = workingDir
        commandLine("git", *args)
        isIgnoreExitValue = ignoreExitValue
    }
    return output.result.get().exitValue
}

fun registerGitCheckoutTask(
    taskName: String,
    repoProperty: String,
    refProperty: String,
    checkoutDir: File
) = tasks.register(taskName) {
    group = "dependencies"
    description = "Fetches ${checkoutDir.name} from its upstream Git repository."

    val repo = providers.gradleProperty(repoProperty)
    val ref = providers.gradleProperty(refProperty)

    inputs.property(repoProperty, repo)
    inputs.property(refProperty, ref)

    doLast {
        val repoValue = repo.get()
        val refValue = ref.get()

        if (checkoutDir.exists() && !checkoutDir.resolve(".git").isDirectory) {
            throw GradleException("${checkoutDir.absolutePath} exists but is not a Git checkout. Remove it and rerun this task.")
        }

        checkoutDir.parentFile.mkdirs()
        if (!checkoutDir.resolve(".git").isDirectory) {
            providers.exec {
                commandLine("git", "clone", repoValue, checkoutDir.absolutePath)
            }.result.get().assertNormalExitValue()
        } else {
            runGit(checkoutDir, "remote", "set-url", "origin", repoValue)
        }

        val hasRef = runGit(checkoutDir, "rev-parse", "--verify", "$refValue^{commit}", ignoreExitValue = true) == 0
        if (!hasRef) {
            runGit(checkoutDir, "fetch", "--tags", "--prune", "origin")
        }

        runGit(checkoutDir, "checkout", "--force", refValue)
        runGit(checkoutDir, "reset", "--hard", refValue)
        runGit(checkoutDir, "clean", "-fdx")
    }
}

val fetchSdlpopSource = registerGitCheckoutTask("fetchSdlpopSource", "sdlpop.repo", "sdlpop.ref", sdlpopRoot)
val fetchSdl2Source = registerGitCheckoutTask("fetchSdl2Source", "sdl2.repo", "sdl2.ref", sdl2Root)
val fetchSdl2ImageSource = registerGitCheckoutTask("fetchSdl2ImageSource", "sdl2Image.repo", "sdl2Image.ref", sdl2ImageRoot)

val prepareSdlpopSource = tasks.register("prepareSdlpopSource") {
    group = "dependencies"
    description = "Resets upstream SDLPoP and applies the Android touch-only patch."
    dependsOn(fetchSdlpopSource)
    inputs.file(sdlpopPatch)

    doLast {
        val refValue = providers.gradleProperty("sdlpop.ref").get()
        runGit(sdlpopRoot, "checkout", "--force", refValue)
        runGit(sdlpopRoot, "reset", "--hard", refValue)
        runGit(sdlpopRoot, "clean", "-fdx")
        runGit(sdlpopRoot, "apply", sdlpopPatch.absolutePath)
    }
}

val prepareNativeDeps = tasks.register("prepareNativeDeps") {
    group = "dependencies"
    description = "Fetches and prepares all upstream native dependencies."
    dependsOn(prepareSdlpopSource, fetchSdl2Source, fetchSdl2ImageSource)
}

val syncSdlpopAssets = tasks.register<Sync>("syncSdlpopAssets") {
    dependsOn(prepareSdlpopSource)
    into(generatedAssets)

    from(sdlpopRoot.resolve("data")) {
        into("sdlpop/data")
    }
    from(sdlpopRoot.resolve("SDLPoP.ini")) {
        into("sdlpop")
    }
    from(sdlpopRoot.resolve("COPYING")) {
        into("sdlpop")
    }
    from(sdlpopRoot.resolve("README.md")) {
        into("sdlpop")
        rename { "README-SDLPoP.md" }
    }
    from(sdlpopRoot.resolve("src/gamecontrollerdb.txt")) {
        into("sdlpop")
    }
    from(sdlpopRoot.resolve("mods")) {
        into("sdlpop/mods")
    }
    from(sdlpopRoot.resolve("replays")) {
        into("sdlpop/replays")
    }

    doFirst {
        require(sdlpopRoot.resolve("data").isDirectory) {
            "Expected SDLPoP source at ${sdlpopRoot.absolutePath}. Run .\\gradlew.bat prepareNativeDeps."
        }
    }
}

android {
    namespace = "com.example.sdlpopoverlay"
    compileSdk = 34
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.example.sdlpopoverlay"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_APP_PLATFORM=android-24",
                    "-DANDROID_STL=c++_static"
                )
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            }
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(generatedAssets)
            java.srcDir("../external/SDL2/android-project/app/src/main/java")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.matching { task ->
    task.name.startsWith("merge") && task.name.endsWith("Assets")
}.configureEach {
    dependsOn(syncSdlpopAssets)
}

tasks.matching { task ->
    task.name == "preBuild" ||
            task.name.startsWith("configureCMake") ||
            task.name.startsWith("buildCMake") ||
            task.name.startsWith("externalNativeBuild")
}.configureEach {
    dependsOn(prepareNativeDeps)
}
