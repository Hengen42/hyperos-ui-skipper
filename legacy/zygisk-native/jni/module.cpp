// HyperOS UI Skipper - hookless Zygisk loader.
//
// No ART method hooking is used. This module only waits for the target
// process's Application object to exist, then loads an embedded DEX with
// InMemoryDexClassLoader and calls a single static entry point. All the
// actual behaviour (reflection-based field/method patches) lives in the
// Java payload (dex-src/dev/hengen/uiskipper/Injector.java).
#include <jni.h>
#include <pthread.h>
#include <unistd.h>
#include <android/log.h>
#include <atomic>
#include <cstring>
#include <vector>

#include "zygisk.hpp"
#include "payload_dex.h"

#define LOG_TAG "HyperOSUiSkipper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using zygisk::Api;
using zygisk::AppSpecializeArgs;
using zygisk::ServerSpecializeArgs;

namespace {

constexpr const char *kTargetPackage = "com.miui.securitycenter";
constexpr const char *kEntryClass = "dev.hengen.uiskipper.Injector";

// The buffer backing dalvik.system.DexFile's direct ByteBuffer is referenced
// by ART for the whole process lifetime (no copy is made), so it must live
// as long as the process does. A translation-unit static, not a stack/heap
// buffer freed when the worker thread returns, is required here.
std::vector<unsigned char> g_dex_keepalive;

std::atomic<bool> g_worker_started{false};

// ActivityThread.currentApplication() is null for a short moment after the
// process is specialized; poll without busy-waiting instead of hooking
// Application#attach or anything similar.
jobject GetApplication(JNIEnv *env) {
    jclass activity_thread = nullptr;
    jmethodID current_application = nullptr;

    for (int attempt = 0; attempt < 150; ++attempt) {
        if (activity_thread == nullptr) {
            activity_thread = env->FindClass("android/app/ActivityThread");
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                activity_thread = nullptr;
            }
        }
        if (activity_thread != nullptr && current_application == nullptr) {
            current_application = env->GetStaticMethodID(
                    activity_thread, "currentApplication", "()Landroid/app/Application;");
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                current_application = nullptr;
            }
        }
        if (activity_thread != nullptr && current_application != nullptr) {
            jobject app = env->CallStaticObjectMethod(activity_thread, current_application);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                app = nullptr;
            }
            if (app != nullptr) {
                return app;
            }
        }
        usleep(attempt < 20 ? 50000 : (attempt < 60 ? 100000 : 200000));
    }
    return nullptr;
}

void *InitWorker(void *arg) {
    JavaVM *vm = reinterpret_cast<JavaVM *>(arg);
    JNIEnv *env = nullptr;
    if (vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
        LOGE("failed to attach worker thread to the JVM");
        return nullptr;
    }

    LOGI("worker running inside %s (pid=%d)", kTargetPackage, getpid());

    jobject app = GetApplication(env);
    if (app == nullptr) {
        LOGE("timed out waiting for the Application instance");
        vm->DetachCurrentThread();
        return nullptr;
    }

    g_dex_keepalive.assign(payload_dex, payload_dex + payload_dex_len);

    jclass app_class = env->GetObjectClass(app);
    jmethodID get_class_loader = env->GetMethodID(app_class, "getClassLoader",
                                                    "()Ljava/lang/ClassLoader;");
    jobject app_loader = get_class_loader != nullptr
            ? env->CallObjectMethod(app, get_class_loader) : nullptr;
    if (app_loader == nullptr) {
        LOGE("failed to get the Application ClassLoader");
        vm->DetachCurrentThread();
        return nullptr;
    }

    jobject byte_buffer = env->NewDirectByteBuffer(g_dex_keepalive.data(),
                                                    static_cast<jlong>(g_dex_keepalive.size()));
    if (byte_buffer == nullptr) {
        LOGE("failed to create the DirectByteBuffer for the DEX");
        vm->DetachCurrentThread();
        return nullptr;
    }

    jclass loader_class = env->FindClass("dalvik/system/InMemoryDexClassLoader");
    if (loader_class == nullptr) {
        LOGE("dalvik.system.InMemoryDexClassLoader not found");
        env->ExceptionClear();
        vm->DetachCurrentThread();
        return nullptr;
    }

    jmethodID loader_ctor = env->GetMethodID(loader_class, "<init>",
                                              "(Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V");
    jobject dex_loader = env->NewObject(loader_class, loader_ctor, byte_buffer, app_loader);
    if (env->ExceptionCheck() || dex_loader == nullptr) {
        LOGE("failed to instantiate InMemoryDexClassLoader");
        env->ExceptionDescribe();
        env->ExceptionClear();
        vm->DetachCurrentThread();
        return nullptr;
    }

    jclass class_loader_class = env->FindClass("java/lang/ClassLoader");
    jmethodID load_class = env->GetMethodID(class_loader_class, "loadClass",
                                             "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring class_name = env->NewStringUTF(kEntryClass);
    jclass entry_class = reinterpret_cast<jclass>(
            env->CallObjectMethod(dex_loader, load_class, class_name));
    env->DeleteLocalRef(class_name);

    if (env->ExceptionCheck() || entry_class == nullptr) {
        LOGE("failed to load %s", kEntryClass);
        env->ExceptionDescribe();
        env->ExceptionClear();
        vm->DetachCurrentThread();
        return nullptr;
    }

    jmethodID init_method = env->GetStaticMethodID(entry_class, "init",
                                                    "(Landroid/content/Context;)V");
    if (init_method == nullptr) {
        LOGE("%s.init(Context) not found", kEntryClass);
        env->ExceptionClear();
        vm->DetachCurrentThread();
        return nullptr;
    }

    env->CallStaticVoidMethod(entry_class, init_method, app);
    if (env->ExceptionCheck()) {
        LOGE("exception while executing %s.init(Context)", kEntryClass);
        env->ExceptionDescribe();
        env->ExceptionClear();
    } else {
        LOGI("%s.init executed successfully", kEntryClass);
    }

    vm->DetachCurrentThread();
    return nullptr;
}

}  // namespace

class HyperOSUiSkipperModule : public zygisk::ModuleBase {
public:
    void onLoad(Api *api, JNIEnv *env) override {
        this->api = api;
        this->env = env;
        LOGI("onLoad: module library mapped (pid=%d)", getpid());
    }

    void preAppSpecialize(AppSpecializeArgs *args) override {
        // Nothing to inspect/change before specialization; the package name
        // check happens after, once args->nice_name is populated for sure.
        if (args != nullptr && args->nice_name != nullptr) {
            const char *nice_name = env->GetStringUTFChars(args->nice_name, nullptr);
            LOGI("preAppSpecialize: nice_name=%s uid=%d (pid=%d)",
                 nice_name ? nice_name : "(null)", args->uid, getpid());
            if (nice_name != nullptr) {
                env->ReleaseStringUTFChars(args->nice_name, nice_name);
            }
        } else {
            LOGI("preAppSpecialize: args/nice_name is null (pid=%d)", getpid());
        }
    }

    void postAppSpecialize(const AppSpecializeArgs *args) override {
        if (args == nullptr || args->nice_name == nullptr) {
            LOGI("postAppSpecialize: args/nice_name is null, unmapping (pid=%d)", getpid());
            api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
            return;
        }
        const char *nice_name = env->GetStringUTFChars(args->nice_name, nullptr);
        const bool is_target = nice_name != nullptr && strcmp(nice_name, kTargetPackage) == 0;
        LOGI("postAppSpecialize: nice_name=%s is_target=%d (pid=%d)",
             nice_name ? nice_name : "(null)", is_target ? 1 : 0, getpid());
        if (nice_name != nullptr) {
            env->ReleaseStringUTFChars(args->nice_name, nice_name);
        }
        if (!is_target) {
            // Unmap immediately everywhere else so the module costs nothing
            // (not even address space) in any other process.
            api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
            return;
        }

        LOGI("postAppSpecialize matched %s", kTargetPackage);

        if (g_worker_started.exchange(true)) {
            return; // never start a second worker in the same process
        }

        JavaVM *vm = nullptr;
        if (env->GetJavaVM(&vm) != JNI_OK || vm == nullptr) {
            LOGE("failed to get the JavaVM pointer");
            g_worker_started.store(false);
            return;
        }

        pthread_t tid;
        if (pthread_create(&tid, nullptr, InitWorker, reinterpret_cast<void *>(vm)) == 0) {
            pthread_detach(tid);
        } else {
            LOGE("failed to launch the initialization worker thread");
            g_worker_started.store(false);
        }
    }

    // system_server is forked like any other zygote child; without this
    // override the module library would stay mapped there for the whole
    // uptime of the device even though it has nothing to do there.
    void postServerSpecialize(const ServerSpecializeArgs *) override {
        api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
    }

private:
    Api *api = nullptr;
    JNIEnv *env = nullptr;
};

REGISTER_ZYGISK_MODULE(HyperOSUiSkipperModule)
