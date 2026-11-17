package com.mdh.gamelauncher

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Aksi internal; input fisik yang diaktifkan saat ini hanya D-pad dan tombol A/B. */
enum class Pad { Left, Right, Up, Down, Confirm, Back, Favorite, Search, PrevTab, NextTab }

/** Meneruskan tombol dari Activity ke root Compose. */
class GamepadInput {
    // Compatibilidade para telas antigas; o modo simples não ativa navegação por tela.
    val active: Boolean get() = false
    private val _events = MutableSharedFlow<Pad>(extraBufferCapacity = 16)
    val events: SharedFlow<Pad> = _events

    fun send(key: Pad) {
        _events.tryEmit(key)
    }
}
