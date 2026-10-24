#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <dlfcn.h>
#include <unistd.h>

#include <cstdlib>
#include <mutex>
#include <string>
#include <vector>

namespace {

constexpr const char* kLogTag = "AveroJVM";
std::mutex gGameWindowMutex;
ANativeWindow* gGameWindow = nullptr;

using ProviderSetupWindow = void (*)(JNIEnv*, jclass, jobject);
using ProviderReleaseWindow = void (*)(JNIEnv*, jclass);
using ProviderJniOnLoad = jint (*)(JavaVM*, void*);

std::mutex gProviderMutex;
void* gProviderHandle = nullptr;
ProviderSetupWindow gProviderSetupWindow = nullptr;
ProviderReleaseWindow gProviderReleaseWindow = nullptr;
bool gProviderArtInitialized = false;
bool gProviderWindowAttached = false;

using JliLaunch = jint (*)(
    int argc,
    char** argv,
    int jargc,
    const char** jargv,
    int appclassc,
    const char** appclassv,
    const char* fullversion,
    const char* dotversion,
    const char* pname,
    const char* lname,
    jboolean javaargs,
    jboolean cpwildcard,
    jboolean javaw,
    jint ergo
);

void throwIllegalState(JNIEnv* env, const std::string& message) {
    jclass clazz = env->FindClass("java/lang/IllegalStateException");
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message.c_str());
    }
}

std::string toString(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::vector<std::string> toStringVector(JNIEnv* env, jobjectArray array) {
    std::vector<std::string> result;
    if (array == nullptr) return result;

    const jsize size = env->GetArrayLength(array);
    result.reserve(static_cast<size_t>(size));

    for (jsize i = 0; i < size; ++i) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(array, i));
        result.emplace_back(toString(env, value));
        env->DeleteLocalRef(value);
    }
    return result;
}

bool applyEnvironment(
    JNIEnv* env,
    jobjectArray keysArray,
    jobjectArray valuesArray
) {
    if (keysArray == nullptr && valuesArray == nullptr) return true;
    if (keysArray == nullptr || valuesArray == nullptr) {
        throwIllegalState(env, "Environment key/value arrays must both be present");
        return false;
    }

    const jsize keyCount = env->GetArrayLength(keysArray);
    const jsize valueCount = env->GetArrayLength(valuesArray);
    if (keyCount != valueCount) {
        throwIllegalState(env, "Environment key/value array sizes do not match");
        return false;
    }

    for (jsize i = 0; i < keyCount; ++i) {
        auto keyValue = static_cast<jstring>(env->GetObjectArrayElement(keysArray, i));
        auto envValue = static_cast<jstring>(env->GetObjectArrayElement(valuesArray, i));

        const std::string key = toString(env, keyValue);
        const std::string value = toString(env, envValue);

        env->DeleteLocalRef(keyValue);
        env->DeleteLocalRef(envValue);

        if (key.empty() || key.find('=') != std::string::npos) {
            throwIllegalState(env, "Invalid environment variable name");
            return false;
        }

        if (setenv(key.c_str(), value.c_str(), 1) != 0) {
            throwIllegalState(env, "Failed to set environment variable: " + key);
            return false;
        }
    }

    return true;
}

} // namespace

extern "C"
JNIEXPORT jint JNICALL
Java_io_yannickfan_avero_runtime_NativeJvmBridge_nativeLaunch(
    JNIEnv* env,
    jobject /* thiz */,
    jstring jliPathValue,
    jobjectArray argsArray,
    jobjectArray envKeys,
    jobjectArray envValues,
    jstring workingDirectoryValue
) {
    if (!applyEnvironment(env, envKeys, envValues)) return -10;

    const std::string workingDirectory = toString(env, workingDirectoryValue);
    if (!workingDirectory.empty() && chdir(workingDirectory.c_str()) != 0) {
        throwIllegalState(env, "Failed to change game working directory");
        return -11;
    }

    const std::string jliPath = toString(env, jliPathValue);
    if (jliPath.empty()) {
        throwIllegalState(env, "libjli.so path is empty");
        return -12;
    }

    dlerror();
    void* jliHandle = dlopen(jliPath.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (jliHandle == nullptr) {
        const char* error = dlerror();
        const std::string message =
            std::string("Failed to load libjli.so: ") + (error == nullptr ? "unknown error" : error);
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "%s", message.c_str());
        throwIllegalState(env, message);
        return -13;
    }

    dlerror();
    auto launch = reinterpret_cast<JliLaunch>(dlsym(jliHandle, "JLI_Launch"));
    const char* symbolError = dlerror();
    if (launch == nullptr || symbolError != nullptr) {
        const std::string message =
            std::string("JLI_Launch not found: ") +
            (symbolError == nullptr ? "unknown error" : symbolError);
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "%s", message.c_str());
        throwIllegalState(env, message);
        return -14;
    }

    std::vector<std::string> args = toStringVector(env, argsArray);
    if (args.empty()) {
        throwIllegalState(env, "JVM argument list is empty");
        return -15;
    }

    std::vector<char*> argv;
    argv.reserve(args.size());
    for (std::string& arg : args) {
        argv.push_back(arg.data());
    }

    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "Launching JVM through JLI_Launch with %d arguments",
        static_cast<int>(argv.size())
    );

    return launch(
        static_cast<int>(argv.size()),
        argv.data(),
        0,
        nullptr,
        0,
        nullptr,
        "Avero",
        "0.1",
        argv.front(),
        argv.front(),
        JNI_FALSE,
        JNI_TRUE,
        JNI_FALSE,
        0
    );
}


extern "C"
JNIEXPORT void JNICALL
Java_io_yannickfan_avero_game_GameSurfaceBridge_nativeAttachSurface(
    JNIEnv* env,
    jobject /* thiz */,
    jobject surface
) {
    if (surface == nullptr) {
        throwIllegalState(env, "Game surface is null");
        return;
    }

    ANativeWindow* next = ANativeWindow_fromSurface(env, surface);
    if (next == nullptr) {
        throwIllegalState(env, "Could not create ANativeWindow from game surface");
        return;
    }

    std::lock_guard<std::mutex> lock(gGameWindowMutex);
    if (gGameWindow != nullptr) {
        ANativeWindow_release(gGameWindow);
    }
    gGameWindow = next;

    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "Game surface attached: %dx%d",
        ANativeWindow_getWidth(gGameWindow),
        ANativeWindow_getHeight(gGameWindow)
    );
}

extern "C"
JNIEXPORT void JNICALL
Java_io_yannickfan_avero_game_GameSurfaceBridge_nativeDetachSurface(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    std::lock_guard<std::mutex> lock(gGameWindowMutex);
    if (gGameWindow != nullptr) {
        ANativeWindow_release(gGameWindow);
        gGameWindow = nullptr;
    }
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "Game surface detached");
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_io_yannickfan_avero_game_GameSurfaceBridge_nativeSurfaceSize(
    JNIEnv* env,
    jobject /* thiz */
) {
    jint values[2] = {0, 0};
    {
        std::lock_guard<std::mutex> lock(gGameWindowMutex);
        if (gGameWindow != nullptr) {
            values[0] = ANativeWindow_getWidth(gGameWindow);
            values[1] = ANativeWindow_getHeight(gGameWindow);
        }
    }

    jintArray result = env->NewIntArray(2);
    if (result != nullptr) {
        env->SetIntArrayRegion(result, 0, 2, values);
    }
    return result;
}

// Future renderer backends can acquire this window without depending on Compose.
extern "C"
ANativeWindow* avero_acquire_game_window() {
    std::lock_guard<std::mutex> lock(gGameWindowMutex);
    if (gGameWindow != nullptr) {
        ANativeWindow_acquire(gGameWindow);
    }
    return gGameWindow;
}


extern "C"
JNIEXPORT void JNICALL
Java_io_yannickfan_avero_game_AndroidLwjglBridge_nativePrepare(
    JNIEnv* env,
    jobject /* thiz */,
    jstring providerPathValue,
    jstring nativeDirectoryValue,
    jobjectArray preloadLibraries,
    jobject surface
) {
    const std::string providerPath = toString(env, providerPathValue);
    const std::string nativeDirectory = toString(env, nativeDirectoryValue);

    if (providerPath.empty() || nativeDirectory.empty()) {
        throwIllegalState(env, "Android LWJGL provider path is incomplete");
        return;
    }
    if (surface == nullptr) {
        throwIllegalState(env, "Android LWJGL provider requires a Surface");
        return;
    }

    setenv("POJAV_NATIVEDIR", nativeDirectory.c_str(), 1);
    setenv("AMETHYST_RENDERER", "opengles_system_gles", 1);
    setenv("LIBGL_ES", "3", 1);

    ANativeWindow* providerWindow = ANativeWindow_fromSurface(env, surface);
    if (providerWindow == nullptr) {
        throwIllegalState(env, "Could not inspect Android LWJGL Surface");
        return;
    }
    const std::string surfaceWidth =
        std::to_string(ANativeWindow_getWidth(providerWindow));
    const std::string surfaceHeight =
        std::to_string(ANativeWindow_getHeight(providerWindow));
    setenv("AWTSTUB_WIDTH", surfaceWidth.c_str(), 1);
    setenv("AWTSTUB_HEIGHT", surfaceHeight.c_str(), 1);
    ANativeWindow_release(providerWindow);

    const std::vector<std::string> preload =
        toStringVector(env, preloadLibraries);
    for (const std::string& path : preload) {
        if (path == providerPath) continue;
        dlerror();
        void* handle = dlopen(path.c_str(), RTLD_LAZY | RTLD_GLOBAL);
        if (handle == nullptr) {
            const char* error = dlerror();
            __android_log_print(
                ANDROID_LOG_WARN,
                kLogTag,
                "Optional provider preload failed for %s: %s",
                path.c_str(),
                error == nullptr ? "unknown error" : error
            );
        }
    }

    std::lock_guard<std::mutex> lock(gProviderMutex);

    if (gProviderHandle == nullptr) {
        dlerror();
        gProviderHandle = dlopen(
            providerPath.c_str(),
            RTLD_NOW | RTLD_GLOBAL
        );
        if (gProviderHandle == nullptr) {
            const char* error = dlerror();
            const std::string message =
                std::string("Failed to load Android LWJGL bridge: ") +
                (error == nullptr ? "unknown error" : error);
            throwIllegalState(env, message);
            return;
        }
    }

    if (!gProviderArtInitialized) {
        auto onLoad = reinterpret_cast<ProviderJniOnLoad>(
            dlsym(gProviderHandle, "JNI_OnLoad")
        );
        if (onLoad == nullptr) {
            throwIllegalState(
                env,
                "Android LWJGL bridge does not export JNI_OnLoad"
            );
            return;
        }

        JavaVM* artVm = nullptr;
        if (env->GetJavaVM(&artVm) != JNI_OK || artVm == nullptr) {
            throwIllegalState(env, "Could not obtain Android JavaVM");
            return;
        }

        const jint version = onLoad(artVm, nullptr);
        if (env->ExceptionCheck()) {
            return;
        }
        if (version < JNI_VERSION_1_4) {
            throwIllegalState(
                env,
                "Android LWJGL bridge rejected the Android JavaVM"
            );
            return;
        }
        gProviderArtInitialized = true;
    }

    if (gProviderSetupWindow == nullptr) {
        gProviderSetupWindow = reinterpret_cast<ProviderSetupWindow>(
            dlsym(
                gProviderHandle,
                "Java_net_kdt_pojavlaunch_utils_JREUtils_setupBridgeWindow"
            )
        );
    }
    if (gProviderReleaseWindow == nullptr) {
        gProviderReleaseWindow = reinterpret_cast<ProviderReleaseWindow>(
            dlsym(
                gProviderHandle,
                "Java_net_kdt_pojavlaunch_utils_JREUtils_releaseBridgeWindow"
            )
        );
    }

    if (gProviderSetupWindow == nullptr) {
        throwIllegalState(
            env,
            "Android LWJGL bridge has no setupBridgeWindow entry"
        );
        return;
    }

    if (gProviderWindowAttached && gProviderReleaseWindow != nullptr) {
        gProviderReleaseWindow(env, nullptr);
        gProviderWindowAttached = false;
        if (env->ExceptionCheck()) return;
    }

    gProviderSetupWindow(env, nullptr, surface);
    if (!env->ExceptionCheck()) {
        gProviderWindowAttached = true;
        __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "Android LWJGL bridge attached to game Surface"
        );
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_io_yannickfan_avero_game_AndroidLwjglBridge_nativeRelease(
    JNIEnv* env,
    jobject /* thiz */
) {
    std::lock_guard<std::mutex> lock(gProviderMutex);
    if (
        gProviderWindowAttached &&
        gProviderHandle != nullptr &&
        gProviderReleaseWindow != nullptr
    ) {
        gProviderReleaseWindow(env, nullptr);
        gProviderWindowAttached = false;
    }
}
