#include <jni.h>
#include <cmath>

extern "C"
JNIEXPORT jint JNICALL
Java_com_mdh_gamelauncher_NativeGamepad_resolveDirection(
        JNIEnv *, jclass, jfloat x, jfloat y, jfloat hatX, jfloat hatY,
        jfloat flatX, jfloat flatY) {
    const float axisX = std::fabs(hatX) > 0.5f ? hatX : x;
    const float axisY = std::fabs(hatY) > 0.5f ? hatY : y;
    const float thresholdX = std::fabs(hatX) > 0.5f ? 0.5f : std::fmax(0.55f, flatX * 2.0f);
    const float thresholdY = std::fabs(hatY) > 0.5f ? 0.5f : std::fmax(0.55f, flatY * 2.0f);
    if (std::fabs(axisX) > std::fabs(axisY)) {
        if (axisX < -thresholdX) return 21;
        if (axisX > thresholdX) return 22;
    } else {
        if (axisY < -thresholdY) return 19;
        if (axisY > thresholdY) return 20;
    }
    return 0;
}
