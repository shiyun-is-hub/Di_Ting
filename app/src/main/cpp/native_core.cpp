#include <jni.h>
#include <sched.h>
#include <sys/resource.h>
#include <sys/mman.h> // [新增] 用于 mlockall
#include <android/log.h>
#include <errno.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "ZR.Native", __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "ZR.Native", __VA_ARGS__)

extern "C" JNIEXPORT jboolean JNICALL
Java_com_zero_recorder_system_NativeCore_enableRealTimeScheduling(JNIEnv *env, jclass clazz) {
    struct sched_param param;
    param.sched_priority = sched_get_priority_max(SCHED_RR); 
    if (sched_setscheduler(0, SCHED_RR, &param) == -1) {
        LOGE("RealTime scheduling failed: %s", strerror(errno));
        return JNI_FALSE;
    }
    LOGI("RealTime SCHED_RR scheduling activated");
    return JNI_TRUE;
}

// [优化] 真正的物理内存锁定，拒绝 Linux Kernel 发生 Swap / ZRAM 交换，消除 GC 卡顿
extern "C" JNIEXPORT jboolean JNICALL
Java_com_zero_recorder_system_NativeCore_lockMemoryIntoRAM(JNIEnv *env, jclass clazz) {
    // MCL_CURRENT: 锁定当前所有已分配的内存页
    // MCL_FUTURE: 锁定未来分配的所有内存页
    if (mlockall(MCL_CURRENT | MCL_FUTURE) == 0) {
        LOGI("Memory strictly locked into physical RAM (mlockall success). Page faults prevented.");
        return JNI_TRUE;
    }
    LOGE("mlockall failed (requires root/shell caps): %s", strerror(errno));
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_zero_recorder_system_NativeCore_bindToPerformanceCores(JNIEnv *env, jclass clazz) {
    long num_cores = sysconf(_SC_NPROCESSORS_CONF);
    if (num_cores <= 4) return JNI_FALSE; 
    
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    for (int i = num_cores / 2; i < num_cores; i++) {
        CPU_SET(i, &cpuset);
    }
    
    if (sched_setaffinity(0, sizeof(cpu_set_t), &cpuset) != 0) {
        return JNI_FALSE;
    }
    LOGI("Thread pinned to P-Cores (CPU %ld to %ld)", num_cores / 2, num_cores - 1);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_zero_recorder_system_NativeCore_protectFromOOM(JNIEnv *env, jclass clazz) {
    // 现代内核使用 oom_score_adj
    int fd = open("/proc/self/oom_score_adj", O_WRONLY);
    if (fd >= 0) {
        write(fd, "-1000", 5);
        close(fd);
        LOGI("OOM Killer Immunity Activated (oom_score_adj = -1000)");
        return;
    }
    // 回退到旧版内核的 oom_adj
    fd = open("/proc/self/oom_adj", O_WRONLY);
    if (fd >= 0) {
        write(fd, "-17", 3);
        close(fd);
        LOGI("OOM Killer Immunity Activated (legacy oom_adj = -17)");
    } else {
        LOGE("Failed to acquire OOM Immunity");
    }
}
