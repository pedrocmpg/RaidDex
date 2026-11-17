package com.raiddex.data

import android.content.Context

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Nível de treinador; limita o nível máximo de Pokémon cadastrável (nível + 2, até 51). */
    var trainerLevel: Int
        get() = prefs.getInt("trainerLevel", 50)
        set(v) = prefs.edit().putInt("trainerLevel", v.coerceIn(1, 80)).apply()

    val maxPokemonLevel: Double get() = minOf(trainerLevel + 2, 51).toDouble()
}
