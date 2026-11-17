package com.mdh.gamelauncher

/** Native helper for deterministic gamepad axis/HAT normalization. */
internal object NativeGamepad {
    init { System.loadLibrary("mdigamepad") }

    @JvmStatic
    external fun resolveDirection(
        x: Float, y: Float, hatX: Float, hatY: Float, flatX: Float, flatY: Float
    ): Int
}
