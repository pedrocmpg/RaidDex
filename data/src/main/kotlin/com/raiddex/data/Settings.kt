package com.raiddex.data

import android.content.Context

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Nível de treinador do jogador (informativo). */
    var trainerLevel: Int
        get() = prefs.getInt("trainerLevel", 33)
        set(v) = prefs.edit().putInt("trainerLevel", v.coerceIn(1, 80)).apply()

    /**
     * Nível que fica na ponta direita do arco da tela de detalhe. Calibrado com prints reais (39) e
     * independente do nível de treinador: com treinador 33 o ponto do nível 28 só cai certo com 39.
     */
    var arcMaxLevel: Int
        get() = prefs.getInt("arcMaxLevel", 39)
        set(v) = prefs.edit().putInt("arcMaxLevel", v.coerceIn(30, 51)).apply()

    /** Pokémon recebidos por troca ou raid passam do nível de treinador + 2; vale o teto do jogo. */
    val maxPokemonLevel: Double get() = 51.0
}
