#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <unistd.h>

#include <cstdlib>
#include <string>
#include <vector>

namespace {

constexpr const char* kLogTag = "AveroJVM";

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
