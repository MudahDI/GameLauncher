package com.contoh.gamelauncher

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Aksi abstrak dari gamepad (D-pad atau stik kiri, A, B, X, Y, L1, R1). */
enum class Pad { Left, Right, Up, Down, Confirm, Back, Favorite, Search, PrevTab, NextTab }

/**
 * Jembatan antara input gamepad di Activity dan layar Compose.
 * Activity memanggil [send]; layar yang sedang tampil mengumpulkan [events].
 */
class GamepadInput {
    /** True sejak input gamepad terakhir; kembali false begitu layar disentuh. */
    var active by mutableStateOf(false)
        private set

    private val _events = MutableSharedFlow<Pad>(extraBufferCapacity = 32)
    val events: SharedFlow<Pad> = _events

    private var lastKey: Pad? = null
    private var lastAt = 0L

    fun send(key: Pad) {
        val now = SystemClock.uptimeMillis()
        // Sebagian gamepad mengirim D-pad sebagai tombol sekaligus sumbu; abaikan duplikat yang nyaris bersamaan.
        if (key == lastKey && now - lastAt < 40) return
        lastKey = key
        lastAt = now
        active = true
        _events.tryEmit(key)
    }

    fun touched() {
        active = false
    }
}
